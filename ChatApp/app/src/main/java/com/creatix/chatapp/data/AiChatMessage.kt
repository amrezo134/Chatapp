package com.creatix.chatapp.data

data class AiChatMessage(
    val id: String = System.nanoTime().toString(),
    val text: String = "",
    val isFromUser: Boolean = true,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
