package com.creatix.chatapp.data

data class ChatUser(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val bio: String = "",
    val online: Boolean = false,
    val lastSeen: Long = 0L,
    val fcmToken: String = ""
)
