package com.creatix.chatapp.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.creatix.chatapp.data.backup.DriveBackupManager
import com.creatix.chatapp.repository.ChatRepository
import com.creatix.chatapp.ui.theme.ChatAppBrandGradient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.creatix.chatapp.viewmodel.AuthViewModel
import com.creatix.chatapp.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * صفحة "الملف الشخصي": بتتفتح من زرار التلات نقط في قائمة المحادثات.
 * بتعرض صورة وبيانات المستخدم الحالي، وتحتها زرار تسجيل الخروج.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    onOpenMyPhoto: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val myUid = authViewModel.currentUid ?: return
    val myProfile by chatViewModel.myProfile.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isUploadingPhoto by remember { mutableStateOf(false) }

    LaunchedEffect(myUid) { chatViewModel.loadMyProfile(myUid) }

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        isUploadingPhoto = true
        coroutineScope.launch {
            val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.readBytes() }
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            if (bytes != null) {
                chatViewModel.updateMyPhoto(context, myUid, bytes, "profile.${mime.substringAfterLast('/')}", mime)
            }
            isUploadingPhoto = false
        }
    }

    val displayName = myProfile?.displayName?.ifBlank { null }
        ?: authViewModel.currentDisplayName?.ifBlank { null }
        ?: "أنا"
    val photoUrl = myProfile?.photoUrl.orEmpty()

    Scaffold(
        topBar = {
            Box(modifier = Modifier.fillMaxWidth().background(ChatAppBrandGradient)) {
                TopAppBar(
                    title = { Text("الملف الشخصي", color = Color.White, fontWeight = FontWeight.SemiBold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Text("‹", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            with(sharedTransitionScope) {
                Box {
                    if (photoUrl.isNotBlank()) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(128.dp)
                                .shadow(8.dp, CircleShape)
                                .clip(CircleShape)
                                .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .sharedElement(
                                    state = rememberSharedContentState(key = "profile-$myUid"),
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                                .clickable { onOpenMyPhoto() }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(128.dp)
                                .shadow(8.dp, CircleShape)
                                .clip(CircleShape)
                                .background(ChatAppBrandGradient)
                                .sharedElement(
                                    state = rememberSharedContentState(key = "profile-$myUid"),
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                                .clickable {
                                    pickPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                (displayName.firstOrNull() ?: '?').toString(),
                                fontSize = 44.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(38.dp)
                            .shadow(4.dp, CircleShape)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable {
                                pickPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isUploadingPhoto) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Default.CameraAlt, contentDescription = "غيّر صورة البروفايل", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = displayName,
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(Modifier.height(24.dp))

            // ---- بطاقة النسخ الاحتياطي (Google Drive) ----
            var backupEnabled by remember { mutableStateOf(false) }
            var backupBusy by remember { mutableStateOf(false) }
            var lastBackupAt by remember { mutableStateOf(DriveBackupManager.lastBackupAt(context)) }
            var backupMessage by remember { mutableStateOf<String?>(null) }
            val chatRepository = remember { ChatRepository() }

            LaunchedEffect(myUid) { backupEnabled = chatRepository.isBackupEnabled(myUid) }

            val driveSignInLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                val account = try {
                    GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
                } catch (e: ApiException) {
                    e.printStackTrace()
                    null
                }
                if (account != null) {
                    coroutineScope.launch {
                        try { chatRepository.setBackupEnabled(myUid, true) } catch (e: Exception) { e.printStackTrace() }
                        backupEnabled = true
                        backupBusy = true
                        backupMessage = try {
                            val n = DriveBackupManager.backupNow(context, myUid)
                            lastBackupAt = DriveBackupManager.lastBackupAt(context)
                            "اتعملت نسخة من $n رسالة"
                        } catch (e: Exception) {
                            "فشل النسخ: ${e.message}"
                        }
                        backupBusy = false
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "النسخ الاحتياطي (Google Drive)",
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = backupEnabled, onCheckedChange = { want ->
                            if (want && DriveBackupManager.signedInAccount(context) == null) {
                                val client = GoogleSignIn.getClient(context, DriveBackupManager.signInOptions())
                                driveSignInLauncher.launch(client.signInIntent)
                            } else {
                                backupEnabled = want
                                coroutineScope.launch {
                                    try { chatRepository.setBackupEnabled(myUid, want) } catch (e: Exception) { e.printStackTrace() }
                                }
                            }
                        })
                    }
                    Text(
                        "نسخة من رسايلك في مجلد مخفي في حسابك على Google Drive، بترجعها لو التليفون ضاع أو اتفرمت.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (lastBackupAt > 0) {
                        Text(
                            "آخر نسخة: " + android.text.format.DateFormat.format("yyyy-MM-dd hh:mm a", lastBackupAt),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    backupMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(4.dp))
                    Row {
                        TextButton(
                            enabled = !backupBusy && backupEnabled,
                            onClick = {
                                coroutineScope.launch {
                                    backupBusy = true
                                    backupMessage = try {
                                        val n = DriveBackupManager.backupNow(context, myUid)
                                        lastBackupAt = DriveBackupManager.lastBackupAt(context)
                                        "اتعملت نسخة من $n رسالة"
                                    } catch (e: Exception) {
                                        "فشل النسخ: ${e.message}"
                                    }
                                    backupBusy = false
                                }
                            }
                        ) { Text(if (backupBusy) "جاري النسخ..." else "انسخ الآن") }
                        TextButton(
                            enabled = !backupBusy && DriveBackupManager.signedInAccount(context) != null,
                            onClick = {
                                coroutineScope.launch {
                                    backupBusy = true
                                    backupMessage = try {
                                        val n = DriveBackupManager.restoreNow(context)
                                        if (n == null) "مفيش نسخة احتياطية على Drive" else "اتسترجعت $n رسالة"
                                    } catch (e: Exception) {
                                        "فشل الاسترجاع: ${e.message}"
                                    }
                                    backupBusy = false
                                }
                            }
                        ) { Text("استرجاع") }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    authViewModel.logout()
                    onLoggedOut()
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("تسجيل الخروج", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

