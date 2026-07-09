package com.creatix.chatapp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatix.chatapp.data.ChatUser
import com.creatix.chatapp.data.Message
import com.creatix.chatapp.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository = ChatRepository()
) : ViewModel() {

    private val _users = MutableStateFlow<List<ChatUser>>(emptyList())
    val users: StateFlow<List<ChatUser>> = _users

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _otherUserTyping = MutableStateFlow(false)
    val otherUserTyping: StateFlow<Boolean> = _otherUserTyping

    fun loadUsers(currentUid: String) {
        viewModelScope.launch {
            repository.observeUsers(currentUid).collect { _users.value = it }
        }
    }

    fun loadMessages(chatId: String, myUid: String, otherUid: String) {
            viewModelScope.launch {
                repository.ensureChatDocument(myUid, otherUid)
                try {
                    repository.observeMessages(chatId).collect { list ->
                        _messages.value = list
                        repository.markMessagesAsSeen(chatId, myUid, list)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

    fun sendMessage(context: Context, senderId: String, receiverId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.sendMessage(context.applicationContext, senderId, receiverId, text.trim())
        }
    }

    fun observeTyping(chatId: String, otherUid: String) {
            viewModelScope.launch {
                try {
                    repository.observeTyping(chatId, otherUid).collect { _otherUserTyping.value = it }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

    fun setTyping(chatId: String, myUid: String, isTyping: Boolean) {
        repository.setTyping(chatId, myUid, isTyping)
    }
}
