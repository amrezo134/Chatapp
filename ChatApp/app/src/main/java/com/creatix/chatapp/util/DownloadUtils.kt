package com.creatix.chatapp.util

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * أدوات تحميل الملفات المرسلة (صور / فيديو / مستندات / صوت) لمجلد التنزيلات بتاع الجهاز.
 */

/** بيستخرج اسم ملف من الرابط لو الاسم الأصلي مش متوفر */
fun fileNameFromUrl(url: String, fallback: String): String {
    return try {
        val last = Uri.parse(url).lastPathSegment?.substringAfterLast('/')
        if (!last.isNullOrBlank()) last else fallback
    } catch (e: Exception) {
        fallback
    }
}

private fun sanitizeFileName(name: String): String {
    val safe = name.ifBlank { "ملف_${System.currentTimeMillis()}" }
    return safe.replace(Regex("[\\\\/:*?\"<>|]"), "_")
}

/** بينزل الملف فعليًا عن طريق DownloadManager (بيظهر في شريط الإشعارات وفي مجلد "التنزيلات") */
fun downloadFileToDevice(context: Context, url: String, fileName: String) {
    if (url.isBlank()) return
    val safeName = sanitizeFileName(fileName)
    try {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(safeName)
            .setDescription("جاري تحميل الملف...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        Toast.makeText(context, "جاري تحميل \"$safeName\" إلى مجلد التنزيلات", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "تعذر تحميل الملف: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Composable helper بيرجعلك دالة download(url, fileName) جاهزة تستخدمها في onClick لأي زرار تحميل.
 * بتطلب صلاحية التخزين تلقائيًا لو الجهاز أندرويد 9 أو أقل (API < 29)، وبعدين تبدأ التحميل.
 */
@Composable
fun rememberFileDownloader(): (String, String) -> Unit {
    val context = LocalContext.current
    var pendingDownload by remember { mutableStateOf<Pair<String, String>?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingDownload
        pendingDownload = null
        if (granted && pending != null) {
            downloadFileToDevice(context, pending.first, pending.second)
        } else if (!granted) {
            Toast.makeText(context, "محتاجين صلاحية التخزين عشان ننزل الملف", Toast.LENGTH_SHORT).show()
        }
    }

    return { url, fileName ->
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingDownload = url to fileName
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            downloadFileToDevice(context, url, fileName)
        }
    }
}
