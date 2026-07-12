package com.creatix.chatapp.ui.screens

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
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.creatix.chatapp.ui.theme.ChatAppBrandGradient
import com.creatix.chatapp.viewmodel.AuthViewModel
import com.creatix.chatapp.viewmodel.ChatViewModel

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

    LaunchedEffect(myUid) { chatViewModel.loadMyProfile(myUid) }

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
                            .clickable { onOpenMyPhoto() },
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
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = displayName,
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(Modifier.height(40.dp))

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

