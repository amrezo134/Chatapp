package com.creatix.chatapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.creatix.chatapp.data.ChatUser
import com.creatix.chatapp.ui.theme.ChatAppBrandGradient
import com.creatix.chatapp.viewmodel.AuthViewModel
import com.creatix.chatapp.viewmodel.ChatViewModel
import com.creatix.chatapp.viewmodel.CreateGroupState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    onBack: () -> Unit,
    onGroupCreated: (String) -> Unit
) {
    val myUid = authViewModel.currentUid ?: return
    val users by chatViewModel.users.collectAsState()
    val createState by chatViewModel.createGroupState.collectAsState()

    var groupName by remember { mutableStateOf("") }
    var searchText by remember { mutableStateOf("") }
    val selectedMemberIds = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) { chatViewModel.loadUsers(myUid) }

    LaunchedEffect(createState) {
        val state = createState
        if (state is CreateGroupState.Success) {
            onGroupCreated(state.groupId)
            chatViewModel.resetCreateGroupState()
        }
    }

    DisposableEffect(Unit) {
        onDispose { chatViewModel.resetCreateGroupState() }
    }

    val filteredUsers = remember(users, searchText) {
        if (searchText.isBlank()) {
            users
        } else {
            users.filter {
                it.displayName.contains(searchText, ignoreCase = true) ||
                    it.email.contains(searchText, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            Box(modifier = Modifier.fillMaxWidth().background(ChatAppBrandGradient)) {
                TopAppBar(
                    title = { Text("جروب جديد", color = Color.White, fontWeight = FontWeight.SemiBold) },
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
            Column(modifier = Modifier.padding(16.dp)) {
                if (createState is CreateGroupState.Error) {
                    Text(
                        (createState as CreateGroupState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Button(
                    onClick = {
                        chatViewModel.createCustomGroup(groupName, myUid, selectedMemberIds.toList())
                    },
                    enabled = createState != CreateGroupState.Loading,
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(ChatAppBrandGradient, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (createState == CreateGroupState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text(
                                "إنشاء الجروب (${selectedMemberIds.size})",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("اسم الجروب") },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )

            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("دور على مستخدم بالاسم...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(8.dp))

            if (filteredUsers.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("مفيش مستخدمين مطابقين", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredUsers, key = { it.uid }) { user ->
                        val isSelected = selectedMemberIds.contains(user.uid)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSelected) selectedMemberIds.remove(user.uid) else selectedMemberIds.add(user.uid)
                                }
                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(user)
                            Spacer(Modifier.width(14.dp))
                            Text(
                                user.displayName.ifBlank { user.email },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(ChatAppBrandGradient),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "متختار",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserAvatar(user: ChatUser) {
    if (user.photoUrl.isNotBlank()) {
        AsyncImage(
            model = user.photoUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(CircleShape)
        )
    } else {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(ChatAppBrandGradient),
            contentAlignment = Alignment.Center
        ) {
            Text(
                (user.displayName.firstOrNull() ?: '?').toString(),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
