package com.creatix.chatapp.repository

import android.content.Context
import android.util.Log
import com.creatix.chatapp.data.AiChatMessage
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * مساعد ذكي: Gemini هو الأساسي (Firebase AI Logic - Gemini Developer API، مجاني،
 * مفيش API key جوه التطبيق)، وبوت محلي بالكلمات المفتاحية كاحتياطي لو Gemini
 * مش متاح لأي سبب (مفيش نت، الخدمة مش متفعلة في الكونسول... إلخ).
 *
 * بيانات البوت الاحتياطي (كلمات مفتاحية -> رد) متخزنة في مشروع Firebase منفصل
 * تمامًا عن باقي التطبيق — اسمه train-38762 — عشان يبقى مخصص لبيانات البوت بس.
 * التطبيق بيسحبها مرة، بيكاشها 5 دقايق، وبيطابقها محليًا مع رسالة المستخدم.
 *
 * لازم تنادي AiChatRepository.init(context) مرة واحدة الأول (مثلاً في
 * onCreate بتاع MainActivity) قبل ما تستخدم شاشة البوت، عشان يوصل قاعدة
 * train-38762. لو نسيت تناديها، الاحتياطي هيرجع رسالة "مش جاهز لسه" بدل ما يكرش.
 */
object AiChatRepository {

    private data class KnowledgeEntry(val keywords: List<String>, val reply: String)

    // إعدادات مشروع train-38762 (نفس القيم اللي في admin-training.html، بس نسخة أندرويد)
    private const val TRAIN_APP_NAME = "trainKnowledge"
    private val trainOptions = FirebaseOptions.Builder()
        .setApiKey("AIzaSyD121a9FE6wOJQL135I7Sgg5gVLuAmlx10")
        .setApplicationId("1:284700909620:web:61e0e92394a05341bdaa61")
        .setProjectId("train-38762")
        .setStorageBucket("train-38762.firebasestorage.app")
        .setGcmSenderId("284700909620")
        .build()

    private var trainDb: FirebaseFirestore? = null

    // قاعدة احتياطية اختيارية لو train-38762 قربت تمتلئ يومًا ما (اختياري، مش مفعّلة لسه)
    private var overflowDb: FirebaseFirestore? = null

    /**
     * ينادَى مرة واحدة بس (مثلاً MainActivity.onCreate) قبل استخدام شاشة البوت.
     * آمن تنادها أكتر من مرة، مش هتعمل مشكلة.
     */
    fun init(context: Context) {
        if (trainDb != null) return
        try {
            val existing = FirebaseApp.getApps(context).firstOrNull { it.name == TRAIN_APP_NAME }
            val app = existing ?: FirebaseApp.initializeApp(context, trainOptions, TRAIN_APP_NAME)
            trainDb = FirebaseFirestore.getInstance(app)
        } catch (e: Exception) {
            trainDb = null
        }
    }

    /**
     * لو عايز تفعّل قاعدة احتياطية تانية غير train-38762 (لو دي قربت تمتلئ يومًا ما).
     * بتاخد نفس فكرة init() بس لمشروع تالت منفصل.
     */
    fun enableOverflowDatabase(options: FirebaseOptions, context: Context) {
        try {
            val overflowApp = FirebaseApp.initializeApp(context, options, "overflow")
            overflowDb = FirebaseFirestore.getInstance(overflowApp)
        } catch (e: Exception) {
            overflowDb = null
        }
    }

    private var cache: List<KnowledgeEntry>? = null
    private var lastFetchAt: Long = 0L

    // مدة صلاحية الكاش — بعدها التطبيق يجيب نسخة جديدة من Firestore تلقائي
    private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 دقايق

    private const val GENERIC_FALLBACK =
        "مفهمتش قصدك بالظبط 🤔 ممكن توضحلي أكتر؟"
    private const val NOT_READY_FALLBACK =
        "البوت لسه بيتحضّر، جرّب تاني بعد شوية 🙏"

    /** لسه متاحة لو عايز تجبر تحديث فوري (مثلاً بعد إضافة سؤال وانت في وضع الاختبار). */
    fun invalidateCache() {
        cache = null
        lastFetchAt = 0L
    }

    private suspend fun loadKnowledge(): List<KnowledgeEntry>? {
        val isFresh = cache != null && (System.currentTimeMillis() - lastFetchAt) < CACHE_TTL_MS
        if (isFresh) return cache!!

        val db = trainDb ?: return null // init() لسه مانوديتش

        return withContext(Dispatchers.IO) {
            val entries = mutableListOf<KnowledgeEntry>()

            val primarySnapshot = db.collection("botKnowledge").get().await()
            entries += parseEntries(primarySnapshot)

            overflowDb?.let { odb ->
                try {
                    val overflowSnapshot = odb.collection("botKnowledge").get().await()
                    entries += parseEntries(overflowSnapshot)
                } catch (_: Exception) {
                    // لو الاحتياطية مش متاحة دلوقتي، نكمل بس بالأساسية
                }
            }

            cache = entries
            lastFetchAt = System.currentTimeMillis()
            entries
        }
    }

