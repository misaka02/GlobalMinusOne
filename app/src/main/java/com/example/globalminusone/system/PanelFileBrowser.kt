package com.example.globalminusone.system

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.globalminusone.FileAccessActivity
import com.example.globalminusone.data.FileBrowserPolicy
import com.example.globalminusone.data.FileBrowserRoot
import com.example.globalminusone.data.FileBrowserSortOrder
import com.example.globalminusone.service.OverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/** Only collapse /sdcard when Android supplies this process's emulated public-storage root. */
private val emulatedPublicRootPattern = Regex("/storage/emulated/[0-9]+")
internal fun normalizePublicStoragePath(path: String, externalRootPath: String?): String {
    val root = externalRootPath?.removeSuffix("/")
    if (root == null || !emulatedPublicRootPattern.matches(root)) return path
    return when {
        path == "/sdcard" -> root
        path.startsWith("/sdcard/") -> root + path.removePrefix("/sdcard")
        else -> path
    }
}

private val processPublicStorageRoot: String? by lazy {
    runCatching { Environment.getExternalStorageDirectory().absolutePath }.getOrNull()
}

data class BrowserLocation(
    val title: String,
    val rootUri: String? = null,
    val documentId: String? = null,
    val path: String? = null,
    val singleDocumentUri: String? = null,
    val rootAccess: Boolean = false
) {
    val key: String get() = if (rootAccess) "root:$path" else path?.let {
        if (it == "/sdcard" || it.startsWith("/sdcard/")) normalizePublicStoragePath(it, processPublicStorageRoot) else it
    } ?: singleDocumentUri ?: "$rootUri|$documentId"
}

data class BrowserEntry(
    val location: BrowserLocation,
    val name: String,
    val isDirectory: Boolean,
    val mimeType: String,
    val size: Long?,
    val modified: Long?,
    val blockedReason: String? = null,
    val parentLocation: BrowserLocation? = null
)

fun sortBrowserEntries(
    entries: List<BrowserEntry>,
    order: FileBrowserSortOrder,
    reverse: Boolean = false
): List<BrowserEntry> {
    val names = compareBy<BrowserEntry> { it.name.lowercase(Locale.ROOT) }
        .thenBy { it.name }.thenBy { it.location.key }
    val valueOrder = when (order) {
        FileBrowserSortOrder.NAME -> if (reverse) names.reversed() else names
        FileBrowserSortOrder.MODIFIED -> if (reverse) {
            compareBy<BrowserEntry> { it.modified == null }
                .thenBy { it.modified ?: Long.MAX_VALUE }
                .then(names)
        } else {
            compareByDescending<BrowserEntry> { it.modified ?: -1L }.then(names)
        }
        FileBrowserSortOrder.SIZE -> if (reverse) {
            compareBy<BrowserEntry> { it.size == null }
                .thenBy { it.size ?: Long.MAX_VALUE }
                .then(names)
        } else {
            compareByDescending<BrowserEntry> { it.size ?: -1L }.then(names)
        }
    }
    return entries.sortedWith(compareByDescending<BrowserEntry> { it.isDirectory }.then(valueOrder))
}

sealed interface BrowserPreview {
    data class Text(val content: String, val truncated: Boolean, val encoding: String) : BrowserPreview
    data class Image(val bitmap: Bitmap) : BrowserPreview
    data class Pdf(val entry: BrowserEntry, val pages: Int, val cacheFile: File) : BrowserPreview
    data class Media(val uri: Uri, val video: Boolean) : BrowserPreview
    data class Summary(val content: String) : BrowserPreview
    data object Other : BrowserPreview
}

/** Small process-local bridge between the overlay, permission activity and physical back key. */
object PanelFileBrowser {
    data class Event(val id: Long, val message: String)
    private val sequence = AtomicLong()
    private val _events = MutableStateFlow<Event?>(null)
    val events = _events.asStateFlow()
    private val _returnToBrowser = MutableStateFlow(false)
    val returnToBrowser = _returnToBrowser.asStateFlow()
    @Volatile private var backHandler: (() -> Unit)? = null

