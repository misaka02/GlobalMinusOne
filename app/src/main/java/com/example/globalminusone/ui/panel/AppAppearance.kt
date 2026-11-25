package com.example.globalminusone.ui.panel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.system.AppAndIntentLauncher
import com.example.globalminusone.system.BoundedMemoryLruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

internal data class AppMenuEnvironment(val actions: AppCardMenuActions, val destinationPicker: CardDestinationPicker, val pageId: String, val item: ComponentItem)
internal val LocalAppMenuEnvironment = staticCompositionLocalOf<AppMenuEnvironment?> { null }

/** Small user-selected icons only. Decode off the UI thread, sampled, with a bounded process cache. */
internal object UserIconCache {
    private val stripes = Array(8) { Mutex() }
    private val decodeSemaphore = Semaphore(12)
    private val bitmaps = BoundedMemoryLruCache<String, Bitmap>(512)
    private val imageBitmaps = BoundedMemoryLruCache<String, ImageBitmap>(512)
    private val missingUris = BoundedMemoryLruCache<String, Boolean>(256)

    private fun cacheKey(uri: String, revision: Long): String = "$revision|${uri.trim()}"

    fun peek(uri: String, revision: Long): Bitmap? {
        if (uri.isBlank()) return null
        return bitmaps.get(cacheKey(uri, revision))
    }

    fun peekImageBitmap(uri: String, revision: Long): ImageBitmap? {
        if (uri.isBlank()) return null
        val key = cacheKey(uri, revision)
        imageBitmaps.get(key)?.let { return it }
        val bmp = bitmaps.get(key) ?: return null
        return runCatching { bmp.asImageBitmap() }.getOrNull()?.also { imageBitmaps.put(key, it) }
    }

    fun isKnownMissing(uri: String, revision: Long): Boolean {
        if (uri.isBlank()) return false
        return missingUris.get(cacheKey(uri, revision)) == true
    }

    fun trimMemory(clearAll: Boolean) {
        imageBitmaps.evictAll()
        if (clearAll) {
            bitmaps.evictAll()
            missingUris.evictAll()
        }
    }

    suspend fun load(context: Context, uri: String, revision: Long, notify: Boolean = true): Bitmap? {
        val trimmed = uri.trim()
        if (trimmed.isEmpty()) return null
        val cacheKey = cacheKey(trimmed, revision)
        bitmaps.get(cacheKey)?.let { return it }
        if (missingUris.get(cacheKey) == true) return null
        return withContext(Dispatchers.IO) {
            bitmaps.get(cacheKey)?.let { return@withContext it }
            if (missingUris.get(cacheKey) == true) return@withContext null
            val stripe = stripes[(cacheKey.hashCode() and 0x7fffffff) % stripes.size]
            stripe.withLock {
                bitmaps.get(cacheKey)?.let { return@withLock it }
                if (missingUris.get(cacheKey) == true) return@withLock null
                decodeSemaphore.withPermit {
                    val decoded = runCatching {
                        val parsed = Uri.parse(trimmed)
                        when (parsed.scheme) {
                            "content" -> Unit
                            "file" -> {
                                val file = java.io.File(requireNotNull(parsed.path)).canonicalFile
                                require(file.isFile && file.toPath().startsWith(context.filesDir.canonicalFile.toPath()))
                            }
                            else -> error("Unsupported icon location")
                        }
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                        require(bounds.outWidth > 0 && bounds.outHeight > 0)
                        var sample = 1
                        while (bounds.outWidth / sample > 192 || bounds.outHeight / sample > 192) sample *= 2
                        val options = BitmapFactory.Options().apply { inSampleSize = sample }
                        context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, options) }
                    }.getOrNull()
                    if (decoded == null) {
                        missingUris.put(cacheKey, true)
                    } else {
                        missingUris.remove(cacheKey)
                        bitmaps.put(cacheKey, decoded)
                        runCatching { imageBitmaps.put(cacheKey, decoded.asImageBitmap()) }
                        if (notify) AppAndIntentLauncher.notifyCacheUpdated()
                    }
                    decoded
                }
            }
        }
    }

    suspend fun prewarm(context: Context, uris: Collection<String>, revisions: Map<String, Long>) {
        val pending = uris.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .filter { uri ->
                val rev = revisions[uri] ?: 0L
                val key = cacheKey(uri, rev)
                bitmaps.get(key) == null && missingUris.get(key) != true
            }
            .toList()
        if (pending.isEmpty()) return
        val appContext = context.applicationContext ?: context
        withContext(Dispatchers.IO) {
            coroutineScope {
                pending.forEach { uri ->
                    launch { load(appContext, uri, revisions[uri] ?: 0L, notify = false) }
                }
            }
        }
        AppAndIntentLauncher.notifyCacheUpdated()
    }
}

@Composable
internal fun rememberUserIcon(context: Context, uri: String): Bitmap? {
    val trimmedUri = uri.trim()
    if (trimmedUri.isEmpty()) return null
    val revisions by remember(context) { MinusOneRepository.getInstance(context).imageAccessRevisions }.collectAsState()
    val cacheGen by AppAndIntentLauncher.cacheGeneration.collectAsState()
    val revision = revisions[trimmedUri] ?: 0L
    val cachedIcon = remember(trimmedUri, revision, cacheGen) { UserIconCache.peek(trimmedUri, revision) }
    var loadedIcon by remember(trimmedUri, revision) { mutableStateOf(cachedIcon) }
    LaunchedEffect(context, trimmedUri, revision) {
        if (cachedIcon == null && loadedIcon == null && !UserIconCache.isKnownMissing(trimmedUri, revision)) {
            loadedIcon = UserIconCache.load(context.applicationContext ?: context, trimmedUri, revision, notify = false)
        }
    }
    return cachedIcon ?: loadedIcon
}

@Composable
internal fun rememberUserIconBitmap(context: Context, uri: String): ImageBitmap? {
    val trimmedUri = uri.trim()
    if (trimmedUri.isEmpty()) return null
    val revisions by remember(context) { MinusOneRepository.getInstance(context).imageAccessRevisions }.collectAsState()
    val cacheGen by AppAndIntentLauncher.cacheGeneration.collectAsState()
    val revision = revisions[trimmedUri] ?: 0L
    val cachedBitmap = remember(trimmedUri, revision, cacheGen) {
        UserIconCache.peekImageBitmap(trimmedUri, revision)
    }
    var loadedBitmap by remember(trimmedUri, revision) {
        mutableStateOf(cachedBitmap)
    }
    LaunchedEffect(context, trimmedUri, revision) {
        if (cachedBitmap == null && loadedBitmap == null && !UserIconCache.isKnownMissing(trimmedUri, revision)) {
            val loaded = UserIconCache.load(context.applicationContext ?: context, trimmedUri, revision, notify = false)
            loadedBitmap = UserIconCache.peekImageBitmap(trimmedUri, revision)
                ?: loaded?.let { runCatching { it.asImageBitmap() }.getOrNull() }
        }
    }
    return cachedBitmap ?: loadedBitmap
}
