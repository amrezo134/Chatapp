package com.creatix.chatapp.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.creatix.chatapp.data.ChatGroup
import com.creatix.chatapp.data.ChatUser
import com.creatix.chatapp.ui.theme.ChatAppBrandGradient
import com.creatix.chatapp.ui.theme.OnlineGreen
import com.creatix.chatapp.viewmodel.AuthViewModel
import com.creatix.chatapp.viewmodel.ChatViewModel

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
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButtonPosition = FabPosition.Start,
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = onOpenGroupChat,
                    containerColor = Color.Transparent,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp),
                    modifier = Modifier
                        .shadow(10.dp, CircleShape)
                        .background(ChatAppBrandGradient, CircleShape)
                ) {
                    Icon(Icons.Default.Groups, contentDescription = "الجروب العام", tint = Color.White)
                }
                if (groupUnreadCount > 0) {
                    Badge(
                        modifier = Modifier.align(Alignment.TopEnd),
                        containerColor = MaterialTheme.colorScheme.error
                    ) {
                        Text(if (groupUnreadCount > 99) "99+" else groupUnreadCount.toString())
                    }
                }
            }
        },
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ChatAppBrandGradient)
            ) {
                TopAppBar(
                    title = {
                        Text(
                            "المحادثات",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    actions = {
                        IconButton(onClick = onOpenCreateGroup) {
                            Icon(Icons.Default.GroupAdd, contentDescription = "إنشاء جروب جديد", tint = Color.White)
                        }
                        IconButton(onClick = onOpenProfile) {
                            Icon(Icons.Default.MoreVert, contentDescription = "المزيد", tint = Color.White)
                        }
                    }
                )
            }
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
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                if (myCustomGroups.isNotEmpty() && searchQuery.isBlank()) {
                    item {
                        SectionLabel("الجروبات بتاعتي")
                    }
                    items(myCustomGroups, key = { "group_${it.id}" }) { group ->
                        CustomGroupRow(
                            group = group,
                            unreadCount = customGroupUnreadCounts[group.id] ?: 0,
                            onClick = { onOpenCustomGroup(group) }
                        )
                    }
                    item {
                        SectionLabel("المحادثات")
                    }
                }

                if (users.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (searchQuery.isBlank()) "مفيش مستخدمين تانيين لسه" else "مفيش نتايج بالاسم ده",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                itemsIndexed(users) { index, user ->
                    AnimatedListRow(index = index) {
                        UserRow(
                            user = user,
                            isOnline = presenceMap[user.uid] == true,
                            isTyping = typingUsers[user.uid] == true,
                            unread = unreadCounts[user.uid] ?: 0,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onOpenProfilePhoto = onOpenProfilePhoto,
                            onClick = { onOpenChat(user) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

/** لفافة بسيطة بتضيف ظهور تدريجي متدرّج (fade + slide) لكل صف في القايمة */
@Composable
private fun AnimatedListRow(index: Int, content: @Composable () -> Unit) {
    var shown by remember(index) { mutableStateOf(false) }
    LaunchedEffect(index) { shown = true }
    androidx.compose.animation.AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(280, delayMillis = (index % 12) * 25)) +
            slideInVertically(tween(280, delayMillis = (index % 12) * 25)) { it / 8 }
    ) {
        content()
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun UserRow(
    user: ChatUser,
    isOnline: Boolean,
    isTyping: Boolean,
    unread: Int,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpenProfilePhoto: (ChatUser) -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            with(sharedTransitionScope) {
                if (user.photoUrl.isNotBlank()) {
                    AsyncImage(
                        model = user.photoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
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
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(ChatAppBrandGradient)
                            .clickable { onOpenProfilePhoto(user) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            (user.displayName.firstOrNull() ?: '?').toString(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
            if (isOnline) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(OnlineGreen)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                user.displayName.ifBlank { user.email },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            if (isTyping) {
                Text(
                    "بيكتب الآن...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            } else if (user.bio.isNotBlank()) {
                Text(
                    user.bio,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    if (isOnline) "متصل الآن" else "غير متصل",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (unread > 0) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 24.dp)
                    .height(24.dp)
                    .clip(CircleShape)
                    .background(ChatAppBrandGradient)
                    .padding(horizontal = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unread > 99) "99+" else unread.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CustomGroupRow(group: ChatGroup, unreadCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (group.photoUrl.isNotBlank()) {
            AsyncImage(
                model = group.photoUrl,
                contentDescription = group.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                group.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                group.lastMessage.ifBlank { "مفيش رسائل لسه" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (unreadCount > 0) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 24.dp)
                    .height(24.dp)
                    .clip(CircleShape)
                    .background(ChatAppBrandGradient)
                    .padding(horizontal = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

