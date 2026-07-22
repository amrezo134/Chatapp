package com.creatix.chatapp.repository

import com.creatix.chatapp.config.AppConfig
import com.creatix.chatapp.data.AiChatMessage
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * الريبو ده مبيكلمش Gemini مباشرة ولا بيشيل مفتاح الـ API خالص.
 * هو بيكلم الـ Cloudflare Worker بتاعنا على /gemini/chat، والـ Worker هو اللي
 * عنده المفتاح السري ومعمول عنده الاتصال الفعلي بـ Gemini.
 * ده بيحمي المفتاح من إنه يتسرب لو حد فك التطبيق (APK) أو شاف الكود على GitHub.
 */
object AiChatRepository {

    private const val SYSTEM_INSTRUCTION =
        "إنت مساعد ذكي جوه تطبيق شات اسمه ChatApp. جاوب باختصار ووضوح، وبالعربي لو المستخدم كتب بالعربي."

    suspend fun sendMessage(history: List<AiChatMessage>): Result<String> = withContext(Dispatchers.IO) {
        try {
            val idToken = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
                ?: return@withContext Result.failure(IllegalStateException("لازم تسجل الدخول الأول"))

            val contents = JSONArray()
            history.forEach { msg ->
                contents.put(
                    JSONObject().apply {
                        put("role", if (msg.isFromUser) "user" else "model")
                        put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
                    }
                )
            }

            val payload = JSONObject().apply {
                put("contents", contents)
                put("model", "gemini-2.0-flash")
                put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_INSTRUCTION)))
                )
            }

            val url = URL("${AppConfig.WORKER_BASE_URL}/gemini/chat")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Authorization", "Bearer $idToken")
                connectTimeout = 20_000
                readTimeout = 30_000
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()

            if (responseCode !in 200..299) {
                return@withContext Result.failure(IllegalStateException("فشل الاتصال بالمساعد ($responseCode): $responseText"))
            }

            val reply = JSONObject(responseText).optString("reply").ifBlank {
                "معرفتش أجاوب دلوقتي، جرّب تاني."
            }
            Result.success(reply)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
