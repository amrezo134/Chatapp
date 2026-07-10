package com.creatix.chatapp.data

data class ChatGroup(
    val id: String = "",
    val name: String = "",
    val ownerId: String = "",
    val memberIds: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastTimestamp: Long = 0L
)
