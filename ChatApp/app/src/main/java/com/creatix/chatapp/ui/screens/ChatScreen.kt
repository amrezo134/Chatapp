package com.creatix.chatapp.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaRecorder
import android.net.Uri
import android.provider.ContactsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.creatix.chatapp.data.ChatUser
import com.creatix.chatapp.data.Message
import com.creatix.chatapp.data.chatIdFor
import com.creatix.chatapp.ui.theme.ChatAppBrandGradient
import com.creatix.chatapp.ui.theme.ChatAppSentBubbleGradient
import com.creatix.chatapp.viewmodel.AuthViewModel
import com.creatix.chatapp.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Calendar
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
    val allUsers by chatViewModel.users.collectAsState()
    val myCustomGroups by chatViewModel.myCustomGroups.collectAsState()
    val myDisplayName by chatViewModel.myDisplayName.collectAsState()
    var text by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<Message?>(null) }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var forwardTarget by remember { mutableStateOf<Message?>(null) }
    var deleteTarget by remember { mutableStateOf<Message?>(null) }
    var viewingImageUrl by remember { mutableStateOf<String?>(null) }
    var viewingVideoUrl by remember { mutableStateOf<String?>(null) }
    var viewingDocument by remember { mutableStateOf<Pair<String, String>?>(null) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // ---- حالة قائمة الإرفاق والحوارات المرتبطة بيها ----
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showPollDialog by remember { mutableStateOf(false) }
    var showEventDialog by remember { mutableStateOf(false) }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    var uploadingCount by remember { mutableStateOf(0) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }

    // بيرفع الملف على Cloudflare وبيبعت الرسالة، وبيظهر رسالة خطأ لو فشل (كل ملف بيتبعت كرسالة مستقلة)
    fun uploadAndSend(bytes: ByteArray, fileName: String, mimeType: String, fileType: String) {
        uploadingCount++
        coroutineScope.launch {
            try {
                val url = chatViewModel.uploadFile(bytes, fileName, mimeType)
                chatViewModel.sendMessage(
                    context = context,
                    senderId = myUid,
                    receiverId = otherUser.uid,
                    text = "",
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

    // بيقرأ يوري واحد ويبعته كمرفق (بيتستخدم مع كل ملف في حالة الاختيار المتعدد)
    suspend fun readAndUploadDocument(uri: Uri) {
        val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.readBytes() }
        if (bytes == null) { uploadError = "تعذر قراءة أحد الملفات"; return }
        var name = "document"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx) ?: name
        }
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        uploadAndSend(bytes, name, mime, "document")
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

    // لواتشر اختيار ملصق (صورة PNG)
    val pickStickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
            if (bytes == null) { uploadError = "تعذر قراءة الملصق"; return@launch }
            uploadAndSend(bytes, "sticker.png", "image/png", "sticker")
        }
    }

    // لواتشر الكاميرا (بيرجع Bitmap مباشرة، مش محتاج FileProvider)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap ?: return@rememberLauncherForActivityResult
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        uploadAndSend(stream.toByteArray(), "camera.jpg", "image/jpeg", "image")
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) cameraLauncher.launch(null) }

    // لواتشر تسجيل الصوت
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

    // لواتشر اختيار جهة اتصال
    var pickContactLauncherRef by remember { mutableStateOf<androidx.activity.result.ActivityResultLauncher<Void?>?>(null) }
    val contactPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) pickContactLauncherRef?.launch(null) }
    val pickContactLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val hasPhoneIdx = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                val contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID))
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) else "جهة اتصال"
                var phone = ""
                if (hasPhoneIdx >= 0 && cursor.getInt(hasPhoneIdx) > 0) {
                    context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(contactId), null
                    )?.use { phoneCursor ->
                        if (phoneCursor.moveToFirst()) {
                            val numIdx = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            if (numIdx >= 0) phone = phoneCursor.getString(numIdx) ?: ""
                        }
                    }
                }
                chatViewModel.sendContactMessage(context, myUid, otherUid = otherUser.uid, contactName = name, contactPhone = phone)
            }
        }
    }
    pickContactLauncherRef = pickContactLauncher

    LaunchedEffect(chatId) {
        chatViewModel.loadMessages(chatId, myUid, otherUser.uid)
        chatViewModel.observeTyping(chatId, otherUser.uid)
    }

    LaunchedEffect(myUid) { chatViewModel.loadMyDisplayName(myUid) }

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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ChatAppBrandGradient)
            ) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            with(sharedTransitionScope) {
                                if (otherUser.photoUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = otherUser.photoUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(38.dp)
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
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.2f))
                                            .clickable { onOpenProfilePhoto() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            (otherUser.displayName.firstOrNull() ?: '?').toString(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    otherUser.displayName.ifBlank { otherUser.email },
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (otherUserTyping) {
                                    Text(
                                        "بيكتب دلوقتي...",
                                        fontSize = 12.sp,
                                        fontStyle = FontStyle.Italic,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                } else if (otherUser.bio.isNotBlank()) {
                                    Text(
                                        otherUser.bio,
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.75f)
                                    )
                                }
                            }
                        }
                    },
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
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(32.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "تعديل الرسالة",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = currentEdit.text,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            editingMessage = null
                            text = ""
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "إلغاء التعديل")
                        }
                    }
                }
                val currentReply = replyingTo
                if (currentReply != null && currentEdit == null) {
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
                            placeholder = { Text("اكتب رسالة...") },
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
                                chatViewModel.editMessage(chatId, currentlyEditing.id, text)
                                editingMessage = null
                                text = ""
                            } else {
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
                            }
                            chatViewModel.setTyping(chatId, myUid, false)
                        },
                        enabled = canSend,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (canSend) ChatAppSentBubbleGradient else Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surfaceVariant
                                )
                            ))
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
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 10.dp),
            contentPadding = PaddingValues(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(messages, key = { _, message -> message.id.ifBlank { message.timestamp.toString() } }) { _, message ->
                var shown by remember(message.id) { mutableStateOf(false) }
                LaunchedEffect(message.id) { shown = true }
                AnimatedVisibility(
                    visible = shown,
                    enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 5 }
                ) {
                    SwipeToReplyBubble(
                        message = message,
                        isMine = message.senderId == myUid,
                        myUid = myUid,
                        onReply = { replyingTo = it; editingMessage = null },
                        onReplyPreviewClick = { replyToId ->
                            val replyIndex = messages.indexOfFirst { it.id == replyToId }
                            if (replyIndex >= 0) {
                                coroutineScope.launch { listState.animateScrollToItem(replyIndex) }
                            }
                        },
                        onCopy = { clipboardManager.setText(AnnotatedString(message.text)) },
                        onForward = { forwardTarget = message },
                        onEdit = {
                            editingMessage = message
                            replyingTo = null
                            text = message.text
                        },
                        onDelete = { deleteTarget = message },
                        onReact = { emoji -> chatViewModel.toggleReaction(chatId, message, myUid, emoji) },
                        onOpenImage = { url -> viewingImageUrl = url },
                        onOpenVideo = { url -> viewingVideoUrl = url },
                        onOpenDocument = { url, name -> viewingDocument = url to name }
                    )
                }
            }
        }
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

    val currentForward = forwardTarget
    if (currentForward != null) {
        ForwardDialog(
            users = allUsers,
            groups = myCustomGroups,
            onDismiss = { forwardTarget = null },
            onForwardToGlobalGroup = {
                chatViewModel.sendGroupMessage(context, myUid, myDisplayName, currentForward.text, forwarded = true)
                forwardTarget = null
            },
            onForwardToGroup = { group ->
                chatViewModel.sendCustomGroupMessage(context, group.id, myUid, myDisplayName, currentForward.text, forwarded = true)
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
            onConfirm = { chatViewModel.deleteMessage(chatId, currentDelete.id) }
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
                    AttachmentType.CAMERA -> {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            cameraLauncher.launch(null)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
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
                    AttachmentType.CONTACT -> {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                            pickContactLauncher.launch(null)
                        } else {
                            contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        }
                    }
                    AttachmentType.POLL -> showPollDialog = true
                    AttachmentType.EVENT -> showEventDialog = true
                    AttachmentType.STICKER -> pickStickerLauncher.launch("image/png")
                }
            }
        )
    }

    if (showPollDialog) {
        PollCreationDialog(
            onDismiss = { showPollDialog = false },
            onCreate = { question, options ->
                chatViewModel.sendPollMessage(context, myUid, otherUser.uid, question, options)
                showPollDialog = false
            }
        )
    }

    if (showEventDialog) {
        val calendar = remember { Calendar.getInstance() }
        LaunchedEffect(showEventDialog) {
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            calendar.set(Calendar.HOUR_OF_DAY, hour)
                            calendar.set(Calendar.MINUTE, minute)
                            chatViewModel.sendEventMessage(
                                context, myUid, otherUser.uid,
                                eventTitle = "مناسبة",
                                eventTimestamp = calendar.timeInMillis
                            )
                            showEventDialog = false
                        },
                        calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true
                    ).apply { setOnCancelListener { showEventDialog = false } }.show()
                },
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
            ).apply { setOnCancelListener { showEventDialog = false } }.show()
        }
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
}

