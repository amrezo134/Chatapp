package com.creatix.chatapp.data.backup

import android.content.Context
import android.content.SharedPreferences
import com.creatix.chatapp.data.local.ChatDatabase
import com.creatix.chatapp.data.local.MessageEntity
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * نسخ احتياطي للرسايل على Google Drive — في المجلد المخفي appDataFolder
 * (مساحة خاصة بالتطبيق، المستخدم مش بيشوفها في ملفاته ولا حد تاني يقدر يوصلها).
 * ملف JSON واحد لكل مستخدم بيتكتب من كاش Room المحلي، وبنسترجعه لو التليفون
 * اتبدل أو التطبيق اتشال واتسطب من جديد.
 * ملاحظة: الميديا مش بتتنسخ — الرسايل فيها روابط الملفات على Cloudflare وهي بتفضل هناك.
 */
object DriveBackupManager {

    const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private const val BACKUP_FILE_NAME = "chatapp-messages-backup.json.gz"
    private const val BACKUP_VERSION = 1
    private const val PREFS = "drive_backup_prefs"
    private const val KEY_PROMPT_SHOWN = "backup_prompt_shown"
    private const val KEY_LAST_BACKUP_AT = "last_backup_at"
    private const val AUTO_BACKUP_INTERVAL_MS = 24L * 60 * 60 * 1000

    private val http = OkHttpClient()

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------- تسجيل الدخول بحساب جوجل (صلاحية المجلد المخفي بس) ----------------

    fun signInOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_APPDATA_SCOPE))
            .build()

    /** حساب جوجل المسجل وبصلاحية Drive، أو null لو لسه محتاج تسجيل/موافقة */
    fun signedInAccount(context: Context): GoogleSignInAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        return if (GoogleSignIn.hasPermissions(account, Scope(DRIVE_APPDATA_SCOPE))) account else null
    }

    // ---------------- حالة الباك اب على الجهاز ----------------

    fun wasPromptShown(context: Context): Boolean = prefs(context).getBoolean(KEY_PROMPT_SHOWN, false)

    fun markPromptShown(context: Context) {
        prefs(context).edit().putBoolean(KEY_PROMPT_SHOWN, true).apply()
    }

    fun lastBackupAt(context: Context): Long = prefs(context).getLong(KEY_LAST_BACKUP_AT, 0L)

    // ---------------- النسخ والاسترجاع ----------------

    private suspend fun localCacheEmpty(): Boolean = (ChatDatabase.dao()?.count() ?: 0) == 0

    /** توكن وصول لـ Drive API، أو null لو مفيش حساب جوجل مسجل بالصلاحية */
    private fun accessToken(context: Context): String? {
        val account = signedInAccount(context)?.account ?: return null
        return try {
            GoogleAuthUtil.getToken(context, account, "oauth2:$DRIVE_APPDATA_SCOPE")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * نسخ كل الرسايل المتخزنة محليًا على Drive (ملف واحد بيتبدل كل مرة).
     * بيرجع عدد الرسايل المتنسخة، ويرمي Exception لو فشل (مفيش حساب/مفيش نت/رفض Drive).
     */
    suspend fun backupNow(context: Context, uid: String): Int = withContext(Dispatchers.IO) {
        val dao = ChatDatabase.dao() ?: throw Exception("الكاش المحلي مش جاهز")
        val token = accessToken(context) ?: throw Exception("سجّل دخولك بحساب جوجل الأول")

        val messages = dao.getAll()
        val payload = JSONObject()
            .put("version", BACKUP_VERSION)
            .put("uid", uid)
            .put("createdAt", System.currentTimeMillis())
            .put("messages", JSONArray().apply { messages.forEach { put(MessageEntity.toJson(it)) } })
        val raw = payload.toString().toByteArray(Charsets.UTF_8)
        val bytes = gzip(raw) // ضغط gzip — ملف النسخة بيبقى أصغر بحوالي 80-90%

        val existingId = findBackupFileId(token)
        if (existingId == null) createBackupFile(token, bytes) else updateBackupFile(token, existingId, bytes)

        val now = System.currentTimeMillis()
        prefs(context).edit().putLong(KEY_LAST_BACKUP_AT, now).apply()
        try {
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .set(mapOf("lastBackupAt" to now), SetOptions.merge()).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        messages.size
    }

    /** بيرجع عدد الرسايل المسترجعة، أو null لو مفيش حساب/نسخة على Drive */
    suspend fun restoreNow(context: Context): Int? = withContext(Dispatchers.IO) {
        val dao = ChatDatabase.dao() ?: return@withContext null
        val token = accessToken(context) ?: return@withContext null
        val fileId = findBackupFileId(token) ?: return@withContext null
        val body = downloadBackupFile(token, fileId) ?: return@withContext null
        val array = JSONObject(String(gunzip(body), Charsets.UTF_8)).optJSONArray("messages") ?: return@withContext null
        val entities = (0 until array.length()).map { MessageEntity.fromJson(array.getJSONObject(it)) }
        entities.chunked(500).forEach { dao.upsertAll(it) }
        entities.size
    }

    /** استرجاع تلقائي: بيحصل بس لو الكاش المحلي فاضي (تليفون جديد أو تثبيت جديد) */
    suspend fun restoreIfEmpty(context: Context): Int? =
        if (localCacheEmpty()) restoreNow(context) else null

    /** نسخ تلقائي مرة كل 24 ساعة. بيرجع true لو حصل نسخ فعلًا */
    suspend fun autoBackupIfDue(context: Context, uid: String, backupEnabled: Boolean): Boolean {
        if (!backupEnabled) return false
        if (signedInAccount(context) == null) return false
        if (System.currentTimeMillis() - lastBackupAt(context) < AUTO_BACKUP_INTERVAL_MS) return false
        return try {
            backupNow(context, uid)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ---------------- Drive REST API (OkHttp مباشرة، من غير مكتبة جوجل الكبيرة) ----------------

    private fun findBackupFileId(token: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder" +
            "&q=name%20%3D%20%27$BACKUP_FILE_NAME%27&fields=files(id)"
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("فشل البحث في Drive: ${response.code}")
            val files = JSONObject(response.body!!.string()).optJSONArray("files") ?: return null
            return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
        }
    }

    private fun createBackupFile(token: String, bytes: ByteArray) {
        val metadata = JSONObject()
            .put("name", BACKUP_FILE_NAME)
            .put("parents", JSONArray().put("appDataFolder"))
            .toString()
        val gzipType = "application/gzip".toMediaType()
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("metadata", null, metadata.toRequestBody("application/json".toMediaType()))
            .addFormDataPart("file", BACKUP_FILE_NAME, bytes.toRequestBody(gzipType))
            .build()
        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("فشل إنشاء ملف النسخة: ${response.code}")
        }
    }

    private fun updateBackupFile(token: String, fileId: String, bytes: ByteArray) {
        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
            .header("Authorization", "Bearer $token")
            .patch(bytes.toRequestBody("application/gzip".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("فشل تحديث ملف النسخة: ${response.code}")
        }
    }

    private fun downloadBackupFile(token: String, fileId: String): ByteArray? {
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            .header("Authorization", "Bearer $token")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body!!.bytes()
        }
    }

    // ضغط/فك ضغط gzip — بيصغّر ملف النسخة JSON جدًا (نصوص بتتضغط كويس)
    private fun gzip(data: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    private fun gunzip(data: ByteArray): ByteArray =
        java.util.zip.GZIPInputStream(data.inputStream()).use { it.readBytes() }
}
