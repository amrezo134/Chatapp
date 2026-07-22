package com.creatix.chatapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatix.chatapp.data.AiChatMessage
import com.creatix.chatapp.repository.AiChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AiChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<AiChatMessage>>(
        listOf(
            AiChatMessage(
                text = "أهلاً! أنا مساعدك الذكي جوه التطبيق، اسألني أي حاجة 👋",
                isFromUser = false
            )
        )
    )
    val messages: StateFlow<List<AiChatMessage>> = _messages.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || _isSending.value) return

        val userMsg = AiChatMessage(text = trimmed, isFromUser = true)
        _messages.value = _messages.value + userMsg
        _isSending.value = true

        viewModelScope.launch {
            val history = _messages.value
            val result = AiChatRepository.sendMessage(history)
            result.onSuccess { reply ->
                _messages.value = _messages.value + AiChatMessage(text = reply, isFromUser = false)
            }.onFailure { error ->
                _messages.value = _messages.value + AiChatMessage(
                    text = error.message ?: "حصل خطأ، حاول تاني",
                    isFromUser = false,
                    isError = true
                )
            }
            _isSending.value = false
        }
    }
}
