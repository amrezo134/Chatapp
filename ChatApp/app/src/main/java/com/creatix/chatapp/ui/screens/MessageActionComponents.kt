package com.creatix.chatapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.creatix.chatapp.data.ChatGroup
import com.creatix.chatapp.data.ChatUser

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
    onReply: (() -> Unit)? = null
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
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
                text = { Text("حذف", color = Color(0xFFD32F2F)) },
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
            }) { Text("حذف", color = Color(0xFFD32F2F)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
