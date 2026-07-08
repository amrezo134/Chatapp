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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creatix.chatapp.data.ChatUser
import com.creatix.chatapp.data.Message
import com.creatix.chatapp.data.chatIdFor
import com.creatix.chatapp.viewmodel.AuthViewModel
import com.creatix.chatapp.viewmodel.ChatViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    otherUser: ChatUser,
    onBack: () -> Unit
) {
    val myUid = authViewModel.currentUid ?: return
    val context = LocalContext.current
    val chatId = remember { chatIdFor(myUid, otherUser.uid) }
    val messages by chatViewModel.messages.collectAsState()
    val otherUserTyping by chatViewModel.otherUserTyping.collectAsState()
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(chatId) {
        chatViewModel.loadMessages(chatId, myUid)
        chatViewModel.observeTyping(chatId, otherUser.uid)
    }

    // لما اليوزر يسيب شاشة الشات، نبلغ إنه بقى "مش بيكتب"
    DisposableEffect(chatId) {
        onDispose { chatViewModel.setTyping(chatId, myUid, false) }
    }

    // Debounce بسيط: لو اليوزر وقف يكتب لمدة 2 ثانية، نبعت "خلص كتابة"
    LaunchedEffect(text) {
        if (text.isNotEmpty()) {
            chatViewModel.setTyping(chatId, myUid, true)
            delay(2000)
            chatViewModel.setTyping(chatId, myUid, false)
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
                        Text(otherUser.displayName.ifBlank { otherUser.email })
                        if (otherUserTyping) {
                            Text(
                                "بيكتب دلوقتي...",
                                fontSize = 12.sp,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (otherUser.bio.isNotBlank()) {
                            Text(
                                otherUser.bio,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("‹") }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("اكتب رسالة...") }
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = {
                    chatViewModel.sendMessage(context, myUid, otherUser.uid, text)
                    text = ""
                    chatViewModel.setTyping(chatId, myUid, false)
                }) {
                    Icon(Icons.Default.Send, contentDescription = "إرسال")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages) { message ->
                MessageBubble(message = message, isMine = message.senderId == myUid)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message, isMine: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (isMine) Color(0xFF1565C0) else Color(0xFFE0E0E0))
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .widthIn(max = 260.dp)
        ) {
            Column {
                Text(
                    text = message.text,
                    color = if (isMine) Color.White else Color.Black
                )
                // علامة "اتقرت" تظهر بس على رسايلي أنا
                if (isMine) {
                    Text(
                        text = if (message.seen) "✓✓ تمت القراءة" else "✓ اتبعتت",
                        fontSize = 10.sp,
                        color = if (message.seen) Color(0xFFB3E5FC) else Color(0xFFCFD8DC),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}
