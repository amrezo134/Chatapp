package com.creatix.chatapp.services

import android.content.Context
import android.util.Base64
import com.creatix.chatapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

/**
 * بيبعت إشعار Push مباشرة من جهاز المرسل لجهاز المستقبل عن طريق FCM HTTP v1 API،
 * من غير أي سيرفر أو Cloud Function (يعني من غير خطة Blaze ومن غير بطاقة بنكية).
 *
 * ملحوظة أمنية مهمة:
 * بيستخدم ملف "Service Account" (res/raw/service_account.json) عشان يوثّق نفسه لجوجل.
 * الملف ده لازم يكون بصلاحية محدودة (Firebase Cloud Messaging API فقط)، ولازم منعرفوش
 * يبقى في الكود المصدري العلني على GitHub — بيتحقن وقت البناء عن طريق GitHub Actions Secret
 * (شوف .github/workflows/build.yml).
 */
object FcmPushSender {

    private const val SCOPE = "https://www.googleapis.com/auth/firebase.messaging"

    // بنكاش الـ access token لحد ما يقرب ينتهي، عشان منطلبش توكن جديد مع كل رسالة
    @Volatile private var cachedToken: String? = null
    @Volatile private var tokenExpiryMillis: Long = 0L

    private fun readServiceAccount(context: Context): JSONObject {
        val text = context.resources.openRawResource(R.raw.service_account)
            .bufferedReader().use { it.readText() }
        return JSONObject(text)
    }

    private fun base64UrlEncode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun parsePrivateKey(pem: String): PrivateKey {
        val cleaned = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "")
            .replace("\n", "")
            .trim()
        val keyBytes = Base64.decode(cleaned, Base64.DEFAULT)
        val spec = PKCS8EncodedKeySpec(keyBytes)
        return KeyFactory.getInstance("RSA").generatePrivate(spec)
    }

    /** بيبني JWT موقّع بالمفتاح الخاص، ويبدله بـ access token صالح لمدة ساعة */
    private suspend fun getAccessToken(context: Context): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cachedToken?.let { if (now < tokenExpiryMillis - 60_000) return@withContext it }

        val sa = readServiceAccount(context)
        val clientEmail = sa.getString("client_email")
        val privateKeyPem = sa.getString("private_key")
        val tokenUri = sa.optString("token_uri", "https://oauth2.googleapis.com/token")

        val iat = now / 1000
        val exp = iat + 3600

        val header = JSONObject().put("alg", "RS256").put("typ", "JWT")
        val claims = JSONObject().apply {
            put("iss", clientEmail)
            put("scope", SCOPE)
            put("aud", tokenUri)
            put("iat", iat)
            put("exp", exp)
        }

        val unsigned = "${base64UrlEncode(header.toString().toByteArray(Charsets.UTF_8))}." +
            base64UrlEncode(claims.toString().toByteArray(Charsets.UTF_8))

        val signatureBytes = Signature.getInstance("SHA256withRSA").apply {
            initSign(parsePrivateKey(privateKeyPem))
            update(unsigned.toByteArray(Charsets.UTF_8))
        }.sign()

        val jwt = "$unsigned.${base64UrlEncode(signatureBytes)}"

        val conn = (URL(tokenUri).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        val body = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=$jwt"
        OutputStreamWriter(conn.outputStream).use { it.write(body) }

        val responseCode = conn.responseCode
        val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
        val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()

        if (responseCode !in 200..299) {
            throw IllegalStateException("فشل الحصول على access token ($responseCode): $responseText")
        }

        val json = JSONObject(responseText)
        val accessToken = json.getString("access_token")
        val expiresIn = json.optLong("expires_in", 3600)

        cachedToken = accessToken
        tokenExpiryMillis = now + expiresIn * 1000
        accessToken
    }

    /** بيبعت إشعار push لتوكن معين (fcmToken بتاع المستقبل) */
    suspend fun sendNotification(
        context: Context,
        projectId: String,
        targetToken: String,
        title: String,
        body: String,
        senderId: String
    ): Unit = withContext(Dispatchers.IO) {
        if (targetToken.isBlank()) return@withContext

        val accessToken = getAccessToken(context)
        val url = URL("https://fcm.googleapis.com/v1/projects/$projectId/messages:send")

        val payload = JSONObject().put("message", JSONObject().apply {
            put("token", targetToken)
            put("notification", JSONObject().apply {
                put("title", title)
                put("body", body)
            })
            put("data", JSONObject().put("senderId", senderId))
            put("android", JSONObject().apply {
                put("priority", "high")
                put("notification", JSONObject().put("channel_id", "chat_messages_channel"))
            })
        })

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Authorization", "Bearer $accessToken")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
            conn.disconnect()
            throw IllegalStateException("فشل إرسال الإشعار ($responseCode): $err")
        }
        conn.disconnect()
    }
}
