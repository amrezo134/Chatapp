package com.creatix.chatapp.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.creatix.chatapp.data.ChatUser
import com.creatix.chatapp.data.Message
import com.creatix.chatapp.data.chatIdFor
import com.creatix.chatapp.viewmodel.AuthViewModel
import com.creatix.chatapp.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ChatScreen(
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    otherUser: ChatUser,
    onBack: () -> Unit,
    onOpenProfilePhoto: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val myUid = authViewModel.currentUid ?: return
    val context = LocalContext.current
    val chatId = remember { chatIdFor(myUid, otherUser.uid) }
    val messages by chatViewModel.messages.collectAsState()
    val otherUserTyping by chatViewModel.otherUserTyping.collectAsState()
    var text by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<Message?>(null) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(chatId) {
        chatViewModel.loadMessages(chatId, myUid, otherUser.uid)
        chatViewModel.observeTyping(chatId, otherUser.uid)
    }

    DisposableEffect(chatId) {
        onDispose { chatViewModel.setTyping(chatId, myUid, false) }
    }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        with(sharedTransitionScope) {
                            if (otherUser.photoUrl.isNotBlank()) {
                                AsyncImage(
                                    model = otherUser.photoUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .sharedElement(
                                            state = rememberSharedContentState(key = "profile-${otherUser.uid}"),
                                            animatedVisibilityScope = animatedVisibilityScope
                                        )
                                        .clickable { onOpenProfilePhoto() }
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .clickable { onOpenProfilePhoto() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text((otherUser.displayName.firstOrNull() ?: '?').toString())
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
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
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("‹") }
                }
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                val currentReply = replyingTo
                if (currentReply != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(32.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (currentReply.senderId == myUid) "أنا" else otherUser.displayName.ifBlank { otherUser.email },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = currentReply.text.ifBlank {
                                    if (currentReply.fileType == "image") "صورة" else if (currentReply.fileType.isNotBlank()) currentReply.fileName else ""
                                },
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { replyingTo = null }) {
                            Icon(Icons.Default.Close, contentDescription = "إلغاء الرد")
                        }
                    }
                }
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
                        chatViewModel.sendMessage(
                            context = context,
                            senderId = myUid,
                            receiverId = otherUser.uid,
                            text = text,
                            replyTo = replyingTo,
                            replyToSenderName = replyingTo?.let {
                                if (it.senderId == myUid) "أنا" else otherUser.displayName.ifBlank { otherUser.email }
                            } ?: ""
                        )
                        text = ""
                        replyingTo = null
                        chatViewModel.setTyping(chatId, myUid, false)
                    }) {
                        Icon(Icons.Default.Send, contentDescription = "إرسال")
                    }
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
            itemsIndexed(messages, key = { _, message -> message.id.ifBlank { message.timestamp.toString() } }) { _, message ->
                SwipeToReplyBubble(
                    message = message,
                    isMine = message.senderId == myUid,
                    onReply = { replyingTo = it },
                    onReplyPreviewClick = { replyToId ->
                        val index = messages.indexOfFirst { it.id == replyToId }
                        if (index >= 0) {
                            coroutineScope.launch { listState.animateScrollToItem(index) }
                        }
                    }
                )
            }
        }
    }
}

/** لفافة بتضيف خاصية "اسحب لليمين عشان ترد" فوق أي رسالة */
@Composable
private fun SwipeToReplyBubble(
    message: Message,
    isMine: Boolean,
    onReply: (Message) -> Unit,
    onReplyPreviewClick: (String) -> Unit
) {
    val density = LocalDensity.current
    val triggerPx = with(density) { 64.dp.toPx() }
    val maxSwipePx = with(density) { 96.dp.toPx() }
    val offsetX = remember(message.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var triggered by remember(message.id) { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        // أيقونة الرد اللي بتظهر تدريجيًا وراء الرسالة وإحنا بنسحب
        Icon(
            imageVector = Icons.Default.Reply,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
                .alpha((offsetX.value / triggerPx).coerceIn(0f, 1f))
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(message.id) {
                    detectHorizontalDragGestures(
                        onDragStart = { triggered = false },
                        onDragEnd = {
                            scope.launch {
                                if (offsetX.value >= triggerPx && !triggered) {
                                    triggered = true
                                    onReply(message)
                                }
                                offsetX.animateTo(0f, animationSpec = spring())
                            }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f, animationSpec = spring()) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val newValue = (offsetX.value + dragAmount).coerceIn(0f, maxSwipePx)
                            scope.launch { offsetX.snapTo(newValue) }
                        }
                    )
                }
        ) {
            MessageBubble(
                message = message,
                isMine = isMine,
                onReplyPreviewClick = onReplyPreviewClick
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    isMine: Boolean,
    onReplyPreviewClick: (String) -> Unit = {}
) {
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
                if (message.replyToText.isNotBlank() || message.replyToId.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                (if (isMine) Color.White else MaterialTheme.colorScheme.primary)
                                    .copy(alpha = 0.15f)
                            )
                            .clickable(enabled = message.replyToId.isNotBlank()) {
                                onReplyPreviewClick(message.replyToId)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = message.replyToSenderName.ifBlank { "رسالة" },
                            fontSize = 11.sp,
                            color = if (isMine) Color.White else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = message.replyToText,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isMine) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = message.text,
                    color = if (isMine) Color.White else Color.Black
                )
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
