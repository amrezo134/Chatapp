package com.creatix.chatapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.creatix.chatapp.data.GroupMessage
import com.creatix.chatapp.ui.theme.ChatAppBrandGradient
import com.creatix.chatapp.ui.theme.ChatAppSentBubbleGradient
import com.creatix.chatapp.viewmodel.AuthViewModel
import com.creatix.chatapp.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val myUid = authViewModel.currentUid ?: return
    val context = LocalContext.current
    val myName by chatViewModel.myDisplayName.collectAsState()
    LaunchedEffect(myUid) { chatViewModel.loadMyDisplayName(myUid) }
    val messages by chatViewModel.groupMessages.collectAsState()
    val typingNames by chatViewModel.groupTypingNames.collectAsState()
    val allUsers by chatViewModel.users.collectAsState()
    val myCustomGroups by chatViewModel.myCustomGroups.collectAsState()
    var text by remember { mutableStateOf("") }
    var editingMessage by remember { mutableStateOf<GroupMessage?>(null) }
    var forwardTarget by remember { mutableStateOf<GroupMessage?>(null) }
    var deleteTarget by remember { mutableStateOf<GroupMessage?>(null) }
    var viewingImageUrl by remember { mutableStateOf<String?>(null) }
    var viewingVideoUrl by remember { mutableStateOf<String?>(null) }
    var viewingDocument by remember { mutableStateOf<Pair<String, String>?>(null) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // ---- حالة قائمة الإرفاق وتسجيل الصوت ----
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    var uploadingCount by remember { mutableStateOf(0) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }

    // بيرفع الملف على Cloudflare وبيبعت الرسالة للجروب العام، وبيظهر رسالة خطأ لو فشل (كل ملف كرسالة مستقلة)
    fun uploadAndSend(bytes: ByteArray, fileName: String, mimeType: String, fileType: String) {
        uploadingCount++
        coroutineScope.launch {
            try {
                val url = chatViewModel.uploadFile(bytes, fileName, mimeType)
                chatViewModel.sendGroupMessage(
                    context, myUid, myName, "",
                    fileUrl = url,
                    fileName = fileName,
                    fileType = fileType
                )
            } catch (e: Exception) {
                uploadError = e.message ?: "فشل رفع الملف"
            } finally {
                uploadingCount--
            }
        }
    }

    suspend fun readAndUploadDocument(uri: Uri) {
        val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.readBytes() }
        if (bytes == null) { uploadError = "تعذر قراءة أحد الملفات"; return }
        var name = "document"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx) ?: name
        }
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        // نحدد نوع المرفق الفعلي من الـ mime بدل ما نثبته "document" دايمًا
        val fileType = when {
            mime.startsWith("audio") -> "audio"
            mime.startsWith("image") -> "image"
            mime.startsWith("video") -> "video"
            else -> "document"
        }
        uploadAndSend(bytes, name, mime, fileType)
    }

    suspend fun readAndUploadMedia(uri: Uri) {
        val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.readBytes() }
        if (bytes == null) { uploadError = "تعذر قراءة أحد الملفات"; return }
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val kind = if (mime.startsWith("video")) "video" else "image"
        uploadAndSend(bytes, "$kind.${mime.substringAfterLast('/')}", mime, kind)
    }

    // لواتشر اختيار مستندات (بيدعم اختيار أكتر من ملف مرة واحدة)
    val pickDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            uris.forEach { uri -> readAndUploadDocument(uri) }
        }
    }

    // لواتشر اختيار صور أو فيديوهات من المعرض (بيدعم اختيار أكتر من ملف مرة واحدة)
    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            uris.forEach { uri -> readAndUploadMedia(uri) }
        }
    }

    // لواتشر تسجيل الصوت (طلب الإذن)
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            val recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            audioFile = file
            isRecordingAudio = true
        }
    }

    LaunchedEffect(Unit) {
        chatViewModel.loadGroupMessages()
        chatViewModel.markGroupAsRead(myUid)
    }
    LaunchedEffect(myUid) { chatViewModel.observeGroupTyping(myUid) }

    DisposableEffect(Unit) {
        onDispose {
            chatViewModel.setGroupTyping(myUid, myName, false)
            chatViewModel.markGroupAsRead(myUid)
        }
    }

    LaunchedEffect(text, myName) {
        if (text.isNotEmpty()) {
            chatViewModel.setGroupTyping(myUid, myName, true)
            delay(2000)
            chatViewModel.setGroupTyping(myUid, myName, false)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            Box(modifier = Modifier.fillMaxWidth().background(ChatAppBrandGradient)) {
                TopAppBar(
                    title = { Text("الجروب العام", color = Color.White, fontWeight = FontWeight.SemiBold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Text("‹", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                )
            }
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
                if (uploadingCount > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "جاري رفع $uploadingCount ${if (uploadingCount == 1) "ملف" else "ملفات"}...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showAttachmentMenu = true }) {
                        Icon(Icons.Default.AttachFile, contentDescription = "إرفاق")
                    }
                    if (isRecordingAudio) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text("بيسجّل صوت...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("اكتب رسالة للجروب...") },
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent
                            )
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    val canSend = text.isNotBlank()
                    if (isRecordingAudio) {
                        // زرار إيقاف التسجيل وإرسال الصوت
                        IconButton(
                            onClick = {
                                try {
                                    mediaRecorder?.stop()
                                    mediaRecorder?.release()
                                } catch (e: Exception) { /* تجاهل لو التسجيل كان قصير جدًا */ }
                                mediaRecorder = null
                                isRecordingAudio = false
                                val file = audioFile
                                if (file != null && file.exists() && file.length() > 0) {
                                    uploadAndSend(file.readBytes(), file.name, "audio/mp4", "audio")
                                }
                                audioFile = null
                            },
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(ChatAppSentBubbleGradient)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "إيقاف وإرسال", tint = Color.White)
                        }
                    } else if (!canSend) {
                        // زرار الميكروفون لما مفيش نص متكتوب
                        IconButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
                                    val recorder = MediaRecorder().apply {
                                        setAudioSource(MediaRecorder.AudioSource.MIC)
                                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                        setOutputFile(file.absolutePath)
                                        prepare()
                                        start()
                                    }
                                    mediaRecorder = recorder
                                    audioFile = file
                                    isRecordingAudio = true
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(
                                    listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
                                ))
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "تسجيل صوت", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        IconButton(
                            onClick = {
                                val currentlyEditing = editingMessage
                                if (currentlyEditing != null) {
                                    chatViewModel.editGroupMessage(currentlyEditing.id, text)
                                    editingMessage = null
                                    text = ""
                                } else {
                                    chatViewModel.sendGroupMessage(context, myUid, myName, text)
                                    text = ""
                                }
                                chatViewModel.setGroupTyping(myUid, myName, false)
                            },
                            enabled = canSend,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(
                                    if (canSend) ChatAppSentBubbleGradient
                                    else Brush.linearGradient(
                                        listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                )
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "إرسال",
                                tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages, key = { it.id.ifBlank { it.timestamp.toString() } }) { message ->
                    GroupMessageBubble(
                        message = message,
                        isMine = message.senderId == myUid,
                        myUid = myUid,
                        onCopy = { clipboardManager.setText(AnnotatedString(message.text)) },
                        onForward = { forwardTarget = message },
                        onEdit = {
                            editingMessage = message
                            text = message.text
                        },
                        onDelete = { deleteTarget = message },
                        onReact = { emoji -> chatViewModel.toggleGroupReaction(message, myUid, emoji) },
                        onOpenImage = { url -> viewingImageUrl = url },
                        onOpenVideo = { url -> viewingVideoUrl = url },
                        onOpenDocument = { url, name -> viewingDocument = url to name }
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
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 4.dp)
                )
            }
        }
    }

    val currentForward = forwardTarget
    if (currentForward != null) {
        ForwardDialog(
            users = allUsers,
            groups = myCustomGroups,
            onDismiss = { forwardTarget = null },
            onForwardToGlobalGroup = {
                chatViewModel.sendGroupMessage(context, myUid, myName, currentForward.text, forwarded = true)
                forwardTarget = null
            },
            onForwardToGroup = { group ->
                chatViewModel.sendCustomGroupMessage(context, group.id, myUid, myName, currentForward.text, forwarded = true)
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
            onConfirm = { chatViewModel.deleteGroupMessage(currentDelete.id) }
        )
    }

    // ---- قائمة الإرفاق ----
    if (showAttachmentMenu) {
        AttachmentMenu(
            onDismiss = { showAttachmentMenu = false },
            onSelect = { type ->
                when (type) {
                    AttachmentType.DOCUMENT -> pickDocumentLauncher.launch("*/*")
                    AttachmentType.MEDIA -> pickMediaLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageAndVideo
                        )
                    )
                    AttachmentType.AUDIO -> {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
                            val recorder = MediaRecorder().apply {
                                setAudioSource(MediaRecorder.AudioSource.MIC)
                                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                setOutputFile(file.absolutePath)
                                prepare()
                                start()
                            }
                            mediaRecorder = recorder
                            audioFile = file
                            isRecordingAudio = true
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                    else -> { /* مش مدعوم هنا (كاميرا/جهة اتصال/استطلاع/مناسبة/ملصق) */ }
                }
            }
        )
    }

    val currentUploadError = uploadError
    if (currentUploadError != null) {
        AlertDialog(
            onDismissRequest = { uploadError = null },
            confirmButton = { TextButton(onClick = { uploadError = null }) { Text("حسنًا") } },
            title = { Text("حصل خطأ") },
            text = { Text(currentUploadError) }
        )
    }

    val currentViewingImage = viewingImageUrl
    if (currentViewingImage != null) {
        ImageViewerDialog(url = currentViewingImage, onDismiss = { viewingImageUrl = null })
    }
    val currentViewingVideo = viewingVideoUrl
    if (currentViewingVideo != null) {
        VideoPlayerDialog(url = currentViewingVideo, onDismiss = { viewingVideoUrl = null })
    }
    val currentViewingDocument = viewingDocument
    if (currentViewingDocument != null) {
        DocumentViewerDialog(
            url = currentViewingDocument.first,
            fileName = currentViewingDocument.second,
            onDismiss = { viewingDocument = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupMessageBubble(
    message: GroupMessage,
    isMine: Boolean,
    myUid: String = "",
    onCopy: () -> Unit = {},
    onForward: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onReact: (String) -> Unit = {},
    onOpenImage: (String) -> Unit = {},
    onOpenVideo: (String) -> Unit = {},
    onOpenDocument: (String, String) -> Unit = { _, _ -> }
) {
    var menuExpanded by remember(message.id) { mutableStateOf(false) }

    val bubbleShape = if (isMine) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .shadow(if (isMine) 3.dp else 1.dp, bubbleShape, clip = false)
                .clip(bubbleShape)
                .background(
                    if (isMine) ChatAppSentBubbleGradient
                    else Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
                    )
                )
                .combinedClickable(
                    enabled = !message.deleted,
                    onClick = {
                        when (message.fileType) {
                            "image", "sticker" -> if (message.fileUrl.isNotBlank()) onOpenImage(message.fileUrl)
                            "video" -> if (message.fileUrl.isNotBlank()) onOpenVideo(message.fileUrl)
                            "document" -> if (message.fileUrl.isNotBlank()) onOpenDocument(message.fileUrl, message.fileName)
                        }
                    },
                    onLongClick = { if (!message.deleted) menuExpanded = true }
                )
                .padding(horizontal = 14.dp, vertical = 9.dp)
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
                onDelete = onDelete,
                onReact = onReact
            )
            if (message.deleted) {
                Text(
                    text = "🚫 تم حذف هذه الرسالة",
                    fontStyle = FontStyle.Italic,
                    fontSize = 13.sp,
                    color = if (isMine) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Box
            }
            Column {
                if (!isMine) {
                    Text(
                        message.senderName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (message.forwarded) {
                    Text(
                        text = "↪ تم التوجيه",
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                        color = if (isMine) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (message.fileUrl.isNotBlank()) {
                    when (message.fileType) {
                        "image", "sticker" -> {
                            AsyncImage(
                                model = message.fileUrl,
                                contentDescription = message.fileName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .widthIn(max = 220.dp)
                                    .heightIn(max = 220.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            if (message.fileType == "image") Spacer(Modifier.height(4.dp))
                        }
                        "video" -> {
                            Text(
                                text = "🎬 ${message.fileName.ifBlank { "فيديو" }} (اضغط للتشغيل)",
                                color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        "audio" -> {
                            AudioPlayerBubble(url = message.fileUrl, isMine = isMine, fileName = message.fileName)
                        }
                        "document" -> {
                            Text(
                                text = "📄 ${message.fileName.ifBlank { "مستند" }} (اضغط للفتح)",
                                color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (message.edited) {
                    Text(
                        text = "(معدلة)",
                        fontSize = 10.sp,
                        fontStyle = FontStyle.Italic,
                        color = if (isMine) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (message.reactions.isNotEmpty()) {
            ReactionsChipsRow(
                reactions = message.reactions,
                myUid = myUid,
                onToggle = { emoji -> onReact(emoji) },
                modifier = Modifier
                    .offset(y = (-6).dp)
                    .shadow(1.dp, RoundedCornerShape(12.dp))
            )
        }
    }
}

