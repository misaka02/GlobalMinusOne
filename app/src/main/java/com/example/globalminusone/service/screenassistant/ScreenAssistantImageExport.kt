package com.example.globalminusone.service.screenassistant

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.globalminusone.screenassistant.SubImageResult
import com.example.globalminusone.system.ImageClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal sealed class ScreenAssistantImage {
    abstract val bitmap: Bitmap
    data class Screenshot(override val bitmap: Bitmap) : ScreenAssistantImage()
    data class Extracted(val image: SubImageResult) : ScreenAssistantImage() {
        override val bitmap get() = image.bitmap
    }
}

/** Android export adapter. It borrows images; the window host retains their final ownership. */
internal class ScreenAssistantImageExport(
    private val context: Context,
    private val onShowFeedback: (String) -> Unit = {},
    private val onDismissOverlayForShare: () -> Unit = {}
) :
    ScreenAssistantImageActions.Port<ScreenAssistantImage, Uri> {
    override suspend fun copy(image: ScreenAssistantImage) {
        val src = image.bitmap
        check(!src.isRecycled) { "图片已释放" }
        val software = if (src.config == Bitmap.Config.HARDWARE) {
            checkNotNull(src.copy(Bitmap.Config.ARGB_8888, false)) { "无法读取图片像素" }
        } else null
        try {
            ImageClipboard.copy(context, software ?: src)
        } finally {
            if (software != null && !software.isRecycled) software.recycle()
        }
    }

    override suspend fun save(image: ScreenAssistantImage): Boolean = withContext(Dispatchers.IO) {
        when (image) {
            is ScreenAssistantImage.Extracted -> saveSubImageToGallery(context, image.image)
            is ScreenAssistantImage.Screenshot -> saveBitmapToGallery(context, image.bitmap)
        } != null
    }

    override suspend fun prepareShare(image: ScreenAssistantImage): Uri? {
        var prepared: Uri? = null
        try {
            return withContext(Dispatchers.IO) {
                when (image) {
                    is ScreenAssistantImage.Extracted -> writeTempShareSubImage(context, image.image)
                    is ScreenAssistantImage.Screenshot -> writeTempSharePng(context, image.bitmap)
                }.also { prepared = it } ?: error("无法生成分享图片")
            }
        } catch (failure: Throwable) {
            // A dispatcher return can be cancelled after the private share file was written.
            prepared?.let(::discardPreparedShare)
            throw failure
        }
    }

    override fun discardPreparedShare(prepared: Uri) {
        runCatching { context.contentResolver.delete(prepared, null, null) }
    }

    override fun share(image: ScreenAssistantImage, prepared: Uri) {
        runCatching {
            val mime = when (image) {
                is ScreenAssistantImage.Extracted -> image.image.rawMimeType.ifBlank { "image/png" }
                is ScreenAssistantImage.Screenshot -> "image/png"
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                clipData = ClipData.newRawUri("ShareImage", prepared)
                putExtra(Intent.EXTRA_STREAM, prepared)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, "分享提取图片").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
        }.onSuccess {
            onDismissOverlayForShare()
        }.onFailure { err ->
            discardPreparedShare(prepared)
            Log.w(TAG, "share image failed", err)
            feedback("无法调起系统分享：${err.localizedMessage ?: "未知错误"}")
        }
    }

    override fun feedback(message: String) {
        onShowFeedback(message)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "ScreenAssistantOverlay"
        private fun timestampToken(): String =
            SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

        private fun saveSubImageToGallery(context: Context, sub: SubImageResult): Uri? {
            val rawBytes = sub.rawEncodedBytes
            if (rawBytes == null) return saveBitmapToGallery(context, sub.bitmap)
            val ext = sub.rawExtension.ifBlank { "png" }
            val mime = sub.rawMimeType.ifBlank { "image/png" }
            val prefix = if (sub.isUnderlyingOriginal) "UnderlyingImage" else "ScreenExtract"
            val fileName = "${prefix}_${timestampToken()}.$ext"
            val resolver = context.contentResolver
            var insertedUri: Uri? = null
            return try {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mime)
                    put(MediaStore.Images.Media.WIDTH, sub.bitmap.width)
                    put(MediaStore.Images.Media.HEIGHT, sub.bitmap.height)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ScreenExtracts")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
                insertedUri = uri
                val ok = resolver.openOutputStream(uri)?.use { out ->
                    out.write(rawBytes)
                    true
                } == true
                if (!ok) {
                    runCatching { resolver.delete(uri, null, null) }
                    return null
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val updated = resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                    check(updated > 0) { "无法发布相册图片" }
                }
                uri
            } catch (t: Throwable) {
                insertedUri?.let { uri -> runCatching { resolver.delete(uri, null, null) } }
                Log.w(TAG, "saveSubImageToGallery failed", t)
                null
            }
        }

        private fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Uri? {
            if (bitmap.isRecycled) return null
            val safeBmp = if (bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
            } else bitmap
            val fileName = "ScreenExtract_${timestampToken()}.png"
            val resolver = context.contentResolver
            var insertedUri: Uri? = null
            return try {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.WIDTH, safeBmp.width)
                    put(MediaStore.Images.Media.HEIGHT, safeBmp.height)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ScreenExtracts")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
                insertedUri = uri
                val ok = resolver.openOutputStream(uri)?.use { out -> safeBmp.compress(Bitmap.CompressFormat.PNG, 100, out) } == true
                if (!ok) {
                    runCatching { resolver.delete(uri, null, null) }
                    return null
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val updated = resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                    check(updated > 0) { "无法发布相册图片" }
                }
                uri
            } catch (t: Throwable) {
                insertedUri?.let { uri -> runCatching { resolver.delete(uri, null, null) } }
                Log.w(TAG, "saveBitmapToGallery failed", t)
                null
            } finally {
                if (safeBmp !== bitmap && !safeBmp.isRecycled) safeBmp.recycle()
            }
        }

        private fun writeTempShareSubImage(context: Context, sub: SubImageResult): Uri? {
            val rawBytes = sub.rawEncodedBytes ?: return writeTempSharePng(context, sub.bitmap)
            var createdFile: File? = null
            return try {
                val dir = File(context.cacheDir, "screen_extracts").apply { mkdirs() }
                dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(5)?.forEach { runCatching { it.delete() } }
                val ext = sub.rawExtension.ifBlank { "png" }
                val file = File.createTempFile("Extract_${timestampToken()}_", ".$ext", dir)
                createdFile = file
                FileOutputStream(file).use { out -> out.write(rawBytes) }
                FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            } catch (t: Throwable) {
                createdFile?.let { file -> runCatching { file.delete() } }
                Log.w(TAG, "writeTempShareSubImage failed", t)
                null
            }
        }

        private fun writeTempSharePng(context: Context, bitmap: Bitmap): Uri? {
            if (bitmap.isRecycled) return null
            val safeBmp = if (bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
            } else bitmap
            var createdFile: File? = null
            return try {
                val dir = File(context.cacheDir, "screen_extracts").apply { mkdirs() }
                dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(5)?.forEach { runCatching { it.delete() } }
                val file = File.createTempFile("Extract_${timestampToken()}_", ".png", dir)
                createdFile = file
                FileOutputStream(file).use { out ->
                    check(safeBmp.compress(Bitmap.CompressFormat.PNG, 100, out)) { "无法编码分享图片" }
                }
                FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            } catch (t: Throwable) {
                createdFile?.let { file -> runCatching { file.delete() } }
                Log.w(TAG, "writeTempSharePng failed", t)
                null
            } finally {
                if (safeBmp !== bitmap && !safeBmp.isRecycled) safeBmp.recycle()
            }
        }
    }
}
