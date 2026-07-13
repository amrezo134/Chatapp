package com.creatix.chatapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** نوع الإرفاق - بيتبعت لـ onSelect لما المستخدم يدوس على أي زرار */
enum class AttachmentType {
    DOCUMENT, MEDIA, CAMERA, AUDIO, CONTACT, POLL, EVENT, STICKER
}

private data class AttachmentItem(
    val type: AttachmentType,
    val label: String,
    val icon: ImageVector,
    val color: Color
)

private val attachmentItems = listOf(
    AttachmentItem(AttachmentType.DOCUMENT, "مستند", Icons.Filled.Description, Color(0xFF7F66FF)),
    AttachmentItem(AttachmentType.MEDIA, "الصور ومقاطع الفيديو", Icons.Filled.Image, Color(0xFF3E7BFA)),
    AttachmentItem(AttachmentType.CAMERA, "الكاميرا", Icons.Filled.CameraAlt, Color(0xFFE5484D)),
    AttachmentItem(AttachmentType.AUDIO, "مقطع صوتي", Icons.Filled.Headset, Color(0xFFFF9F5A)),
    AttachmentItem(AttachmentType.CONTACT, "جهة اتصال", Icons.Filled.Person, Color(0xFF3E9BFA)),
    AttachmentItem(AttachmentType.POLL, "استطلاع رأي", Icons.Filled.Poll, Color(0xFFE8B400)),
    AttachmentItem(AttachmentType.EVENT, "مناسبة", Icons.Filled.Event, Color(0xFFE5484D)),
    AttachmentItem(AttachmentType.STICKER, "ملصق جديد", Icons.Filled.InsertEmoticon, Color(0xFF2ECC71)),
)

/**
 * البوتوم شيت بتاعة قائمة الإرفاق (زي الصورة اللي بعتها).
 * استخدمها من ChatScreen: بس اظهر AttachmentMenu لما تدوس على زرار الدبوس/الـ +.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AttachmentMenu(
    onDismiss: () -> Unit,
    onSelect: (AttachmentType) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(attachmentItems) { item ->
                AttachmentButton(item = item, onClick = {
                    onSelect(item.type)
                    onDismiss()
                })
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AttachmentButton(item: AttachmentItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(item.color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2
        )
    }
}
