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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.creatix.chatapp.data.ChatUser
import com.creatix.chatapp.data.ChatGroup
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
    onOpenCustomGroup: (ChatGroup) -> Unit,
    onOpenCreateGroup: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenProfilePhoto: (ChatUser) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val users by chatViewModel.filteredUsers.collectAsState()
    val searchQuery by chatViewModel.searchQuery.collectAsState()
    val presenceMap by chatViewModel.presenceMap.collectAsState()
    val unreadCounts by chatViewModel.unreadCounts.collectAsState()
    val typingUsers by chatViewModel.typingUsers.collectAsState()
    val groupUnreadCount by chatViewModel.groupUnreadCount.collectAsState()
    val myCustomGroups by chatViewModel.myCustomGroups.collectAsState()
    val customGroupUnreadCounts by chatViewModel.customGroupUnreadCounts.collectAsState()

    LaunchedEffect(Unit) {
        authViewModel.currentUid?.let { chatViewModel.loadUsers(it) }
        chatViewModel.observePresence()
        authViewModel.currentUid?.let {
            chatViewModel.observeGroupUnreadCount(it)
            chatViewModel.observeMyCustomGroups(it)
        }
    }

    // لما قائمة اليوزرز توصل (أو تتحدث)، ابدأ راقب لكل واحد فيهم: رسايله الغير مقروءة، وهل بيكتب دلوقتي
    LaunchedEffect(users) {
        val myUid = authViewModel.currentUid ?: return@LaunchedEffect
        if (users.isNotEmpty()) chatViewModel.observeChatListExtras(myUid, users)
    }

    // لما قائمة جروباتي المخصصة توصل (أو تتحدث)، ابدأ راقب عدد الرسائل الغير مقروءة لكل واحد فيها
    LaunchedEffect(myCustomGroups) {
        val myUid = authViewModel.currentUid ?: return@LaunchedEffect
        if (myCustomGroups.isNotEmpty()) chatViewModel.observeCustomGroupListExtras(myUid, myCustomGroups)
    }

    Scaffold(
        floatingActionButtonPosition = FabPosition.Start,
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = onOpenGroupChat) {
                    Icon(Icons.Default.Groups, contentDescription = "الجروب العام")
                }
                if (groupUnreadCount > 0) {
                    Badge(
                        modifier = Modifier.align(Alignment.TopStart),
                        containerColor = MaterialTheme.colorScheme.error
                    ) {
                        Text(if (groupUnreadCount > 99) "99+" else groupUnreadCount.toString())
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("المحادثات") },
                actions = {
                    IconButton(onClick = onOpenCreateGroup) {
                        Icon(Icons.Default.GroupAdd, contentDescription = "إنشاء جروب جديد")
                    }
                    IconButton(onClick = onOpenProfile) {
                        Icon(Icons.Default.MoreVert, contentDescription = "المزيد")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { chatViewModel.onSearchQueryChange(it) },
                placeholder = { Text("دور على مستخدم بالاسم...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { chatViewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "مسح البحث")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                if (myCustomGroups.isNotEmpty() && searchQuery.isBlank()) {
                    item {
                        Text(
                            "الجروبات بتاعتي",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(myCustomGroups, key = { "group_${it.id}" }) { group ->
                        CustomGroupRow(
                            group = group,
                            unreadCount = customGroupUnreadCounts[group.id] ?: 0,
                            onClick = { onOpenCustomGroup(group) }
                        )
                        HorizontalDivider()
                    }
                    item {
                        Text(
                            "المحادثات",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                if (users.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (searchQuery.isBlank()) "مفيش مستخدمين تانيين لسه" else "مفيش نتايج بالاسم ده")
                        }
                    }
                }

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

@Composable
private fun CustomGroupRow(group: ChatGroup, unreadCount: Int, onClick: () -> Unit) {
    ListItem(
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Groups, contentDescription = null)
            }
        },
        headlineContent = { Text(group.name) },
        supportingContent = {
            Text(
                group.lastMessage.ifBlank { "مفيش رسائل لسه" },
                maxLines = 1
            )
        },
        trailingContent = {
            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
