package com.creatix.chatapp.repository

import com.creatix.chatapp.data.AiChatMessage
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * مساعد ذكي محلي بالكامل — مفيش أي اتصال بأي AI خارجي (لا Gemini ولا DeepSeek ولا غيره).
 *
 * الفكرة: بيانات التدريب (كلمات مفتاحية -> رد) بتتخزن في Firestore تحت
 * مجموعة "botKnowledge"، وبتتضاف من لوحة تحكم ويب منفصلة (admin-training.html).
 * التطبيق بيسحب البيانات دي مرة واحدة (وبيكاشها في الذاكرة)، وبيطابقها محليًا
 * مع رسالة المستخدم — من غير ما يبعت أي حاجة لأي سيرفر AI برة.
 *
 * شكل الـ document في Firestore:
 * {
 *   "patterns": "الأسعار, بكام, تكلفة",   // كلمات مفتاحية مفصولة بفاصلة
 *   "reply": "الأسعار بتتحدد حسب حجم المشروع..."
 * }
 */
object AiChatRepository {

    private data class KnowledgeEntry(val keywords: List<String>, val reply: String)

    private val db = FirebaseFirestore.getInstance()
    private var cache: List<KnowledgeEntry>? = null
    private var lastFetchAt: Long = 0L

    // مدة صلاحية الكاش — بعدها التطبيق يجيب نسخة جديدة من Firestore تلقائي
    private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 دقايق

    private const val GENERIC_FALLBACK =
        "مفهمتش قصدك بالظبط 🤔 ممكن توضحلي أكتر؟"

    /** لسه متاحة لو عايز تجبر تحديث فوري (مثلاً بعد إضافة سؤال وانت في وضع الاختبار). */
    fun invalidateCache() {
        cache = null
        lastFetchAt = 0L
    }

    private suspend fun loadKnowledge(): List<KnowledgeEntry> {
        val isFresh = cache != null && (System.currentTimeMillis() - lastFetchAt) < CACHE_TTL_MS
        if (isFresh) return cache!!

        return withContext(Dispatchers.IO) {
            val snapshot = db.collection("botKnowledge").get().await()
            val entries = snapshot.documents.mapNotNull { doc ->
                val patterns = doc.getString("patterns") ?: return@mapNotNull null
                val reply = doc.getString("reply") ?: return@mapNotNull null
                val keywords = patterns.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (keywords.isEmpty() || reply.isBlank()) null else KnowledgeEntry(keywords, reply)
            }
            cache = entries
            lastFetchAt = System.currentTimeMillis()
            entries
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

    /**
     * نفس توقيع الدالة القديمة بالظبط عشان AiChatViewModel يفضل شغال من غير أي تعديل.
     * history: كل الرسائل لحد دلوقتي — إحنا بناخد آخر رسالة من المستخدم بس ونطابقها.
     */
    suspend fun sendMessage(history: List<AiChatMessage>): Result<String> = withContext(Dispatchers.IO) {
        try {
            val lastUserMsg = history.lastOrNull { it.isFromUser }?.text.orEmpty()
            if (lastUserMsg.isBlank()) {
                return@withContext Result.success(GENERIC_FALLBACK)
            }

            val normMsg = normalizeArabic(lastUserMsg)
            val knowledge = loadKnowledge()

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
