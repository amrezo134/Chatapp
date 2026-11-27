package com.creatix.chatapp.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class Message(
    val id: String = "",
    val senderId: String = "",
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
    val reactions: Map<String, String> = emptyMap(), // userId -> emoji

    /**
     * وقت السيرفر لحظة تسجيل الرسالة — بيتكتب من Firestore نفسه مش من التليفون، وبنستخدمه
     * في ترتيب الرسايل بس عشان فرق الساعات بين الأجهزة ما يبوظش الترتيب. العرض على الشاشة
     * بيفضل بوقت التليفون (timestamp). بيبقى null في الرسايل القديمة ولو الكتابة لسه pending.
     */
    @get:ServerTimestamp
    val serverTimestamp: Timestamp? = null
)

fun chatIdFor(uidA: String, uidB: String): String {
    return if (uidA < uidB) "${uidA}_${uidB}" else "${uidB}_${uidA}"
}

data class ChatPreview(
    val chatId: String = "",
    val otherUser: ChatUser = ChatUser(),
    val lastMessage: String = "",
    val lastTimestamp: Long = 0L,
    val unreadCount: Int = 0
)
