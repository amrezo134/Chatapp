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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.creatix.chatapp.data.ChatGroup
import com.creatix.chatapp.data.ChatUser
import com.creatix.chatapp.ui.theme.ChatAppBrandGradient
import com.creatix.chatapp.viewmodel.AuthViewModel
import com.creatix.chatapp.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * شاشة معلومات الجروب: صورة الجروب، بايوه، وقائمة الأعضاء.
 * صاحب الجروب والمشرفين بس يقدروا يغيروا الصورة/البايو ويضيفوا أعضاء.
 * صاحب الجروب بس يقدر يرقّي عضو لمشرف أو يحذف الجروب نهائيًا.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun GroupInfoScreen(
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    group: ChatGroup,
    onBack: () -> Unit,
    onOpenGroupPhoto: () -> Unit,
    onGroupDeleted: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val myUid = authViewModel.currentUid ?: return
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val allUsers by chatViewModel.users.collectAsState()
    val myProfile by chatViewModel.myProfile.collectAsState()
    val groupInfoError by chatViewModel.groupInfoError.collectAsState()

    LaunchedEffect(myUid) {
        chatViewModel.loadUsers(myUid)
        chatViewModel.loadMyProfile(myUid)
    }

    val isOwner = group.isOwner(myUid)
    val isAdmin = group.isAdmin(myUid)

    val userMap = remember(allUsers, myProfile) {
        (allUsers + listOfNotNull(myProfile)).associateBy { it.uid }
    }
    val memberUsers = remember(group.memberIds, userMap) {
        group.memberIds.map { uid -> userMap[uid] ?: ChatUser(uid = uid, displayName = "مستخدم") }
    }

    var showBioEditor by remember { mutableStateOf(false) }
    var showAddMembers by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isUploadingPhoto by remember { mutableStateOf(false) }

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        isUploadingPhoto = true
        coroutineScope.launch {
            val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.readBytes() }
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            if (bytes != null) {
                chatViewModel.updateGroupPhoto(group.id, bytes, "group.${mime.substringAfterLast('/')}", mime)
            }
            isUploadingPhoto = false
        }
    }

    Scaffold(
        topBar = {
            Box(modifier = Modifier.fillMaxWidth().background(ChatAppBrandGradient)) {
                TopAppBar(
                    title = { Text("معلومات الجروب", color = Color.White, fontWeight = FontWeight.SemiBold) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    with(sharedTransitionScope) {
                        Box {
                            if (group.photoUrl.isNotBlank()) {
                                AsyncImage(
                                    model = group.photoUrl,
                                    contentDescription = group.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(120.dp)
                                        .shadow(8.dp, CircleShape)
                                        .clip(CircleShape)
                                        .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        .sharedElement(
                                            state = rememberSharedContentState(key = "group-photo-${group.id}"),
                                            animatedVisibilityScope = animatedVisibilityScope
                                        )
                                        .clickable { onOpenGroupPhoto() }
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .shadow(8.dp, CircleShape)
                                        .clip(CircleShape)
                                        .background(ChatAppBrandGradient)
                                        .sharedElement(
                                            state = rememberSharedContentState(key = "group-photo-${group.id}"),
                                            animatedVisibilityScope = animatedVisibilityScope
                                        )
                                        .clickable(enabled = isAdmin) { if (isAdmin) pickPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        (group.name.firstOrNull() ?: '#').toString(),
                                        fontSize = 40.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (isAdmin) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(34.dp)
                                        .shadow(4.dp, CircleShape)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .clickable {
                                            pickPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isUploadingPhoto) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                    } else {
                                        Icon(Icons.Default.CameraAlt, contentDescription = "غيّر صورة الجروب", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(group.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${group.memberIds.size} عضو",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .then(if (isAdmin) Modifier.clickable { showBioEditor = true } else Modifier)
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("البايو", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        if (isAdmin) {
                            Icon(Icons.Default.Edit, contentDescription = "عدّل البايو", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        group.bio.ifBlank { "مفيش بايو لسه" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (group.bio.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "الأعضاء (${group.memberIds.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (isAdmin) {
                        TextButton(onClick = { showAddMembers = true }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("إضافة")
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            items(memberUsers, key = { it.uid }) { member ->
                GroupMemberRow(
                    member = member,
                    isMe = member.uid == myUid,
                    isTargetOwner = group.isOwner(member.uid),
                    isTargetAdmin = group.isAdmin(member.uid),
                    canPromote = isOwner && member.uid != myUid && !group.isAdmin(member.uid),
                    onPromote = { chatViewModel.promoteGroupMember(group.id, member.uid) }
                )
            }

            if (isOwner) {
                item {
                    Spacer(Modifier.height(24.dp))
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("حذف الجروب", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showBioEditor) {
        BioEditorDialog(
            initialBio = group.bio,
            onDismiss = { showBioEditor = false },
            onSave = { newBio ->
                chatViewModel.updateGroupBio(group.id, newBio)
                showBioEditor = false
            }
        )
    }

    if (showAddMembers) {
        AddGroupMembersDialog(
            allUsers = allUsers,
            existingMemberIds = group.memberIds,
            onDismiss = { showAddMembers = false },
            onConfirm = { selectedIds ->
                chatViewModel.addMembersToGroup(group.id, selectedIds)
                showAddMembers = false
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف الجروب") },
            text = { Text("متأكد إنك عايز تحذف \"${group.name}\" نهائيًا؟ الخطوة دي مش هترجع.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        chatViewModel.deleteGroup(group.id, onDeleted = onGroupDeleted)
                    }
                ) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("إلغاء") }
            }
        )
    }

    val currentError = groupInfoError
    if (currentError != null) {
        AlertDialog(
            onDismissRequest = { chatViewModel.clearGroupInfoError() },
            confirmButton = { TextButton(onClick = { chatViewModel.clearGroupInfoError() }) { Text("حسنًا") } },
            title = { Text("حصل خطأ") },
            text = { Text(currentError) }
        )
    }
}

@Composable
private fun GroupMemberRow(
    member: ChatUser,
    isMe: Boolean,
    isTargetOwner: Boolean,
    isTargetAdmin: Boolean,
    canPromote: Boolean,
    onPromote: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (member.photoUrl.isNotBlank()) {
            AsyncImage(
                model = member.photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(ChatAppBrandGradient),
                contentAlignment = Alignment.Center
            ) {
                Text((member.displayName.firstOrNull() ?: '?').toString(), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                member.displayName.ifBlank { member.email }.let { if (isMe) "$it (أنا)" else it },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isTargetOwner || isTargetAdmin) {
                Text(
                    if (isTargetOwner) "صاحب الجروب" else "مشرف",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (canPromote) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "خيارات")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("خليه مشرف") },
                        onClick = { menuExpanded = false; onPromote() }
                    )
                }
            }
        }
    }
}

@Composable
private fun BioEditorDialog(initialBio: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var bio by remember { mutableStateOf(initialBio) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بايو الجروب") },
        text = {
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                placeholder = { Text("اكتب وصف قصير للجروب...") },
                shape = RoundedCornerShape(12.dp),
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onSave(bio) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun AddGroupMembersDialog(
    allUsers: List<ChatUser>,
    existingMemberIds: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<String>() }

    val candidates = remember(allUsers, existingMemberIds, searchText) {
        allUsers.filter { it.uid !in existingMemberIds }
            .filter {
                searchText.isBlank() ||
                    it.displayName.contains(searchText, ignoreCase = true) ||
                    it.email.contains(searchText, ignoreCase = true)
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة أعضاء") },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp)) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = { Text("دور على مستخدم...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (candidates.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("مفيش مستخدمين لإضافتهم", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn {
                        items(candidates, key = { it.uid }) { user ->
                            val isSelected = selectedIds.contains(user.uid)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) selectedIds.remove(user.uid) else selectedIds.add(user.uid)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = isSelected, onCheckedChange = null)
                                Spacer(Modifier.width(6.dp))
                                Text(user.displayName.ifBlank { user.email }, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedIds.toList()) },
                enabled = selectedIds.isNotEmpty()
            ) { Text("إضافة (${selectedIds.size})") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
