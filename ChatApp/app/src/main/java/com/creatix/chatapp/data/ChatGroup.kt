package com.creatix.chatapp.data

data class ChatGroup(
    val id: String = "",
    val name: String = "",
    val ownerId: String = "",
    val memberIds: List<String> = emptyList(),
    val adminIds: List<String> = emptyList(),
    val photoUrl: String = "",
    val bio: String = "",
    val lastMessage: String = "",
    val lastTimestamp: Long = 0L
) {
    /** صاحب الجروب هو مشرف دايمًا حتى لو مش موجود صراحة في adminIds */
    fun isAdmin(uid: String): Boolean = uid.isNotBlank() && (uid == ownerId || adminIds.contains(uid))
    fun isOwner(uid: String): Boolean = uid.isNotBlank() && uid == ownerId
}
