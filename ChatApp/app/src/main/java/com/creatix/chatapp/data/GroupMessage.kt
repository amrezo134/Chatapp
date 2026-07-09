package com.creatix.chatapp.data

data class GroupMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

// كل الجروب العام بيستخدم مستند ثابت واحد بالـ id ده
const val GLOBAL_GROUP_ID = "global_group"
