package com.creatix.chatapp.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.creatix.chatapp.data.ChatUser
import com.creatix.chatapp.viewmodel.AuthViewModel
import com.creatix.chatapp.viewmodel.ChatViewModel
import androidx.compose.material.icons.filled.Groups

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ChatListScreen(
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    onOpenChat: (ChatUser) -> Unit,
    onOpenGroupChat: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenProfilePhoto: (ChatUser) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val users by chatViewModel.users.collectAsState()
    val presenceMap by chatViewModel.presenceMap.collectAsState()
    val unreadCounts by chatViewModel.unreadCounts.collectAsState()
    val typingUsers by chatViewModel.typingUsers.collectAsState()

    LaunchedEffect(Unit) {
        authViewModel.currentUid?.let { chatViewModel.loadUsers(it) }
        chatViewModel.observePresence()
    }

    // لما قائمة اليوزرز توصل (أو تتحدث)، ابدأ راقب لكل واحد فيهم: رسايله الغير مقروءة، وهل بيكتب دلوقتي
    LaunchedEffect(users) {
        val myUid = authViewModel.currentUid ?: return@LaunchedEffect
        if (users.isNotEmpty()) chatViewModel.observeChatListExtras(myUid, users)
    }

    Scaffold(
        floatingActionButtonPosition = FabPosition.Start,
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenGroupChat) {
                Icon(Icons.Default.Groups, contentDescription = "الجروب العام")
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("المحادثات") },
                actions = {
                    IconButton(onClick = onOpenProfile) {
                        Icon(Icons.Default.MoreVert, contentDescription = "المزيد")
                    }
                }
            )
        }
    ) { padding ->
        if (users.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("مفيش مستخدمين تانيين لسه")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(users) { user ->
                    ListItem(
                        leadingContent = {
                            with(sharedTransitionScope) {
                                if (user.photoUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = user.photoUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .sharedElement(
                                                state = rememberSharedContentState(key = "profile-${user.uid}"),
                                                animatedVisibilityScope = animatedVisibilityScope
                                            )
                                            .clickable { onOpenProfilePhoto(user) }
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text((user.displayName.firstOrNull() ?: '?').toString())
                                    }
                                }
                            }
                        },
                        headlineContent = { Text(user.displayName.ifBlank { user.email }) },
                        supportingContent = {
                            Column {
                                if (user.bio.isNotBlank()) Text(user.bio, maxLines = 1)
                                val isTyping = typingUsers[user.uid] == true
                                if (isTyping) {
                                    Text(
                                        "بيكتب الآن...",
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    val isOnline = presenceMap[user.uid] == true
                                    Text(if (isOnline) "متصل الآن" else "غير متصل")
                                }
                            }
                        },
                        trailingContent = {
                            val unread = unreadCounts[user.uid] ?: 0
                            // الدائرة بيظهر بس لو فيه رسائل جديدة فعلاً، مش هتظهر أبدًا وهي صفر
                            if (unread > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (unread > 99) "99+" else unread.toString(),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        },
                        modifier = Modifier.clickable { onOpenChat(user) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
