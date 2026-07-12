package com.creatix.chatapp.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creatix.chatapp.data.ChatGroup
import com.creatix.chatapp.data.GroupMessage
import com.creatix.chatapp.viewmodel.AuthViewModel
import com.creatix.chatapp.viewmodel.ChatViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomGroupChatScreen(
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    group: ChatGroup,
    onBack: () -> Unit
) {
    val myUid = authViewModel.currentUid ?: return
    val context = LocalContext.current
    val myName by chatViewModel.myDisplayName.collectAsState()
    LaunchedEffect(myUid) { chatViewModel.loadMyDisplayName(myUid) }

    val messages by chatViewModel.customGroupMessages.collectAsState()
    val typingNames by chatViewModel.customGroupTypingNames.collectAsState()
    val allUsers by chatViewModel.users.collectAsState()
    val myCustomGroups by chatViewModel.myCustomGroups.collectAsState()
    var text by remember { mutableStateOf("") }
    var editingMessage by remember { mutableStateOf<GroupMessage?>(null) }
    var forwardTarget by remember { mutableStateOf<GroupMessage?>(null) }
    var deleteTarget by remember { mutableStateOf<GroupMessage?>(null) }
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(group.id) {
        chatViewModel.loadCustomGroupMessages(group.id)
        chatViewModel.markCustomGroupAsRead(group.id, myUid)
        chatViewModel.observeCustomGroupTyping(group.id, myUid)
    }

    DisposableEffect(group.id) {
        onDispose {
            chatViewModel.setCustomGroupTyping(group.id, myUid, myName, false)
            chatViewModel.markCustomGroupAsRead(group.id, myUid)
        }
    }

    LaunchedEffect(text, myName) {
        if (text.isNotEmpty()) {
            chatViewModel.setCustomGroupTyping(group.id, myUid, myName, true)
            delay(2000)
            chatViewModel.setCustomGroupTyping(group.id, myUid, myName, false)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(group.name)
                        Text(
                            "${group.memberIds.size} عضو",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Text("‹") } }
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                val currentEdit = editingMessage
                if (currentEdit != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تعديل الرسالة", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Text(
                                currentEdit.text,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { editingMessage = null; text = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "إلغاء التعديل")
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("اكتب رسالة للجروب...") }
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = {
                        val currentlyEditing = editingMessage
                        if (currentlyEditing != null) {
                            chatViewModel.editCustomGroupMessage(group.id, currentlyEditing.id, text)
                            editingMessage = null
                            text = ""
                        } else {
                            chatViewModel.sendCustomGroupMessage(context, group.id, myUid, myName, text)
                            text = ""
                        }
                        chatViewModel.setCustomGroupTyping(group.id, myUid, myName, false)
                    }) {
                        Icon(Icons.Default.Send, contentDescription = "إرسال")
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages, key = { it.id.ifBlank { it.timestamp.toString() } }) { message ->
                    CustomGroupMessageBubble(
                        message = message,
                        isMine = message.senderId == myUid,
                        onCopy = { clipboardManager.setText(AnnotatedString(message.text)) },
                        onForward = { forwardTarget = message },
                        onEdit = {
                            editingMessage = message
                            text = message.text
                        },
                        onDelete = { deleteTarget = message }
                    )
                }
            }

            val typingText = when (typingNames.size) {
                0 -> null
                1 -> "${typingNames[0]} بيكتب دلوقتي..."
                else -> "${typingNames.joinToString("، ")} بيكتبوا دلوقتي..."
            }
            if (typingText != null) {
                Text(
                    text = typingText,
                    fontSize = 11.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 4.dp)
                )
            }
        }
    }

    val currentForward = forwardTarget
    if (currentForward != null) {
        ForwardDialog(
            users = allUsers,
            groups = myCustomGroups.filter { it.id != group.id },
            onDismiss = { forwardTarget = null },
            onForwardToGlobalGroup = {
                chatViewModel.sendGroupMessage(context, myUid, myName, currentForward.text, forwarded = true)
                forwardTarget = null
            },
            onForwardToGroup = { targetGroup ->
                chatViewModel.sendCustomGroupMessage(context, targetGroup.id, myUid, myName, currentForward.text, forwarded = true)
                forwardTarget = null
            },
            onForwardToUser = { user ->
                chatViewModel.sendMessage(
                    context = context,
                    senderId = myUid,
                    receiverId = user.uid,
                    text = currentForward.text,
                    forwarded = true
                )
                forwardTarget = null
            }
        )
    }

    val currentDelete = deleteTarget
    if (currentDelete != null) {
        DeleteMessageConfirmDialog(
            onDismiss = { deleteTarget = null },
            onConfirm = { chatViewModel.deleteCustomGroupMessage(group.id, currentDelete.id) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomGroupMessageBubble(
    message: GroupMessage,
    isMine: Boolean,
    onCopy: () -> Unit = {},
    onForward: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    var menuExpanded by remember(message.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .combinedClickable(
                    enabled = !message.deleted,
                    onClick = {},
                    onLongClick = { menuExpanded = true }
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .widthIn(max = 260.dp)
        ) {
            MessageActionsMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                canEdit = isMine && message.text.isNotBlank(),
                canDelete = isMine,
                onCopy = onCopy,
                onForward = onForward,
                onEdit = onEdit,
                onDelete = onDelete
            )
            if (message.deleted) {
                Text(
                    text = "🚫 تم حذف هذه الرسالة",
                    fontStyle = FontStyle.Italic,
                    fontSize = 13.sp,
                    color = if (isMine) Color.White.copy(alpha = 0.8f) else Color.Gray
                )
                return@Box
            }
            Column {
                if (!isMine) {
                    Text(message.senderName, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
                if (message.forwarded) {
                    Text(
                        text = "↪ تم التوجيه",
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                        color = if (isMine) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(text = message.text, color = if (isMine) Color.White else Color.Black)
                if (message.edited) {
                    Text(
                        text = "(معدلة)",
                        fontSize = 10.sp,
                        fontStyle = FontStyle.Italic,
                        color = if (isMine) Color.White.copy(alpha = 0.7f) else Color.Gray
                    )
                }
            }
        }
    }
}