    fun installBackHandler(handler: () -> Unit): () -> Unit {
        backHandler = handler
        return { if (backHandler === handler) backHandler = null }
    }

    fun handleBack(): Boolean {
        val handler = backHandler ?: return false
        handler()
        return true
    }

    fun consumeReturnToBrowser() { _returnToBrowser.value = false }

    fun completeAccess(context: Context, message: String, returnToBrowser: Boolean = true) {
        _events.value = Event(sequence.incrementAndGet(), message)
        _returnToBrowser.value = returnToBrowser
        OverlayService.start(context, OverlayService.ACTION_OPEN_PANEL)
    }

    fun requestDirectory(context: Context, replaceUri: String? = null) = launchAccess(
        context, FileAccessActivity.ACTION_ADD_DIRECTORY, replaceUri
    )

    fun requestComponentIcon(context: Context, pageId: String, componentId: String) = requestIcon(context, pageId, componentId, null)
    fun requestAppIcon(context: Context, packageName: String) = requestIcon(context, null, null, packageName)
    private fun requestIcon(context: Context, pageId: String?, componentId: String?, packageName: String?) {
        try {
            context.startActivity(Intent(context, FileAccessActivity::class.java).apply {
                action = if (packageName == null) FileAccessActivity.ACTION_PICK_COMPONENT_ICON else FileAccessActivity.ACTION_PICK_APP_ICON
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(FileAccessActivity.EXTRA_PAGE_ID, pageId)
                putExtra(FileAccessActivity.EXTRA_COMPONENT_ID, componentId)
                putExtra(FileAccessActivity.EXTRA_ICON_PACKAGE, packageName)
            })
        } catch (_: Exception) {
            android.widget.Toast.makeText(context, "无法打开图标图片选择器", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun requestFile(context: Context, pageId: String, componentId: String? = null, targetContainerId: String? = null, beforeId: String? = null) = requestDocument(context, pageId, componentId, false, targetContainerId, beforeId)

    fun requestImage(context: Context, pageId: String, componentId: String? = null, targetContainerId: String? = null, beforeId: String? = null) = requestDocument(context, pageId, componentId, true, targetContainerId, beforeId)

    private fun requestDocument(context: Context, pageId: String, componentId: String?, image: Boolean, targetContainerId: String? = null, beforeId: String? = null) {
        try {
            context.startActivity(Intent(context, FileAccessActivity::class.java).apply {
                action = if (image) FileAccessActivity.ACTION_PICK_IMAGE else FileAccessActivity.ACTION_PICK_FILE
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(FileAccessActivity.EXTRA_PAGE_ID, pageId)
                putExtra(FileAccessActivity.EXTRA_COMPONENT_ID, componentId)
                putExtra("container_id", targetContainerId)
                putExtra("before_component_id", beforeId)
            })
        } catch (_: Exception) {
            android.widget.Toast.makeText(context, "无法打开系统文件选择器", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun requestWritableDirectory(context: Context, replaceUri: String? = null) = launchAccess(
        context, FileAccessActivity.ACTION_ADD_WRITABLE_DIRECTORY, replaceUri
    )

    fun requestAllFiles(context: Context) = launchAccess(context, FileAccessActivity.ACTION_ALL_FILES, null)

    private fun launchAccess(context: Context, action: String, replaceUri: String?) {
        try {
            context.startActivity(Intent(context, FileAccessActivity::class.java).apply {
                this.action = action
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(FileAccessActivity.EXTRA_REPLACE_URI, replaceUri)
            })
        } catch (_: Exception) {
            _events.value = Event(sequence.incrementAndGet(), "无法打开目录授权页面")
        }
    }
}

/** Process-only metadata cache: no file contents or grants are persisted here. */
private object DirectoryMemoryCache {
    private const val MAX_DIRECTORIES = 24
    private const val MAX_ENTRIES = 6000
    private data class Snapshot(val rows: List<BrowserEntry>, val loadedAt: Long)
    private val entries = LinkedHashMap<String, Snapshot>(16, 0.75f, true)
    private val cacheHits = AtomicLong()
    private val directoryScans = AtomicLong()
    private val providerQueries = AtomicLong()
    private var permissions: String? = null

    @Synchronized fun validate(fingerprint: String) {
        if (permissions != fingerprint) { entries.clear(); permissions = fingerprint }
    }
    @Synchronized fun get(key: String): List<BrowserEntry>? {
        val cached = entries[key] ?: return null
        cacheHits.incrementAndGet()
        android.util.Log.d("PanelFileBrowser", "directory cache hit; ${stats()}")
        return cached.rows
    }
    @Synchronized fun isFresh(key: String): Boolean = entries[key]?.let {
        android.os.SystemClock.elapsedRealtime() - it.loadedAt < 10_000L
    } ?: false
    fun scanStarted() { directoryScans.incrementAndGet() }
    fun queryStarted() { providerQueries.incrementAndGet() }
    fun stats() = "cacheHits=${cacheHits.get()}, directoryScans=${directoryScans.get()}, providerQueries=${providerQueries.get()}"
    @Synchronized fun clear() { entries.clear() }
    @Synchronized fun remove(key: String) { entries.remove(key) }
    @Synchronized fun expire(key: String) { entries[key]?.let { entries[key] = it.copy(loadedAt = -10_000L) } }
    @Synchronized fun put(key: String, value: List<BrowserEntry>) {
        entries.remove(key)
        if (value.size > MAX_ENTRIES) return
        entries[key] = Snapshot(value.toList(), android.os.SystemClock.elapsedRealtime())
        while (entries.size > MAX_DIRECTORIES || entries.values.sumOf { it.rows.size } > MAX_ENTRIES) {
            entries.remove(entries.keys.first())
        }
    }
}

class FileBrowserIo(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    @Suppress("DEPRECATION")
    private val sharedRoot: File get() = Environment.getExternalStorageDirectory()

    fun hasAllFilesAccess(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        Environment.isExternalStorageManager()

    private fun validateDirectoryAccess(location: BrowserLocation) {
        val grants = resolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri.toString() }.sorted()
        DirectoryMemoryCache.validate("${hasAllFilesAccess()}|${grants.joinToString("|")}|${UserRootSession.status.value}")
        if (location.rootAccess) RootFileIo.checkSession()
        else if (location.path != null) checkedFile(location)
        else {
            val root = checkedTree(requireNotNull(location.rootUri))
            check(root.toString() in grants) { "目录读取权限已失效，请返回入口重新授权" }
            requireNotNull(location.documentId)
        }
    }

    /** Access is checked even for a cache hit, so revoked grants never expose old rows. */
    fun checkDirectoryAccess(location: BrowserLocation) = validateDirectoryAccess(location)

    fun cachedDirectory(location: BrowserLocation): List<BrowserEntry>? {
        validateDirectoryAccess(location)
        return DirectoryMemoryCache.get(location.key)
    }

    fun isDirectoryCacheFresh(location: BrowserLocation): Boolean = DirectoryMemoryCache.isFresh(location.key)

    fun directoryCacheStats(): String = DirectoryMemoryCache.stats()

    fun invalidateDirectory(location: BrowserLocation) = DirectoryMemoryCache.remove(location.key)
    fun expireDirectory(location: BrowserLocation) = DirectoryMemoryCache.expire(location.key)
    fun invalidateAllDirectories() = DirectoryMemoryCache.clear()

    fun sharedLocation() = BrowserLocation("手机共享存储", path = sharedRoot.absolutePath)

    fun sharedSubdirectoryLocation(folderName: String, displayTitle: String = folderName): BrowserLocation? {
        if (!hasAllFilesAccess()) return null
        val target = File(sharedRoot, folderName)
        if (!FileBrowserPolicy.allowsSharedPath(sharedRoot, target)) return null
        if (!target.exists() || !target.isDirectory) return null
        return BrowserLocation(displayTitle, path = target.absolutePath)
    }

    fun rootLocation(root: FileBrowserRoot): BrowserLocation {
        val uri = checkedTree(root.uri)
        return BrowserLocation(root.title, root.uri, DocumentsContract.getTreeDocumentId(uri))
    }

    fun hasReadGrant(uri: String): Boolean = try {
        val parsed = checkedTree(uri)
        resolver.persistedUriPermissions.any { it.uri == parsed && it.isReadPermission }
    } catch (_: Exception) { false }

    private fun checkedTree(value: String): Uri {
        val uri = Uri.parse(value)
        require(uri.scheme == "content" && DocumentsContract.isTreeUri(uri)) { "目录书签无效，请重新授权" }
        return uri
    }

    private fun checkedFile(location: BrowserLocation): File {
        check(hasAllFilesAccess()) { "尚未授权手机共享存储，请返回入口授权，或添加单独目录" }
        val file = File(requireNotNull(location.path)).canonicalFile
        check(FileBrowserPolicy.allowsSharedPath(sharedRoot, file)) { "无法访问共享存储范围以外或 Android/data、Android/obb 中的文件" }
        return file
    }

    /** A persisted document grant is distinct from a tree grant. */
    fun singleDocumentEntry(uri: String, title: String): BrowserEntry = BrowserEntry(
        BrowserLocation(title, singleDocumentUri = uri), title, false,
        resolver.getType(Uri.parse(uri))?.takeUnless { it == "application/octet-stream" } ?: mimeFor(title), null, null
    )

    private fun documentUri(location: BrowserLocation): Uri {
        location.singleDocumentUri?.let { value ->
            val uri = Uri.parse(value)
            check(uri.scheme == "content" && resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }) {
                "文件读取权限已失效，请重新选择文件"
            }
            return uri
        }
        val root = requireNotNull(location.rootUri)
        check(hasReadGrant(root)) { "目录读取权限已失效，请返回入口重新授权" }
        return DocumentsContract.buildDocumentUriUsingTree(checkedTree(root), requireNotNull(location.documentId))
    }

    suspend fun list(location: BrowserLocation): List<BrowserEntry> = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        validateDirectoryAccess(location)
        DirectoryMemoryCache.scanStarted()
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val loaded = if (location.rootAccess) RootFileIo.list(location) else if (location.path != null) {
            val directory = checkedFile(location)
            check(directory.isDirectory) { "目录不存在或已被移走" }
            val children = directory.listFiles() ?: throw IOException("无法读取目录，请检查权限或刷新后重试")
            children.map { file ->
                currentCoroutineContext().ensureActive()
                val allowed = FileBrowserPolicy.allowsSharedPath(sharedRoot, file)
                val directoryEntry = !allowed || file.isDirectory
                BrowserEntry(
                    BrowserLocation(file.name, path = file.absolutePath), file.name, directoryEntry,
                    if (directoryEntry) DocumentsContract.Document.MIME_TYPE_DIR else mimeFor(file.name),
                    if (directoryEntry) null else file.length(), if (allowed) file.lastModified().takeIf { it > 0 } else null,
                    if (allowed) null else "受保护目录或超出共享存储范围，无法访问"
                )
            }
        } else {
            val directoryUri = documentUri(location)
            // This query also verifies that a saved location is still a readable directory.
            DirectoryMemoryCache.queryStarted()
            resolver.query(directoryUri, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use {
                check(it.moveToFirst() && it.getString(0) == DocumentsContract.Document.MIME_TYPE_DIR) { "目录不存在或已被移走" }
            } ?: throw IOException("无法读取目录信息")
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                checkedTree(requireNotNull(location.rootUri)), requireNotNull(location.documentId)
            )
            val columns = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )
            val entries = mutableListOf<BrowserEntry>()
            DirectoryMemoryCache.queryStarted()
            resolver.query(childrenUri, columns, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: "未命名"
                    val reportedMime = cursor.getString(2)
                    val mime = if (reportedMime.isNullOrBlank() || reportedMime == "application/octet-stream") mimeFor(name) else reportedMime
                    entries += BrowserEntry(
                        BrowserLocation(name, location.rootUri, id), name,
                        mime == DocumentsContract.Document.MIME_TYPE_DIR, mime,
                        if (cursor.isNull(3)) null else cursor.getLong(3).takeIf { it >= 0 },
                        if (cursor.isNull(4)) null else cursor.getLong(4).takeIf { it > 0 }
                    )
                }
            } ?: throw IOException("无法读取目录，请检查权限或刷新后重试")
            entries
        }
        currentCoroutineContext().ensureActive()
        validateDirectoryAccess(location)
        DirectoryMemoryCache.put(location.key, loaded)
        android.util.Log.d("PanelFileBrowser", "directory scan complete rows=${loaded.size}, elapsedMs=${android.os.SystemClock.elapsedRealtime() - startedAt}; ${DirectoryMemoryCache.stats()}")
        loaded
    }

    private suspend fun openStream(entry: BrowserEntry): InputStream = if (entry.location.rootAccess) {
        RootFileIo.read(entry.location, 32 * 1024 * 1024).inputStream()
    } else if (entry.location.path != null) {
        checkedFile(entry.location).inputStream()
    } else {
        resolver.openInputStream(documentUri(entry.location)) ?: throw IOException("无法打开文件")
    }

    suspend fun preview(entry: BrowserEntry, onPdfCacheCreated: (File) -> Unit): BrowserPreview = withContext(Dispatchers.IO) {
        check(!entry.isDirectory && entry.blockedReason == null) { entry.blockedReason ?: "无法预览目录" }
        currentCoroutineContext().ensureActive()
        if (entry.location.rootAccess && (entry.mimeType.startsWith("audio/") || entry.mimeType.startsWith("video/")))
            return@withContext BrowserPreview.Summary("root媒体流暂不支持内嵌播放；未导出受保护文件到其它应用。普通SAF/共享媒体播放仍可用。")
        val kind = FilePreviewFormats.kind(entry.name, entry.mimeType)
        if (kind == "pdf") {
            return@withContext withPreviewFile(entry, keepFile = true, onCreated = onPdfCacheCreated) { file ->
                android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    android.graphics.pdf.PdfRenderer(descriptor).use {
                        check(it.pageCount > 0) { "PDF 没有可预览页面，请使用其他应用打开" }
                        BrowserPreview.Pdf(entry, it.pageCount, file)
                    }
                }
            }
        }
        if (kind == "audio" || kind == "video") {
            val uri = if (entry.location.path != null) androidx.core.content.FileProvider.getUriForFile(
                appContext, appContext.packageName + ".files", checkedFile(entry.location)
            ) else documentUri(entry.location)
            return@withContext BrowserPreview.Media(uri, kind == "video")
        }
        if (kind in setOf("zip", "docx", "xlsx", "pptx")) {
            return@withContext withPreviewFile(entry) { BrowserPreview.Summary(FilePreviewFormats.summary(it, kind)) }
        }
        check(kind != "svg") { "SVG 矢量图片请使用其他应用打开" }
        if (entry.mimeType.startsWith("image/") || kind in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")) {
            check(entry.size == null || entry.size <= FileBrowserPolicy.IMAGE_INPUT_BYTES) {
                "图片超过 32 MB 预览上限，请使用其他应用打开"
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openStream(entry).use { BitmapFactory.decodeStream(LimitedImageStream(it), null, bounds) }
            check(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法识别此图片，请使用其他应用打开" }
            currentCoroutineContext().ensureActive()
            val options = BitmapFactory.Options().apply {
                inSampleSize = FileBrowserPolicy.imageSampleSize(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = openStream(entry).use { BitmapFactory.decodeStream(LimitedImageStream(it), null, options) }
                ?: throw IOException("图片解码失败，请使用其他应用打开")
            currentCoroutineContext().ensureActive()
            BrowserPreview.Image(bitmap)
        } else if (isText(entry)) {
            val limit = FileBrowserPolicy.TEXT_PREVIEW_BYTES
            val bytes = openStream(entry).use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= limit) {
                    currentCoroutineContext().ensureActive()
                    val count = stream.read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
                    if (count < 0) break
                    if (count > 0) output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val truncated = bytes.size > limit
            val contentBytes = if (truncated) bytes.copyOf(limit) else bytes
            val (text, encoding) = FilePreviewFormats.decodeText(contentBytes, truncated)
            BrowserPreview.Text(text, truncated, encoding)
        } else BrowserPreview.Other
    }

    /** PDF copies are registered with the caller before any cancellable work; other copies are operation-local. */
    private suspend fun <T> withPreviewFile(
        entry: BrowserEntry, keepFile: Boolean = false, onCreated: (File) -> Unit = {}, block: (File) -> T
    ): T {
        check(entry.size == null || entry.size <= FilePreviewFormats.DOCUMENT_BYTES) { "文件超过 64 MB 预览上限，请使用其他应用打开" }
        val file = File.createTempFile("file-preview-", ".tmp", appContext.cacheDir)
        var success = false
        try {
            onCreated(file)
            openStream(entry).use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(32768)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= FilePreviewFormats.DOCUMENT_BYTES) { "文件超过 64 MB 预览上限，请使用其他应用打开" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            return block(file).also { success = true }
        } finally { if (!keepFile || !success) file.delete() }
    }

    suspend fun renderPdfPage(preview: BrowserPreview.Pdf, index: Int): Bitmap = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        run {
            val file = preview.cacheFile
            android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                    renderer.openPage(index).use { page ->
                        val scale = minOf(1.5, 1600.0 / maxOf(page.width, page.height),
                            kotlin.math.sqrt(2_000_000.0 / (page.width.toDouble() * page.height)))
                        val bitmap = Bitmap.createBitmap(maxOf(1, (page.width * scale).toInt()), maxOf(1, (page.height * scale).toInt()), Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        try { page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                        catch (e: Exception) { bitmap.recycle(); throw e }
                        bitmap
                    }
                }
            }
        }
    }

    suspend fun launchExternal(entry: BrowserEntry, share: Boolean): String? = withContext(Dispatchers.IO) {
        if (entry.location.rootAccess) return@withContext "root受保护文件不直接导出到其它应用；当前仅支持本窗有界预览"
        try {
            check(entry.blockedReason == null) { entry.blockedReason ?: "无法访问文件" }
            val uri = if (entry.location.path != null) {
                FileProvider.getUriForFile(appContext, appContext.packageName + ".files", checkedFile(entry.location))
            } else documentUri(entry.location)
            val mime = entry.mimeType.ifBlank { "application/octet-stream" }
            val intent = if (share) {
                Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri(entry.name, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }.let { Intent.createChooser(it, "分享文件") }
            } else {
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    clipData = ClipData.newRawUri(entry.name, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            withContext(Dispatchers.Main) {
                OverlayService.start(appContext, OverlayService.ACTION_CLOSE_PANEL)
                appContext.startActivity(intent)
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { OverlayService.start(appContext, OverlayService.ACTION_OPEN_PANEL) }
            if (e is android.content.ActivityNotFoundException) "没有可打开此文件的应用" else e.message ?: "无法打开文件"
        }
    }

    private class LimitedImageStream(input: InputStream) : FilterInputStream(input) {
        private var consumed = 0L
        override fun read(): Int {
            val value = super.read()
            if (value >= 0) checkCount(1)
            return value
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = `in`.read(buffer, offset, length)
            if (count > 0) checkCount(count)
            return count
        }
        override fun skip(count: Long): Long {
            val skipped = `in`.skip(count)
            checkCount(skipped)
            return skipped
        }
        private fun checkCount(count: Number) {
            consumed += count.toLong()
            if (consumed > FileBrowserPolicy.IMAGE_INPUT_BYTES) throw IOException("图片超过 32 MB 预览上限")
        }
    }

    private fun isText(entry: BrowserEntry): Boolean = entry.mimeType.startsWith("text/") ||
        entry.mimeType in setOf("application/json", "application/xml", "application/javascript", "application/x-sh") ||
        entry.name.substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf(
            "txt", "md", "log", "json", "xml", "csv", "tsv", "yaml", "yml", "ini", "conf", "cfg",
            "kt", "java", "js", "ts", "css", "html", "htm", "py", "sh", "c", "cpp", "h", "sql", "toml", "properties"
        )

    private fun mimeFor(name: String): String = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase(Locale.ROOT)) ?: "application/octet-stream"
}
