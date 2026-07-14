package com.creatix.chatapp.data

data class GroupMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val receiverId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val seen: Boolean = false,
    val fileUrl: String = "",
    val fileName: String = "",
    val fileType: String = "", // image, video, audio, document, contact, poll, event, sticker
    val durationMs: Long = 0L,          // للصوت والفيديو
    val contactName: String = "",       // لمشاركة جهة اتصال
    val contactPhone: String = "",
    val pollQuestion: String = "",      // للاستطلاع
    val pollOptions: List<String> = emptyList(),
    val pollVotes: Map<String, Int> = emptyMap(), // userId -> optionIndex
    val eventTitle: String = "",        // للمناسبة
    val eventTimestamp: Long = 0L,
    val replyToId: String = "",
    val replyToText: String = "",
    val replyToSenderName: String = "",
    val edited: Boolean = false,
    val deleted: Boolean = false,
    val forwarded: Boolean = false,
    val reactions: Map<String, String> = emptyMap() // userId -> emoji
)
// كل الجروب العام بيستخدم مستند ثابت واحد بالـ id ده
const val GLOBAL_GROUP_ID = "global_group"
