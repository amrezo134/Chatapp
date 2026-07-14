package com.creatix.chatapp.ui.screens

import android.media.MediaPlayer
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * فيوور صور بملء الشاشة مع إمكانية التكبير/التصغير والسحب (pinch to zoom).
 */
@Composable
fun ImageViewerDialog(url: String, onDismiss: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
        ) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
            }
        }
    }
}

/**
 * مشغل فيديو بملء الشاشة باستخدام ExoPlayer (Media3).
 */
@Composable
fun VideoPlayerDialog(url: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
            }
        }
    }
}

/**
 * عرض المستندات (PDF / Word / Excel / PowerPoint...) جوه التطبيق عن طريق Google Docs Viewer
 * (WebView بيحمل رابط عرض عن بُعد للملف، من غير ما نحتاج مكتبات معالجة ملفات تقيلة).
 */
@Composable
fun DocumentViewerDialog(url: String, fileName: String, onDismiss: () -> Unit) {
    var isLoading by remember { mutableStateOf(true) }
    val viewerUrl = "https://docs.google.com/viewer?url=${Uri.encode(url)}&embedded=true"

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E1E))
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                    }
                    Text(
                        fileName.ifBlank { "مستند" },
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isLoading = false
                                    }
                                }
                                loadUrl(viewerUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }
}

/**
 * مشغل صوت مضمّن جوه فقاعة الرسالة (تشغيل/إيقاف + شريط تقدّم + الوقت).
 */
@Composable
fun AudioPlayerBubble(url: String, isMine: Boolean) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var currentPos by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0) }

    DisposableEffect(url) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPos = mediaPlayer?.currentPosition ?: 0
            delay(200)
        }
    }

    fun formatMs(ms: Int): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format("%d:%02d", m, s)
    }

    val tint = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(min = 160.dp)) {
        IconButton(
            onClick = {
                val mp = mediaPlayer
                if (mp == null) {
                    val newMp = MediaPlayer().apply {
                        setDataSource(context, Uri.parse(url))
                        setOnPreparedListener {
                            isPrepared = true
                            duration = it.duration
                            it.start()
                            isPlaying = true
                        }
                        setOnCompletionListener {
                            isPlaying = false
                            currentPos = 0
                            it.seekTo(0)
                        }
                        prepareAsync()
                    }
                    mediaPlayer = newMp
                } else if (isPrepared) {
                    if (mp.isPlaying) {
                        mp.pause()
                        isPlaying = false
                    } else {
                        mp.start()
                        isPlaying = true
                    }
                }
            },
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.15f))
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "إيقاف" else "تشغيل",
                tint = tint
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = if (duration > 0) currentPos.toFloat() / duration.toFloat() else 0f,
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = tint,
                trackColor = tint.copy(alpha = 0.25f)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (isPrepared) "${formatMs(currentPos)} / ${formatMs(duration)}" else "🎤 مقطع صوتي",
                fontSize = 11.sp,
                color = tint.copy(alpha = 0.85f)
            )
        }
    }
}
