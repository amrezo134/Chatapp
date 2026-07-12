package com.creatix.chatapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

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
                    chatViewModel.sendCustomGroupMessage(context, group.id, myUid, myName, text)
                    text = ""
                    chatViewModel.setCustomGroupTyping(group.id, myUid, myName, false)
                }) {
                    Icon(Icons.Default.Send, contentDescription = "إرسال")
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
                items(messages) { message ->
                    CustomGroupMessageBubble(message = message, isMine = message.senderId == myUid)
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
}

@Composable
private fun CustomGroupMessageBubble(message: GroupMessage, isMine: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .widthIn(max = 260.dp)
        ) {
            Column {
                if (!isMine) {
                    Text(message.senderName, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
                Text(text = message.text, color = if (isMine) Color.White else Color.Black)
            }
        }
    }
}