/** حوار بسيط لإنشاء استطلاع رأي: سؤال + 2 لحد 4 خيارات */
@Composable
private fun PollCreationDialog(
    onDismiss: () -> Unit,
    onCreate: (question: String, options: List<String>) -> Unit
) {
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("استطلاع رأي") },
        text = {
            Column {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("السؤال") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                options.forEachIndexed { index, opt ->
                    OutlinedTextField(
                        value = opt,
                        onValueChange = { options[index] = it },
                        label = { Text("خيار ${index + 1}") },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
                if (options.size < 4) {
                    TextButton(onClick = { options.add("") }) { Text("+ إضافة خيار") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cleanOptions = options.map { it.trim() }.filter { it.isNotBlank() }
                    if (question.isNotBlank() && cleanOptions.size >= 2) {
                        onCreate(question.trim(), cleanOptions)
                    }
                }
            ) { Text("إنشاء") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

/** لفافة بتضيف خاصية "اسحب لليمين عشان ترد" فوق أي رسالة */
@Composable
private fun SwipeToReplyBubble(
    message: Message,
    isMine: Boolean,
    myUid: String,
    onReply: (Message) -> Unit,
    onReplyPreviewClick: (String) -> Unit,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReact: (String) -> Unit,
    onOpenImage: (String) -> Unit = {},
    onOpenVideo: (String) -> Unit = {},
    onOpenDocument: (String, String) -> Unit = { _, _ -> }
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
                myUid = myUid,
                onReplyPreviewClick = onReplyPreviewClick,
                onCopy = onCopy,
                onForward = onForward,
                onEdit = onEdit,
                onDelete = onDelete,
                onReact = onReact,
                onOpenImage = onOpenImage,
                onOpenVideo = onOpenVideo,
                onOpenDocument = onOpenDocument
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    isMine: Boolean,
    myUid: String = "",
    onReplyPreviewClick: (String) -> Unit = {},
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
                if (message.forwarded) {
                    Text(
                        text = "↪ تم التوجيه",
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                        color = if (isMine) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                            color = if (isMine) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
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
                if (message.contactName.isNotBlank()) {
                    Text(
                        text = "👤 ${message.contactName}${if (message.contactPhone.isNotBlank()) " - ${message.contactPhone}" else ""}",
                        color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (message.pollQuestion.isNotBlank()) {
                    Column {
                        Text(
                            text = "📊 ${message.pollQuestion}",
                            fontWeight = FontWeight.SemiBold,
                            color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        message.pollOptions.forEach { opt ->
                            Text(
                                text = "• $opt",
                                fontSize = 13.sp,
                                color = if (isMine) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (message.eventTitle.isNotBlank()) {
                    Text(
                        text = "📅 ${message.eventTitle}",
                        color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
                    )
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
                if (isMine) {
                    Text(
                        text = if (message.seen) "✓✓ تمت القراءة" else "✓ اتبعتت",
                        fontSize = 10.sp,
                        color = if (message.seen) com.creatix.chatapp.ui.theme.SeenBlue else Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.align(Alignment.End)
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
