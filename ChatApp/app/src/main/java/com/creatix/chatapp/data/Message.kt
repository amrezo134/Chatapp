package com.creatix.chatapp.data

data class Message(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val seen: Boolean = false
)

/**
 * كل محادثة بين شخصين ليها ID ثابت = دمج الـ uid بتوع الاتنين مرتبين أبجديًا
 * عشان تضمن إن نفس الشات id يترجع مهما مين بدأ المحادثة.
 */
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
