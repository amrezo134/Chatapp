package com.creatix.chatapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onReact: ((String) -> Unit)? = null
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (onReact != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QUICK_REACTION_EMOJIS.forEach { emoji ->
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

