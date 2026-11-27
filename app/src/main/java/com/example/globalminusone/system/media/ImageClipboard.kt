package com.example.globalminusone.system.media

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

internal object ImageClipboard {
    private val publication = Mutex()

    /** Caller keeps [bitmap] alive until this returns. It is never recycled here. */
    suspend fun copy(context: Context, bitmap: Bitmap) = publication.withLock {
        val app = context.applicationContext
        val files = ImageClipboardFiles(File(app.filesDir, "image_clipboard"))
        var staged: File? = null
        var published = false
        try {
            // Assign inside IO so cancellation on dispatcher return cannot leak an unpublished PNG.
            withContext(Dispatchers.IO) {
                staged = files.stage { file ->
                    file.outputStream().use { out ->
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "Cannot encode clipboard image" }
                    }
                }
            }
            coroutineContext.ensureActive()
            val file = checkNotNull(staged)
            withContext(Dispatchers.Main.immediate) {
                coroutineContext.ensureActive()
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
                val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                // Android grants URI access to eligible clipboard readers; do not grant all packages.
                clipboard.setPrimaryClip(ClipData.newUri(app.contentResolver, "图片", uri))
                published = true
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                staged?.let { file ->
                    if (published) {
                        // If marker IO fails, retain all images rather than break a published URI.
                        runCatching { files.retainPublished(file) }
                            .onFailure { Log.w("ImageClipboard", "Could not update retained image marker", it) }
                    } else file.delete()
                }
            }
        }
    }
}