    private fun parseEntries(snapshot: QuerySnapshot): List<KnowledgeEntry> {
        return snapshot.documents.mapNotNull { doc ->
            val patterns = doc.getString("patterns") ?: return@mapNotNull null
            val reply = doc.getString("reply") ?: return@mapNotNull null
            val keywords = patterns.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (keywords.isEmpty() || reply.isBlank()) null else KnowledgeEntry(keywords, reply)
        }
    }

    // تبسيط للعربي: توحيد الألف والهمزات والتاء المربوطة وإزالة التشكيل، عشان المطابقة تبقى مرنة
    private fun normalizeArabic(text: String): String {
        return text
            .replace(Regex("[إأآا]"), "ا")
            .replace('ة', 'ه')
            .replace('ى', 'ي')
            .replace(Regex("[ًٌٍَُِّْ]"), "")
            .lowercase()
            .trim()
    }

    // ---------------- Gemini (Firebase AI Logic - Gemini Developer API) ----------------

    private const val GEMINI_MODEL = "gemini-2.5-flash"
    private const val HISTORY_LIMIT = 20

    private val geminiModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = GEMINI_MODEL,
            systemInstruction = content {
                text(
                    "أنت مساعد ذكي ودود جوه تطبيق دردشة اسمه Chatapp. " +
                        "رد بلغة المستخدم (افتراضيًا العربي) بشكل مختصر ومفيد وودود."
                )
            }
        )
    }

    private suspend fun askGemini(history: List<AiChatMessage>, lastUserMsg: String): String {
        // السياق: آخر 20 رسالة حقيقية، وGemini بيشترط إن أول رسالة في المحادثة تكون من المستخدم
        val mapped = history.dropLast(1)
            .filter { it.text.isNotBlank() && !it.isError }
            .map { content(role = if (it.isFromUser) "user" else "model") { text(it.text) } }
        val firstUserIdx = mapped.indexOfFirst { it.role == "user" }
        val chatHistory = if (firstUserIdx >= 0) mapped.drop(firstUserIdx).takeLast(HISTORY_LIMIT) else emptyList()

        val response = geminiModel.startChat(chatHistory).sendMessage(lastUserMsg)
        return response.text?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw Exception("Empty Gemini response")
    }

    /**
     * نفس توقيع الدالة القديمة بالظبط عشان AiChatViewModel يفضل شغال من غير أي تعديل.
     * history: كل الرسائل لحد دلوقتي — Gemini بياخدها كلها كسياق محادثة.
     */
    suspend fun sendMessage(history: List<AiChatMessage>): Result<String> = withContext(Dispatchers.IO) {
        val lastUserMsg = history.lastOrNull { it.isFromUser }?.text.orEmpty()
        if (lastUserMsg.isBlank()) {
            return@withContext Result.success(GENERIC_FALLBACK)
        }
        try {
            Result.success(askGemini(history, lastUserMsg))
        } catch (e: Exception) {
            // سجل صامت (logcat) بسبب فشل Gemini عشان نقدر نشخص من غير ما نزعج المستخدم
            Log.e("AiChat", "Gemini failed (${e.javaClass.simpleName}): ${e.message}", e)
            val name = e.javaClass.simpleName
            val msg = e.message.orEmpty()
            val quotaHit = name.contains("quota", true) ||
                msg.contains("429") || msg.contains("quota", true) || msg.contains("rate limit", true)
            if (quotaHit) {
                Result.success("المساعد وصل لحد الاستخدام المجاني دلوقتي ⏳ جرّب تاني بعد شوية.")
            } else {
                // أي مشكلة تانية في Gemini (مفيش نت، الخدمة مش متفعلة...) → نرجع للبوت المحلي
                localBotReply(lastUserMsg)
            }
        }
    }

    /** البوت المحلي القديم (كلمات مفتاحية من train-38762) — احتياطي لو Gemini مش متاح. */
    private suspend fun localBotReply(lastUserMsg: String): Result<String> {
        return try {

            val knowledge = loadKnowledge()
                ?: return Result.success(NOT_READY_FALLBACK)

            val normMsg = normalizeArabic(lastUserMsg)

            var best: KnowledgeEntry? = null
            var bestScore = 0.0

            knowledge.forEach { entry ->
                var score = 0.0
                entry.keywords.forEach { rawKeyword ->
                    val k = normalizeArabic(rawKeyword)
                    if (k.isEmpty()) return@forEach
                    // كلمات أطول (5 حروف فأكتر) = أكثر تحديدًا = وزن أعلى، زي نظام الموقع بالظبط
                    val weight = if (k.length >= 5) 2.0 else 1.0
                    if (normMsg.contains(k)) score += weight
                }
                if (score > bestScore) {
                    bestScore = score
                    best = entry
                }
            }

            val reply = if (best != null && bestScore >= 1.0) best!!.reply else GENERIC_FALLBACK
            Result.success(reply)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
