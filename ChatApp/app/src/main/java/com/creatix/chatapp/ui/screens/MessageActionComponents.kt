package com.creatix.chatapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.creatix.chatapp.data.ChatGroup
import com.creatix.chatapp.data.ChatUser

/** الإيموجيهات السريعة اللي بتظهر لما تعمل ضغطة طويلة على أي رسالة */
val QUICK_REACTION_EMOJIS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

/**
 * قائمة الإجراءات اللي بتظهر لما تعمل ضغطة طويلة على أي رسالة:
 * رد (اختياري) / نسخ / إعادة توجيه / تعديل (لو رسالتي) / حذف (لو رسالتي)
 */
@Composable
fun MessageActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    canEdit: Boolean,
    canDelete: Boolean,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReply: (() -> Unit)? = null,
    onReact: ((String) -> Unit)? = null,
    onMoreReactions: (() -> Unit)? = null,
    quickEmojis: List<String> = QUICK_REACTION_EMOJIS
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (onReact != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                quickEmojis.forEach { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 22.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                onDismiss()
                                onReact(emoji)
                            }
                            .padding(4.dp)
                    )
                }
                if (onMoreReactions != null) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.Gray.copy(alpha = 0.15f))
                            .clickable {
                                onDismiss()
                                onMoreReactions()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "⋯", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Divider()
        }
        if (onReply != null) {
            DropdownMenuItem(
                text = { Text("رد") },
                onClick = {
                    onDismiss()
                    onReply()
                }
            )
        }
        DropdownMenuItem(
            text = { Text("نسخ") },
            onClick = {
                onDismiss()
                onCopy()
            }
        )
        DropdownMenuItem(
            text = { Text("إعادة توجيه") },
            onClick = {
                onDismiss()
                onForward()
            }
        )
        if (canEdit) {
            DropdownMenuItem(
                text = { Text("تعديل") },
                onClick = {
                    onDismiss()
                    onEdit()
                }
            )
        }
        if (canDelete) {
            DropdownMenuItem(
                text = { Text("حذف", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    onDismiss()
                    onDelete()
                }
            )
        }
    }
}

/**
 * نافذة اختيار وجهة إعادة التوجيه: الجروب العام / جروباتي المخصصة / أي مستخدم.
 */
@Composable
fun ForwardDialog(
    users: List<ChatUser>,
    groups: List<ChatGroup>,
    onDismiss: () -> Unit,
    onForwardToGlobalGroup: () -> Unit,
    onForwardToGroup: (ChatGroup) -> Unit,
    onForwardToUser: (ChatUser) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعادة توجيه إلى") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                item {
                    Text(
                        text = "الجروب العام",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onForwardToGlobalGroup() }
                            .padding(vertical = 12.dp)
                    )
                    Divider()
                }
                items(groups) { group ->
                    Text(
                        text = group.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onForwardToGroup(group) }
                            .padding(vertical = 12.dp)
                    )
                    Divider()
                }
                items(users) { user ->
                    Text(
                        text = user.displayName.ifBlank { user.email },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onForwardToUser(user) }
                            .padding(vertical = 12.dp)
                    )
                    Divider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

/**
 * شريط صغير بيظهر تحت الرسالة يلخص التفاعلات (إيموجي + عدد)،
 * وبيسمح بإزالة/تغيير تفاعل المستخدم نفسه بالضغط عليه.
 */
@Composable
fun ReactionsChipsRow(
    reactions: Map<String, String>,
    myUid: String,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (reactions.isEmpty()) return
    val counts = reactions.values.groupingBy { it }.eachCount()
    val myReaction = reactions[myUid]

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        counts.forEach { (emoji, count) ->
            val mine = emoji == myReaction
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else Color.Transparent
                    )
                    .clickable { onToggle(emoji) }
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(text = emoji, fontSize = 13.sp)
                if (count > 1) {
                    Text(
                        text = " $count",
                        fontSize = 11.sp,
                        fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                        color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * نافذة اختيار الإيموجي الكاملة - بتظهر لما تدوس على زرار الـ "⋯" جنب
 * الإيموجيهات السريعة. كل الإيموجي متخزّن جوه التطبيق نفسه (EMOJI_CATEGORIES)
 * ومش معتمد على كيبورد الجهاز، مقسّم لتصنيفات مع بحث بالاسم مش متاح
 * (البحث بيفلتر بالإيموجي المطابق حرفيًا لو اتلصق، والأساسي هو التصنيفات).
 */
@Composable
fun FullEmojiPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var selectedCategory by rememberSaveable { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(min = 420.dp, max = 560.dp),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "اختر إيموجي",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    placeholder = { Text("دوّر أو الصق إيموجي…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(8.dp))

                if (searchQuery.isBlank()) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedCategory,
                        edgePadding = 8.dp
                    ) {
                        EMOJI_CATEGORIES.forEachIndexed { index, category ->
                            Tab(
                                selected = selectedCategory == index,
                                onClick = { selectedCategory = index },
                                text = { Text(category.icon, fontSize = 18.sp) }
                            )
                        }
                    }
                }

                val displayedEmojis = if (searchQuery.isNotBlank()) {
                    // البحث بيدور على الإيموجي في كل التصنيفات (مفيد لو المستخدم لصق إيموجي)
                    EMOJI_CATEGORIES.flatMap { it.emojis }.filter { it.contains(searchQuery) }
                } else {
                    EMOJI_CATEGORIES[selectedCategory].emojis
                }

                if (displayedEmojis.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("لا يوجد نتائج", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(8),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(displayedEmojis) { emoji ->
                            Box(
                                modifier = Modifier
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        onSelect(emoji)
                                        onDismiss()
                                    }
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 24.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** نافذة تأكيد بسيطة بتتستخدم لتأكيد حذف رسالة */
@Composable
fun DeleteMessageConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حذف الرسالة") },
        text = { Text("هل أنت متأكد إنك عايز تحذف الرسالة دي؟") },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
