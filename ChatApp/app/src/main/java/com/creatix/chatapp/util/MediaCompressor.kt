package com.creatix.chatapp.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume

/**
 * ضغط المرفقات على الجهاز قبل الرفع (عشان التخزين على السيرفر والباك اب يفضلوا صغيرين):
 * - الصور: أطول ضلع 1280px وترميز WebP جودة 75 (الجيف بيتساب زي ما هو عشان ما يفقدش حركته).
 * - الفيديو: ترميز جديد H.264 بارتفاع أقصاه 720p وبيتريت ~1.5 ميجا بت/ثانية (MP4).
 * - الصوت المرفوع من الملفات: ترميز AAC بيتريت 64 كيلو بت/ثانية (M4A).
 *   (التسجيل من المايك أصلًا AAC 48k — متظبط في شاشات الشات.)
 * أي فشل في الضغط بيرجع null ونرفع الملف الأصلي — الضغط تحسين مش شرط.
 */
object MediaCompressor {

    private const val IMAGE_MAX_SIDE = 1280
    private const val IMAGE_QUALITY = 75
    private const val VIDEO_MAX_HEIGHT = 720
    private const val VIDEO_BITRATE = 1_500_000

    /** بيصغّر الصورة لو نوعها صورة (غير الجيف). بيرجع (الباتات، الاسم الجديد، النوع الجديد) أو null */
    fun compressImageIfNeeded(bytes: ByteArray, fileName: String, mimeType: String): Triple<ByteArray, String, String>? {
        if (!mimeType.startsWith("image/") || mimeType == "image/gif") return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= IMAGE_MAX_SIDE) sampleSize *= 2
            val decoded = BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }
            ) ?: return null

            val ratio = minOf(1f, IMAGE_MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
            val bitmap = if (ratio < 1f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * ratio).toInt().coerceAtLeast(1),
                    (decoded.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else decoded

            val out = ByteArrayOutputStream()
            val format =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
                else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
            bitmap.compress(format, IMAGE_QUALITY, out)
            val compressed = out.toByteArray()
            if (compressed.isEmpty() || compressed.size >= bytes.size) return null // مفيش توفير
            Triple(compressed, fileName.substringBeforeLast('.') + ".webp", "image/webp")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** ترميز جديد للفيديو: H.264 بحد أقصى 720p وبيتريت أقل. بيرجع null لو فشل أو مفيش توفير */
    suspend fun transcodeVideoIfNeeded(
        context: Context, bytes: ByteArray, fileName: String, mimeType: String
    ): Triple<ByteArray, String, String>? {
        if (!mimeType.startsWith("video/")) return null
        return transcode(context, bytes, fileName, isVideo = true, outExt = "mp4", outMime = "video/mp4")
    }

    /** ترميز جديد للصوت: AAC 64k. بيرجع null لو فشل أو مفيش توفير */
    suspend fun transcodeAudioIfNeeded(
        context: Context, bytes: ByteArray, fileName: String, mimeType: String
    ): Triple<ByteArray, String, String>? {
        if (!mimeType.startsWith("audio/")) return null
        return transcode(context, bytes, fileName, isVideo = false, outExt = "m4a", outMime = "audio/mp4")
    }

    private suspend fun transcode(
        context: Context,
        bytes: ByteArray,
        fileName: String,
        isVideo: Boolean,
        outExt: String,
        outMime: String
    ): Triple<ByteArray, String, String>? {
        var input: File? = null
        var output: File? = null
        return try {
            input = File.createTempFile("compress_in_", ".tmp", context.cacheDir).apply { writeBytes(bytes) }
            output = File.createTempFile("compress_out_", ".$outExt", context.cacheDir)
            val ok = runTransformer(context, Uri.fromFile(input), output.absolutePath, isVideo)
            if (!ok) return null
            val result = output.readBytes()
            if (result.isEmpty() || result.size >= bytes.size) return null // مفيش توفير، سيب الأصلي
            Triple(result, fileName.substringBeforeLast('.') + ".$outExt", outMime)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { input?.delete() } catch (_: Exception) {}
            try { output?.delete() } catch (_: Exception) {}
        }
    }

    /** Media3 Transformer شغال على الـ Main thread (محتاج Looper) */
    private suspend fun runTransformer(
        context: Context, input: Uri, outputPath: String, isVideo: Boolean
    ): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder().setBitrate(VIDEO_BITRATE).build()
                )
                .build()
            val builder = Transformer.Builder(context)
                .setEncoderFactory(encoderFactory)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onError(
                        composition: Composition, exportResult: ExportResult, exportException: ExportException
                    ) {
                        exportException.printStackTrace()
                        if (cont.isActive) cont.resume(false)
                    }
                })
            if (isVideo) builder.setVideoMimeType(MimeTypes.VIDEO_H264)
            else builder.setAudioMimeType(MimeTypes.AUDIO_AAC)
            val transformer = builder.build()
            cont.invokeOnCancellation { transformer.cancel() }
            try {
                if (isVideo) {
                    // تأثيرات الفيديو (تحجيم لـ 720p) بتتحط على الـ EditedMediaItem مش على الـ Transformer
                    val editedItem = EditedMediaItem.Builder(MediaItem.fromUri(input))
                        .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(VIDEO_MAX_HEIGHT))))
                        .build()
                    transformer.start(
                        Composition.Builder(EditedMediaItemSequence(editedItem)).build(),
                        outputPath
                    )
                } else {
                    transformer.start(MediaItem.fromUri(input), outputPath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (cont.isActive) cont.resume(false)
            }
        }
    }
}
