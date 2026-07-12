package com.creatix.chatapp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatix.chatapp.data.ChatUser
import com.creatix.chatapp.data.Message
import com.creatix.chatapp.data.GroupMessage
import com.creatix.chatapp.data.chatIdFor
import com.creatix.chatapp.repository.ChatRepository
import com.creatix.chatapp.repository.PresenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository = ChatRepository(),
    private val presenceRepository: PresenceRepository = PresenceRepository()
) : ViewModel() {

    private val _users = MutableStateFlow<List<ChatUser>>(emptyList())
    val users: StateFlow<List<ChatUser>> = _users

    private val _presenceMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val presenceMap: StateFlow<Map<String, Boolean>> = _presenceMap

    fun observePresence() {
        viewModelScope.launch {
            try {
                presenceRepository.observeAllPresence().collect { _presenceMap.value = it }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val _myDisplayName = MutableStateFlow("أنا")
    val myDisplayName: StateFlow<String> = _myDisplayName

    fun loadMyDisplayName(uid: String) {
        viewModelScope.launch {
            _myDisplayName.value = repository.getCurrentUserName(uid)
        }
    }

    private val _myProfile = MutableStateFlow<ChatUser?>(null)
    val myProfile: StateFlow<ChatUser?> = _myProfile

    fun loadMyProfile(uid: String) {
        viewModelScope.launch {
            _myProfile.value = repository.getCurrentUser(uid)
        }
    }

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _groupMessages = MutableStateFlow<List<GroupMessage>>(emptyList())
    val groupMessages: StateFlow<List<GroupMessage>> = _groupMessages

    private val _otherUserTyping = MutableStateFlow(false)
    val otherUserTyping: StateFlow<Boolean> = _otherUserTyping

    private val _groupTypingNames = MutableStateFlow<List<String>>(emptyList())
    val groupTypingNames: StateFlow<List<String>> = _groupTypingNames

    fun loadUsers(currentUid: String) {
        viewModelScope.launch {
            repository.observeUsers(currentUid).collect { _users.value = it }
        }
    }

    // ---------------------------------------------------------------
    // عدد الرسائل غير المقروءة + "بيكتب الآن" لكل يوزر في قائمة المحادثات
    // ---------------------------------------------------------------

    private val _unreadCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    /** uid -> عدد الرسائل اللي ما اتشافتش. لو مفيش قيمة أو القيمة صفر يبقى معندوش رسائل جديدة */
    val unreadCounts: StateFlow<Map<String, Int>> = _unreadCounts

    private val _typingUsers = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    /** uid -> هل ده بيكتب لي دلوقتي؟ */
    val typingUsers: StateFlow<Map<String, Boolean>> = _typingUsers

    private val observedChatExtras = mutableSetOf<String>()

    /** بتتنادى كل ما قائمة اليوزرز تتحدث؛ بتبدأ مراقبة الرسائل الغير مقروءة والكتابة لكل يوزر جديد بس (مرة واحدة لكل واحد) */
    fun observeChatListExtras(myUid: String, users: List<ChatUser>) {
        users.forEach { user ->
            if (!observedChatExtras.add(user.uid)) return@forEach
            val chatId = chatIdFor(myUid, user.uid)

            viewModelScope.launch {
                try {
                    repository.observeUnreadCount(chatId, myUid).collect { count ->
                        _unreadCounts.value = _unreadCounts.value + (user.uid to count)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            viewModelScope.launch {
                try {
                    repository.observeTyping(chatId, user.uid).collect { isTyping ->
                        _typingUsers.value = _typingUsers.value + (user.uid to isTyping)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
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

    fun loadGroupMessages() {
        viewModelScope.launch {
            try {
                repository.observeGroupMessages().collect { _groupMessages.value = it }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendGroupMessage(context: Context, senderId: String, senderName: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.sendGroupMessage(context.applicationContext, senderId, senderName, text.trim())
        }
    }

    fun observeGroupTyping(myUid: String) {
        viewModelScope.launch {
            try {
                repository.observeGroupTyping(myUid).collect { _groupTypingNames.value = it }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setGroupTyping(myUid: String, myName: String, isTyping: Boolean) {
        repository.setGroupTyping(myUid, myName, isTyping)
    }

    // ---------------------------------------------------------------
    // عدد رسائل الجروب الغير مقروءة
    // ---------------------------------------------------------------

    private val _groupUnreadCount = MutableStateFlow(0)
    val groupUnreadCount: StateFlow<Int> = _groupUnreadCount

    /** بيراقب عدد رسائل الجروب اللي جت من غيري بعد آخر مرة فتحت فيها الجروب */
    fun observeGroupUnreadCount(myUid: String) {
        viewModelScope.launch {
            try {
                combine(
                    repository.observeGroupMessages(),
                    repository.observeGroupLastRead(myUid)
                ) { messages, lastRead ->
                    messages.count { it.senderId != myUid && it.timestamp > lastRead }
                }.collect { _groupUnreadCount.value = it }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** بتتنادى وقت ما اليوزر يفتح شاشة الجروب عشان نصفّر العداد */
    fun markGroupAsRead(myUid: String) {
        viewModelScope.launch {
            try {
                repository.markGroupAsRead(myUid)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
