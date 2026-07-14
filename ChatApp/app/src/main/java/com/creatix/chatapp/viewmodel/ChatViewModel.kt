package com.creatix.chatapp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatix.chatapp.data.ChatUser
import com.creatix.chatapp.data.Message
import com.creatix.chatapp.data.GroupMessage
import com.creatix.chatapp.data.ChatGroup
import com.creatix.chatapp.data.chatIdFor
import com.creatix.chatapp.repository.ChatRepository
import com.creatix.chatapp.repository.PresenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    // البحث عن مستخدم بالاسم
    // ---------------------------------------------------------------

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    /** قائمة اليوزرز بعد الفلترة بالاسم (لو الكلمة فاضية بترجع كل اليوزرز) */
    val filteredUsers: StateFlow<List<ChatUser>> = combine(_users, _searchQuery) { users, query ->
        if (query.isBlank()) {
            users
        } else {
            val normalizedQuery = query.trim()
            users.filter { user ->
                user.displayName.contains(normalizedQuery, ignoreCase = true) ||
                    user.email.contains(normalizedQuery, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
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

    fun sendMessage(
        context: Context,
        senderId: String,
        receiverId: String,
        text: String,
        replyTo: Message? = null,
        replyToSenderName: String = "",
        forwarded: Boolean = false,
        fileUrl: String = "",
        fileName: String = "",
        fileType: String = "",
        durationMs: Long = 0L
    ) {
        if (text.isBlank() && fileUrl.isBlank()) return
        viewModelScope.launch {
            repository.sendMessage(
                context.applicationContext,
                senderId,
                receiverId,
                text.trim(),
                replyTo = replyTo,
                replyToSenderName = replyToSenderName,
                forwarded = forwarded,
                fileUrl = fileUrl,
                fileName = fileName,
                fileType = fileType,
                durationMs = durationMs
            )
        }
    }

    /** بترفع بايتات ملف على Cloudflare وترجع رابطه - بتستخدمها الشاشة قبل ما تبعت رسالة فيها مرفق */
    suspend fun uploadFile(bytes: ByteArray, fileName: String, mimeType: String): String =
        repository.uploadFileToCloudflare(bytes, fileName, mimeType)

    /** إرسال رسالة "مشاركة جهة اتصال" */
    fun sendContactMessage(context: Context, senderId: String, otherUid: String, contactName: String, contactPhone: String) {
        viewModelScope.launch {
            repository.sendMessage(
                context.applicationContext, senderId, otherUid, text = "",
                contactName = contactName, contactPhone = contactPhone
            )
        }
    }

    /** إرسال رسالة "استطلاع رأي" */
    fun sendPollMessage(context: Context, senderId: String, otherUid: String, question: String, options: List<String>) {
        viewModelScope.launch {
            repository.sendMessage(
                context.applicationContext, senderId, otherUid, text = "",
                pollQuestion = question, pollOptions = options
            )
        }
    }

    /** إرسال رسالة "مناسبة" */
    fun sendEventMessage(context: Context, senderId: String, otherUid: String, eventTitle: String, eventTimestamp: Long) {
        viewModelScope.launch {
            repository.sendMessage(
                context.applicationContext, senderId, otherUid, text = "",
                eventTitle = eventTitle, eventTimestamp = eventTimestamp
            )
        }
    }

    // ---------------------------------------------------------------
    // تعديل وحذف الرسائل (شات خاص)
    // ---------------------------------------------------------------

    fun editMessage(chatId: String, messageId: String, newText: String) {
        if (newText.isBlank()) return
        viewModelScope.launch {
            try {
                repository.editMessage(chatId, messageId, newText.trim())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteMessage(chatId: String, messageId: String) {
        viewModelScope.launch {
            try {
                repository.deleteMessage(chatId, messageId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** بيحدد تفاعل إيموجي على رسالة، أو بيشيله لو نفس الإيموجي متحدد قبل كده */
    fun toggleReaction(chatId: String, message: Message, myUid: String, emoji: String) {
        viewModelScope.launch {
            try {
                val newEmoji = if (message.reactions[myUid] == emoji) null else emoji
                repository.setMessageReaction(chatId, message.id, myUid, newEmoji)
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

    fun sendGroupMessage(
        context: Context,
        senderId: String,
        senderName: String,
        text: String,
        forwarded: Boolean = false,
        fileUrl: String = "",
        fileName: String = "",
        fileType: String = "",
        durationMs: Long = 0L
    ) {
        if (text.isBlank() && fileUrl.isBlank()) return
        viewModelScope.launch {
            repository.sendGroupMessage(
                context.applicationContext, senderId, senderName, text.trim(),
                forwarded = forwarded,
                fileUrl = fileUrl,
                fileName = fileName,
                fileType = fileType,
                durationMs = durationMs
            )
        }
    }

    // ---------------------------------------------------------------
    // تعديل وحذف رسائل الجروب العام
    // ---------------------------------------------------------------

    fun editGroupMessage(messageId: String, newText: String) {
        if (newText.isBlank()) return
        viewModelScope.launch {
            try {
                repository.editGroupMessage(messageId, newText.trim())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteGroupMessage(messageId: String) {
        viewModelScope.launch {
            try {
                repository.deleteGroupMessage(messageId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** بيحدد تفاعل إيموجي على رسالة في الجروب العام، أو بيشيله لو نفس الإيموجي متحدد قبل كده */
    fun toggleGroupReaction(message: GroupMessage, myUid: String, emoji: String) {
        viewModelScope.launch {
            try {
                val newEmoji = if (message.reactions[myUid] == emoji) null else emoji
                repository.setGroupMessageReaction(message.id, myUid, newEmoji)
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

    // ---------------------------------------------------------------
    // الجروبات المخصصة (Custom Groups)
    // ---------------------------------------------------------------

    private val _myCustomGroups = MutableStateFlow<List<ChatGroup>>(emptyList())
    val myCustomGroups: StateFlow<List<ChatGroup>> = _myCustomGroups

    fun observeMyCustomGroups(myUid: String) {
        viewModelScope.launch {
            try {
                repository.observeMyCustomGroups(myUid).collect { _myCustomGroups.value = it }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val _createGroupState = MutableStateFlow<CreateGroupState>(CreateGroupState.Idle)
    val createGroupState: StateFlow<CreateGroupState> = _createGroupState

    /** بتعمل جروب جديد؛ لو نجح بيتغير الـ state لـ Success ومعاه id الجروب عشان ننتقل لشاشته */
    fun createCustomGroup(name: String, ownerId: String, memberIds: List<String>) {
        if (name.isBlank()) {
            _createGroupState.value = CreateGroupState.Error("اكتب اسم للجروب الأول")
            return
        }
        if (memberIds.isEmpty()) {
            _createGroupState.value = CreateGroupState.Error("اختار عضو واحد على الأقل")
            return
        }
        _createGroupState.value = CreateGroupState.Loading
        viewModelScope.launch {
            try {
                val groupId = repository.createCustomGroup(name.trim(), ownerId, memberIds)
                _createGroupState.value = CreateGroupState.Success(groupId)
            } catch (e: Exception) {
                _createGroupState.value = CreateGroupState.Error(e.message ?: "حصل خطأ، حاول تاني")
            }
        }
    }

    fun resetCreateGroupState() {
        _createGroupState.value = CreateGroupState.Idle
    }

    private val _customGroupMessages = MutableStateFlow<List<GroupMessage>>(emptyList())
    val customGroupMessages: StateFlow<List<GroupMessage>> = _customGroupMessages

    fun loadCustomGroupMessages(groupId: String) {
        viewModelScope.launch {
            try {
                repository.observeCustomGroupMessages(groupId).collect { _customGroupMessages.value = it }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendCustomGroupMessage(
        context: Context,
        groupId: String,
        senderId: String,
        senderName: String,
        text: String,
        forwarded: Boolean = false,
        fileUrl: String = "",
        fileName: String = "",
        fileType: String = "",
        durationMs: Long = 0L
    ) {
        if (text.isBlank() && fileUrl.isBlank()) return
        viewModelScope.launch {
            repository.sendCustomGroupMessage(
                context.applicationContext, groupId, senderId, senderName, text.trim(),
                forwarded = forwarded,
                fileUrl = fileUrl,
                fileName = fileName,
                fileType = fileType,
                durationMs = durationMs
            )
        }
    }

    // ---------------------------------------------------------------
    // تعديل وحذف رسائل الجروبات المخصصة
    // ---------------------------------------------------------------

    fun editCustomGroupMessage(groupId: String, messageId: String, newText: String) {
        if (newText.isBlank()) return
        viewModelScope.launch {
            try {
                repository.editCustomGroupMessage(groupId, messageId, newText.trim())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteCustomGroupMessage(groupId: String, messageId: String) {
        viewModelScope.launch {
            try {
                repository.deleteCustomGroupMessage(groupId, messageId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** بيحدد تفاعل إيموجي على رسالة في جروب مخصص، أو بيشيله لو نفس الإيموجي متحدد قبل كده */
    fun toggleCustomGroupReaction(groupId: String, message: GroupMessage, myUid: String, emoji: String) {
        viewModelScope.launch {
            try {
                val newEmoji = if (message.reactions[myUid] == emoji) null else emoji
                repository.setCustomGroupMessageReaction(groupId, message.id, myUid, newEmoji)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val _customGroupTypingNames = MutableStateFlow<List<String>>(emptyList())
    val customGroupTypingNames: StateFlow<List<String>> = _customGroupTypingNames

    fun observeCustomGroupTyping(groupId: String, myUid: String) {
        viewModelScope.launch {
            try {
                repository.observeCustomGroupTyping(groupId, myUid).collect { _customGroupTypingNames.value = it }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setCustomGroupTyping(groupId: String, myUid: String, myName: String, isTyping: Boolean) {
        repository.setCustomGroupTyping(groupId, myUid, myName, isTyping)
    }

    private val _customGroupUnreadCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    /** groupId -> عدد الرسائل الغير مقروءة في الجروبات المخصصة، بتستخدم في قائمة المحادثات */
    val customGroupUnreadCounts: StateFlow<Map<String, Int>> = _customGroupUnreadCounts

    private val observedCustomGroupExtras = mutableSetOf<String>()

    /** بتتنادى كل ما قائمة جروباتي تتحدث؛ بتبدأ مراقبة عدد الرسائل الغير مقروءة لكل جروب جديد بس */
    fun observeCustomGroupListExtras(myUid: String, groups: List<ChatGroup>) {
        groups.forEach { group ->
            if (!observedCustomGroupExtras.add(group.id)) return@forEach
            viewModelScope.launch {
                try {
                    combine(
                        repository.observeCustomGroupMessages(group.id),
                        repository.observeCustomGroupLastRead(group.id, myUid)
                    ) { messages, lastRead ->
                        messages.count { it.senderId != myUid && it.timestamp > lastRead }
                    }.collect { count ->
                        _customGroupUnreadCounts.value = _customGroupUnreadCounts.value + (group.id to count)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun markCustomGroupAsRead(groupId: String, myUid: String) {
        viewModelScope.launch {
            try {
                repository.markCustomGroupAsRead(groupId, myUid)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

/** حالة عملية إنشاء جروب مخصص جديد */
sealed class CreateGroupState {
    data object Idle : CreateGroupState()
    data object Loading : CreateGroupState()
    data class Success(val groupId: String) : CreateGroupState()
    data class Error(val message: String) : CreateGroupState()
}
