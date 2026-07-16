package com.creatix.chatapp.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.creatix.chatapp.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * شاشة عرض صورة البروفايل بالحجم الكامل - نفس فكرة واتساب:
 * تدوس على الصورة الصغيرة، تتحرك وتتكبر بسلاسة لحد ما توصل هنا (Shared Element).
 * تدوس على أي مكان في الشاشة -> ترجع تصغر تاني لمكانها الأصلي.
 *
 * لو دي صورة بروفايلي أنا (isOwnProfile = true)، بتظهر أيقونة 3 نقط فوق (زي ميتا/انستجرام)
 * فيها خيارين: تغيير الصورة، وتعديل البايو.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ProfilePhotoScreen(
    photoUrl: String,
    displayName: String,
    sharedKey: String,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    isOwnProfile: Boolean = false,
    currentBio: String = "",
    myUid: String? = null,
    chatViewModel: ChatViewModel? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    var showBioEditor by remember { mutableStateOf(false) }
    var isUploadingPhoto by remember { mutableStateOf(false) }

    val canEdit = isOwnProfile && myUid != null && chatViewModel != null

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        if (myUid == null || chatViewModel == null) return@rememberLauncherForActivityResult
        isUploadingPhoto = true
        coroutineScope.launch {
            val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.readBytes() }
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            if (bytes != null) {
                chatViewModel.updateMyPhoto(myUid, bytes, "profile.${mime.substringAfterLast('/')}", mime)
            }
            isUploadingPhoto = false
        }
    }

    with(sharedTransitionScope) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onBack() }
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .sharedElement(
                            state = rememberSharedContentState(key = sharedKey),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                )
                if (isUploadingPhoto) {
                    Box(modifier = Modifier.fillMaxSize().align(Alignment.Center), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }

            Text(
                text = displayName,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            )

            if (canEdit) {
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 4.dp)) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "خيارات", tint = Color.White)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("غيّر الصورة") },
                            leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                pickPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("عدّل البايو") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                showBioEditor = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (canEdit && showBioEditor) {
        var bio by remember { mutableStateOf(currentBio) }
        AlertDialog(
            onDismissRequest = { showBioEditor = false },
            title = { Text("البايو") },
            text = {
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    placeholder = { Text("اكتب نبذة عن نفسك...") },
                    shape = RoundedCornerShape(12.dp),
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    chatViewModel?.updateMyBio(myUid!!, bio)
                    showBioEditor = false
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showBioEditor = false }) { Text("إلغاء") } }
        )
    }
}
