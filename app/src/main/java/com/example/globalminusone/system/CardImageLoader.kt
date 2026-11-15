package com.example.globalminusone.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.example.globalminusone.data.CARD_IMAGE_MAX_EDGE
import com.example.globalminusone.data.cardImageTargetSize
import com.example.globalminusone.data.readCardImageBytes
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private val imageDecodeSlots = Semaphore(2)

internal suspend fun loadCardImage(context: Context, rawUri: String, maxEdge: Int = CARD_IMAGE_MAX_EDGE): Bitmap = imageDecodeSlots.withPermit {
    withContext(Dispatchers.IO) { decodeCardImage(context, rawUri, maxEdge) }
}

/** Worker-thread decoder. Never retain a provider stream or an activity after decoding. */
private fun decodeCardImage(context: Context, rawUri: String, maxEdge: Int): Bitmap {
    require(maxEdge in 1..CARD_IMAGE_MAX_EDGE)
    val uri = Uri.parse(rawUri)
    fun openStream() = when (uri.scheme?.lowercase()) {
        null, "", "file" -> File(uri.path ?: rawUri).inputStream()
        "content", "android.resource" -> context.contentResolver.openInputStream(uri)
        else -> throw IOException("不支持的图片来源")
    } ?: throw IOException("图片提供方未返回内容")

    if (Build.VERSION.SDK_INT >= 28) {
        try {
            val source = when (uri.scheme?.lowercase()) {
                null, "", "file" -> ImageDecoder.createSource(File(uri.path ?: rawUri))
                "content", "android.resource" -> ImageDecoder.createSource(context.contentResolver, uri)
                else -> throw IOException("不支持的图片来源")
            }
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val (width, height) = cardImageTargetSize(info.size.width, info.size.height, maxEdge)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSize(width, height)
            }
        } catch (denied: SecurityException) {
            throw denied
        } catch (_: IOException) {
            // Providers with non-seekable descriptors can still offer an ordinary stream.
        }
    }
    val bytes = openStream().use { readCardImageBytes(it) }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "图片格式无法解码或内容不完整" }
    val options = BitmapFactory.Options().apply {
        var sample = 1
        while (bounds.outWidth / sample > maxEdge || bounds.outHeight / sample > maxEdge) sample *= 2
        inSampleSize = sample
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        ?: throw IOException("图片格式无法解码或内容不完整")
}
