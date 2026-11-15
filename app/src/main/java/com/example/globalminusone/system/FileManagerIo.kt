package com.example.globalminusone.system

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import com.example.globalminusone.data.FileBrowserBookmark
import com.example.globalminusone.data.FileBrowserPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.ArrayDeque
import java.util.Locale

fun BrowserLocation.bookmark(directory: Boolean = true, mime: String = "application/octet-stream") =
    FileBrowserBookmark(title, rootUri, documentId, path, singleDocumentUri, directory, mime, rootAccess)
fun FileBrowserBookmark.location() = BrowserLocation(title, rootUri, documentId, path, singleDocumentUri, rootAccess)
fun FileBrowserBookmark.entry() = BrowserEntry(location(), title, directory, mimeType, null, null)

enum class FileCategory(val label: String) {
    ALL("全部"), DIRECTORY("目录"), IMAGE("图片"), AUDIO("音频"), VIDEO("视频"), DOCUMENT("文档"), ARCHIVE("压缩包"), OTHER("其它");
    companion object {
        fun of(entry: BrowserEntry): FileCategory {
            if (entry.isDirectory) return DIRECTORY
            val extension = entry.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            return when {
                entry.mimeType.startsWith("image/") || extension in setOf("jpg","jpeg","png","gif","webp","heic","heif","bmp","svg") -> IMAGE
                entry.mimeType.startsWith("audio/") || extension in setOf("mp3","wav","aac","m4a","ogg","flac","opus") -> AUDIO
                entry.mimeType.startsWith("video/") || extension in setOf("mp4","mkv","webm","mov","avi","3gp") -> VIDEO
                extension in setOf("zip","rar","7z","tar","gz","bz2","xz") -> ARCHIVE
                entry.mimeType.startsWith("text/") || extension in setOf("pdf","txt","md","json","csv","xml","html","htm","doc","docx","xls","xlsx","ppt","pptx","epub","rtf","log") -> DOCUMENT
                else -> OTHER
            }
        }
    }
}
data class FileCategoryUsage(val files: Long = 0, val knownBytes: Long = 0, val unknownSizes: Long = 0)
data class FileScanResult(
    val groups: Map<FileCategory, FileCategoryUsage>,
    val directories: Long,
    val completedAt: Long,
    val inaccessibleDirectories: Long = 0,
    val largeFiles: List<BrowserEntry> = emptyList(),
    val apkFiles: List<BrowserEntry> = emptyList(),
    val emptyDirectories: List<BrowserLocation> = emptyList(),
    val categoryFiles: Map<FileCategory, List<BrowserEntry>> = emptyMap()
) {
    val knownBytes get() = groups.values.sumOf { it.knownBytes }
    val files get() = groups.values.sumOf { it.files }
    val unknownSizes get() = groups.values.sumOf { it.unknownSizes }
}
data class DuplicateFileGroup(
    val sizeBytes: Long,
    val sha256: String,
    val entries: List<BrowserEntry>
) {
    val wastedBytes: Long get() = if (entries.size <= 1) 0L else sizeBytes * (entries.size - 1)
}
data class FileSearchCursor internal constructor(
    internal val rootKey: String, internal val permissions: String, internal val query: String, internal val category: FileCategory,
    internal val pending: List<BrowserLocation>, internal val seen: Set<String>, internal val rows: List<BrowserEntry>,
    internal val index: Int, internal val visited: Int, internal val directories: Int, val skippedDirectories: Int,
    internal val includeSubdirectories: Boolean = true, internal val regex: Boolean = false
)
data class FileSearchResult(val entries: List<BrowserEntry>, val continuation: FileSearchCursor?, val skippedDirectories: Int,
    val limitReason: String? = null) {
    val limited get() = continuation != null
}
data class FileScanProgress(val visited: Int, val directories: Int, val current: String)
data class StorageVolumeInfo(val title: String, val removable: Boolean, val total: Long?, val available: Long?)
data class FileProperties(val title: String, val type: String, val size: Long?, val modified: Long?, val writable: Boolean, val location: String)

/** All mutations resolve and verify the real grant at operation time, never from a saved UI label. */
class FileManagerIo(context: Context, private val browser: FileBrowserIo) {
    private val app = context.applicationContext
    private val resolver = app.contentResolver
    private val textRecovery = SafTextRecoveryStore(File(app.filesDir, "text-save-recovery"))
    private data class ScanCache(val permissions: String, val value: FileScanResult)
    private data class DuplicateCache(val permissions: String, val value: List<DuplicateFileGroup>)
    companion object {
        private const val MAX_CACHED_ROOTS = 12
        private const val MAX_CATEGORY_DETAIL_FILES = 500
        private const val MAX_LARGE_FILES = 100
        private const val MAX_APK_FILES = 100
        private const val MAX_EMPTY_DIRS = 100
        private val scans = LinkedHashMap<String, ScanCache>(MAX_CACHED_ROOTS, 0.75f, true)
        private val duplicates = LinkedHashMap<String, DuplicateCache>(MAX_CACHED_ROOTS, 0.75f, true)
    }
    fun permissionKey(): String = "${browser.hasAllFilesAccess()}|${UserRootSession.status.value}|" + resolver.persistedUriPermissions
        .map { "${it.uri}:${it.isReadPermission}:${it.isWritePermission}" }.sorted().joinToString("|")
    private fun checkedFile(location: BrowserLocation): File {
        check(browser.hasAllFilesAccess()) { "未获共享存储管理权限；请使用目录授权或明确授权共享存储" }
        @Suppress("DEPRECATION") val root = Environment.getExternalStorageDirectory()
        val file = File(requireNotNull(location.path)).canonicalFile
        check(FileBrowserPolicy.allowsSharedPath(root, file)) { "无法管理共享存储范围以外或Android/data、Android/obb中的文件" }
        return file
    }
    private fun uri(location: BrowserLocation, write: Boolean = false): Uri {
        val root = Uri.parse(location.rootUri ?: location.singleDocumentUri ?: error("文件位置无效"))
        check(root.scheme == "content") { "文件位置无效" }
        val grant = resolver.persistedUriPermissions.firstOrNull { it.uri == root && it.isReadPermission }
        check(grant != null) { "读取权限已失效，请重新授权" }
        check(!write || grant.isWritePermission) { "此位置为只读授权，请先选择「添加可管理目录」授予写入权限" }
        return if (location.singleDocumentUri != null) root else DocumentsContract.buildDocumentUriUsingTree(root, requireNotNull(location.documentId))
    }
    fun canWrite(location: BrowserLocation): Boolean = runCatching {
        if (location.rootAccess) RootWritePolicy.canWrite(RootFileIo.path(location)) else if (location.path != null) checkedFile(location).canWrite() else { uri(location, true); true }
    }.getOrDefault(false)
    fun checkRead(location: BrowserLocation) { if (location.rootAccess) RootFileIo.checkSession() else if (location.path != null) checkedFile(location) else uri(location) }
    fun clearScanCache() {
        synchronized(scans) { scans.clear() }
        synchronized(duplicates) { duplicates.clear() }
    }
    fun cachedScan(location: BrowserLocation): FileScanResult? {
        checkRead(location)
        val permissions = permissionKey()
        return synchronized(scans) {
            val cached = scans[location.key]
            if (cached == null || cached.permissions != permissions) {
                scans.remove(location.key); null
            } else cached.value
        }
    }
    fun cachedDuplicates(location: BrowserLocation): List<DuplicateFileGroup>? {
        checkRead(location)
        val permissions = permissionKey()
        return synchronized(duplicates) {
            val cached = duplicates[location.key]
            if (cached == null || cached.permissions != permissions) {
                duplicates.remove(location.key); null
            } else cached.value
        }
    }
    suspend fun volumes(): List<StorageVolumeInfo> = withContext(Dispatchers.IO) {
        val manager = app.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        manager.storageVolumes.map { volume ->
            val directory = if (Build.VERSION.SDK_INT >= 30) volume.directory else if (volume.isPrimary) {
                @Suppress("DEPRECATION") Environment.getExternalStorageDirectory()
            } else null
            val stat = directory?.let { runCatching { StatFs(it.absolutePath) }.getOrNull() }
            StorageVolumeInfo(volume.getDescription(app), volume.isRemovable, stat?.totalBytes, stat?.availableBytes)
        }
    }
    suspend fun properties(entry: BrowserEntry): FileProperties = withContext(Dispatchers.IO) {
        if (entry.location.rootAccess) {
            RootFileIo.checkSession()
            return@withContext FileProperties(entry.name, entry.mimeType, entry.size, entry.modified, canWrite(entry.location),
                "root:${entry.location.path}（逐文件解锁/显式目录范围；不改挂载或SELinux）")
        }
        if (entry.location.path != null) {
            val file = checkedFile(entry.location)
            check(file.exists()) { "文件不存在" }
            FileProperties(file.name, if (file.isDirectory) "目录" else entry.mimeType, if (file.isDirectory) null else file.length(), file.lastModified().takeIf { it > 0 }, file.canWrite(), file.path)
        } else {
            val document = uri(entry.location)
            val cols = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_FLAGS)
            resolver.query(document, cols, null, null, null)?.use {
                check(it.moveToFirst()) { "文件不存在或无法读取" }
                val mime = it.getString(1) ?: entry.mimeType
                val flags = it.getInt(4)
                FileProperties(it.getString(0) ?: entry.name, mime,
                    if (it.isNull(2) || mime == DocumentsContract.Document.MIME_TYPE_DIR) null else it.getLong(2).takeIf { size -> size >= 0 },
                    if (it.isNull(3)) null else it.getLong(3).takeIf { time -> time > 0 },
                    canWrite(entry.location) && flags and (DocumentsContract.Document.FLAG_SUPPORTS_WRITE or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE or DocumentsContract.Document.FLAG_SUPPORTS_DELETE or DocumentsContract.Document.FLAG_SUPPORTS_RENAME) != 0,
                    document.toString())
            } ?: error("无法读取文件属性")
        }
    }
    suspend fun scan(location: BrowserLocation, force: Boolean = false, onProgress: suspend (FileScanProgress) -> Unit): FileScanResult = withContext(Dispatchers.IO) {
        if (!force) cachedScan(location)?.let { return@withContext it }
        else synchronized(duplicates) { duplicates.remove(location.key) }
        val permissions = permissionKey()
        val queue = ArrayDeque<BrowserLocation>(); queue.add(location)
        val seen = HashSet<String>(); val groups = mutableMapOf<FileCategory, FileCategoryUsage>()
        val categoryBuckets = java.util.EnumMap<FileCategory, ArrayList<BrowserEntry>>(FileCategory::class.java)
        val largeFiles = ArrayList<BrowserEntry>()
        val apkFiles = ArrayList<BrowserEntry>()
        val emptyDirs = ArrayList<BrowserLocation>()
        var visited = 0; var directories = 0; var inaccessible = 0L
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            check(permissionKey() == permissions) { "权限发生变化，本次统计已废弃，请重新扫描" }
            val folder = queue.removeFirst()
            // A provider or a symbolic link can expose the same directory twice.
            if (!seen.add(folder.key)) { inaccessible++; continue }
            directories++
            onProgress(FileScanProgress(visited, directories, folder.title))
            var readFailed = false
            val children = try { browser.list(folder) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                checkRead(location)
                check(permissionKey() == permissions) { "权限变化，统计已废弃" }
                if (folder.key == location.key) throw e
                readFailed = true
                inaccessible++; emptyList()
            }
            if (!readFailed && children.isEmpty() && folder.key != location.key && emptyDirs.size < MAX_EMPTY_DIRS) {
                emptyDirs.add(folder)
            }
            children.forEach { entry ->
                currentCoroutineContext().ensureActive()
                visited++
                if (entry.blockedReason != null) { inaccessible++; return@forEach }
                if (entry.isDirectory) queue.add(entry.location)
                else {
                    val withParent = if (entry.parentLocation == null) entry.copy(parentLocation = folder) else entry
                    val key = FileCategory.of(withParent)
                    groups[key] = (groups[key] ?: FileCategoryUsage()).including(withParent.size)
                    val bucket = categoryBuckets.getOrPut(key) { ArrayList() }
                    bucket.add(withParent)
                    if (bucket.size > MAX_CATEGORY_DETAIL_FILES + 120) {
                        bucket.sortByDescending { it.size ?: -1L }
                        while (bucket.size > MAX_CATEGORY_DETAIL_FILES) bucket.removeAt(bucket.lastIndex)
                    }
                    val knownSize = withParent.size ?: -1L
                    if (knownSize >= 10L * 1024 * 1024) {
                        largeFiles.add(withParent)
                        if (largeFiles.size > MAX_LARGE_FILES + 40) {
                            largeFiles.sortByDescending { it.size ?: 0L }
                            while (largeFiles.size > MAX_LARGE_FILES) largeFiles.removeAt(largeFiles.lastIndex)
                        }
                    }
                    if (withParent.name.endsWith(".apk", ignoreCase = true)) {
                        apkFiles.add(withParent)
                        if (apkFiles.size > MAX_APK_FILES + 40) {
                            apkFiles.sortByDescending { it.size ?: 0L }
                            while (apkFiles.size > MAX_APK_FILES) apkFiles.removeAt(apkFiles.lastIndex)
                        }
                    }
                }
            }
        }
        currentCoroutineContext().ensureActive()
        checkRead(location)
        check(permissionKey() == permissions) { "权限发生变化，本次统计已废弃" }
        val sortedLarge = sortAnalyzerEntries(largeFiles, AnalyzerSortOrder.SIZE_DESC).take(MAX_LARGE_FILES)
        val sortedApks = sortAnalyzerEntries(apkFiles, AnalyzerSortOrder.SIZE_DESC).take(MAX_APK_FILES)
        val sortedCategoryFiles = categoryBuckets.entries.associate { (cat, list) ->
            cat to sortAnalyzerEntries(list, AnalyzerSortOrder.SIZE_DESC).take(MAX_CATEGORY_DETAIL_FILES)
        }.filterValues { it.isNotEmpty() }
        val result = FileScanResult(
            groups = groups,
            directories = directories.toLong(),
            completedAt = System.currentTimeMillis(),
            inaccessibleDirectories = inaccessible,
            largeFiles = sortedLarge,
            apkFiles = sortedApks,
            emptyDirectories = emptyDirs.toList(),
            categoryFiles = sortedCategoryFiles
        )
        synchronized(scans) {
            scans[location.key] = ScanCache(permissions, result)
            while (scans.size > MAX_CACHED_ROOTS) scans.remove(scans.keys.first())
        }
        result
    }
    /** Calculates MD5, SHA-1, SHA-256 and CRC32 for a single readable file without loading it into memory. */
    suspend fun calculateChecksums(entry: BrowserEntry, onProgress: suspend (Long) -> Unit = {}): FileChecksums = withContext(Dispatchers.IO) {
        check(!entry.isDirectory && entry.blockedReason == null) { entry.blockedReason ?: "目录不支持计算文件哈希" }
        checkRead(entry.location)
        val ctx = currentCoroutineContext()
        val stream = if (entry.location.rootAccess) {
            RootFileIo.read(entry.location, 32 * 1024 * 1024).inputStream()
        } else {
            input(entry.location)
        }
        computeStreamChecksums(
            stream = stream,
            checkCancelled = { ctx.ensureActive() },
            onProgress = { bytes ->
                ctx.ensureActive()
            }
        ).also { onProgress(it.byteCount) }
    }

    /**
     * Snow Leopard inspired duplicate file detector:
     * 1) Groups readable files by exact byte size (> 0)
     * 2) Computes bounded SHA-256 only for same-size buckets with >= 2 files
     */
    suspend fun findDuplicateFiles(
        location: BrowserLocation,
        force: Boolean = false,
        maxFileBytes: Long = 128L * 1024 * 1024,
        onProgress: suspend (FileScanProgress) -> Unit = {}
    ): List<DuplicateFileGroup> = withContext(Dispatchers.IO) {
        if (!force) cachedDuplicates(location)?.let { return@withContext it }
        checkRead(location)
        val permissions = permissionKey()
        val queue = ArrayDeque<BrowserLocation>().apply { add(location) }
        val seen = HashSet<String>()
        val bySize = HashMap<Long, MutableList<BrowserEntry>>()
        var visited = 0
        var directories = 0
        while (queue.isNotEmpty() && visited < 12_000) {
            currentCoroutineContext().ensureActive()
            check(permissionKey() == permissions) { "权限发生变化，重复文件扫描已中止" }
            val folder = queue.removeFirst()
            if (!seen.add(folder.key)) continue
            directories++
            onProgress(FileScanProgress(visited, directories, folder.title))
            val children = try { browser.list(folder) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                checkRead(location)
                if (folder.key == location.key) throw IllegalStateException("无法读取根目录")
                emptyList()
            }
            for (entry in children) {
                currentCoroutineContext().ensureActive()
                visited++
                if (entry.blockedReason != null) continue
                if (entry.name.startsWith('.')) continue
                val normPath = (entry.location.path ?: entry.location.documentId).orEmpty().replace('\\', '/').lowercase(Locale.ROOT)
                if (normPath.contains("/android/data") || normPath.contains("/android/obb") ||
                    normPath.endsWith("primary:android/data") || normPath.endsWith("primary:android/obb")
                ) continue
                if (entry.isDirectory) {
                    queue.add(entry.location)
                } else {
                    val sz = entry.size ?: -1L
                    if (sz in 1..maxFileBytes) {
                        val withParent = if (entry.parentLocation == null) entry.copy(parentLocation = folder) else entry
                        bySize.getOrPut(sz) { ArrayList() }.add(withParent)
                    }
                }
            }
        }
        val candidateBuckets = bySize.entries
            .filter { it.value.size >= 2 }
            .sortedByDescending { it.key * (it.value.size - 1) }
            .take(50)
        val duplicateGroups = ArrayList<DuplicateFileGroup>()
        val ctx = currentCoroutineContext()
        for ((sizeBytes, bucket) in candidateBuckets) {
            ctx.ensureActive()
            check(permissionKey() == permissions) { "权限发生变化，重复文件比对已中止" }
            val byHash = LinkedHashMap<String, MutableList<BrowserEntry>>()
            for (entry in bucket.take(20)) {
                ctx.ensureActive()
                onProgress(FileScanProgress(visited, directories, "校验 ${entry.name}"))
                val sha256 = runCatching {
                    val stream = if (entry.location.rootAccess) {
                        RootFileIo.read(entry.location, 32 * 1024 * 1024).inputStream()
                    } else {
                        input(entry.location)
                    }
                    stream.use { src ->
                        val digest = java.security.MessageDigest.getInstance("SHA-256")
                        val buf = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            ctx.ensureActive()
                            val n = src.read(buf)
                            if (n < 0) break
                            if (n > 0) {
                                total += n
                                check(total <= maxFileBytes) { "超过单文件比对上限" }
                                digest.update(buf, 0, n)
                            }
                        }
                        digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }
                    }
                }.getOrNull() ?: continue
                byHash.getOrPut(sha256) { ArrayList() }.add(entry)
            }
            for ((hash, matches) in byHash) {
                if (matches.size >= 2) {
                    duplicateGroups.add(
                        DuplicateFileGroup(sizeBytes, hash, orderDuplicateEntriesKeepingOriginal(matches))
                    )
                }
            }
        }
        val sorted = duplicateGroups.sortedByDescending { it.wastedBytes }
        synchronized(duplicates) {
            duplicates[location.key] = DuplicateCache(permissions, sorted)
            while (duplicates.size > MAX_CACHED_ROOTS) duplicates.remove(duplicates.keys.first())
        }
        sorted
    }
    /** A page is a display bound only; continuation resumes without re-walking earlier files. */
    suspend fun search(location: BrowserLocation, name: String, category: FileCategory, previous: FileSearchCursor? = null,
        includeSubdirectories: Boolean = true, regex: Boolean = false, onProgress: suspend (FileScanProgress) -> Unit): FileSearchResult = withContext(Dispatchers.IO) {
        searchFileTree(location, name, category, previous, ::permissionKey, ::checkRead, { browser.list(it) }, onProgress, includeSubdirectories, regex)
    }
    private suspend fun create(parent: BrowserLocation, name: String, mime: String): BrowserLocation {
        val checked = validatedFileName(name)
        if (parent.rootAccess) {
            check(mime == DocumentsContract.Document.MIME_TYPE_DIR) { "Root创建文本请用新建文本" }
            return RootFileIo.createDirectory(parent, checked)
        }
        check(browser.list(parent).none { it.name == checked }) { "目标已有同名项目，未覆盖" }
        return if (parent.path != null) {
            val directory = checkedFile(parent); check(directory.canWrite()) { "目标目录不可写" }
            val child = File(directory, checked)
            check(if (mime == DocumentsContract.Document.MIME_TYPE_DIR) child.mkdir() else child.createNewFile()) { "创建失败" }
            BrowserLocation(checked, path = child.absolutePath)
        } else {
            val created = DocumentsContract.createDocument(resolver, uri(parent, true), mime, checked) ?: error("提供方不支持创建此项目")
            BrowserLocation(checked, parent.rootUri, DocumentsContract.getDocumentId(created))
        }
    }
    suspend fun createDirectory(parent: BrowserLocation, name: String) = withContext(Dispatchers.IO) {
        val created = create(parent, name, DocumentsContract.Document.MIME_TYPE_DIR)
        changed(parent); created
    }
    fun resolveEntryParent(entry: BrowserEntry, fallback: BrowserLocation): BrowserLocation {
        entry.parentLocation?.let { return it }
        val path = entry.location.path
        if (path != null && !entry.location.rootAccess) {
            val parentFile = runCatching { File(path).canonicalFile.parentFile }.getOrNull()
            if (parentFile != null) {
                @Suppress("DEPRECATION") val sharedRoot = Environment.getExternalStorageDirectory()
                if (FileBrowserPolicy.allowsSharedPath(sharedRoot, parentFile)) {
                    return BrowserLocation(parentFile.name.ifBlank { fallback.title }, path = parentFile.absolutePath)
                }
            }
        }
        return fallback
    }

    /** Builds a navigable breadcrumb stack from baseStack down to targetParent for 'Open Parent Directory'. */
    fun resolveParentStack(baseStack: List<BrowserLocation>, targetParent: BrowserLocation): List<BrowserLocation> {
        if (baseStack.isEmpty()) return listOf(targetParent)
        val existingIdx = baseStack.indexOfFirst { it.key == targetParent.key }
        if (existingIdx >= 0) return baseStack.take(existingIdx + 1)
        val anchor = baseStack.last()
        if (anchor.path != null && targetParent.path != null && !anchor.rootAccess && !targetParent.rootAccess) {
            val anchorFile = runCatching { File(anchor.path).canonicalFile }.getOrNull()
            val targetFile = runCatching { File(targetParent.path).canonicalFile }.getOrNull()
            if (anchorFile != null && targetFile != null) {
                val anchorPrefix = anchorFile.path.trimEnd(File.separatorChar) + File.separator
                if (targetFile.path.startsWith(anchorPrefix)) {
                    val chain = ArrayList<BrowserLocation>()
                    var cur: File? = targetFile
                    while (cur != null && cur.path != anchorFile.path && cur.path.startsWith(anchorPrefix)) {
                        chain.add(BrowserLocation(cur.name.ifBlank { cur.path }, path = cur.absolutePath))
                        cur = cur.parentFile
                    }
                    chain.reverse()
                    return baseStack + chain
                }
                val rootAnchor = baseStack.first()
                val rootFile = rootAnchor.path?.let { runCatching { File(it).canonicalFile }.getOrNull() }
                if (rootFile != null) {
                    val rootPrefix = rootFile.path.trimEnd(File.separatorChar) + File.separator
                    if (targetFile.path == rootFile.path) return listOf(rootAnchor)
                    if (targetFile.path.startsWith(rootPrefix)) {
                        val chain = ArrayList<BrowserLocation>()
                        var cur: File? = targetFile
                        while (cur != null && cur.path != rootFile.path && cur.path.startsWith(rootPrefix)) {
                            chain.add(BrowserLocation(cur.name.ifBlank { cur.path }, path = cur.absolutePath))
                            cur = cur.parentFile
                        }
                        chain.reverse()
                        return listOf(rootAnchor) + chain
                    }
                }
            }
        }
        if (anchor.rootUri != null && anchor.rootUri == targetParent.rootUri &&
            anchor.documentId != null && targetParent.documentId != null
        ) {
            if (targetParent.documentId.startsWith(anchor.documentId + "/")) {
                val segments = targetParent.documentId.removePrefix(anchor.documentId + "/").split('/').filter { it.isNotEmpty() }
                var acc = anchor.documentId
                val chain = segments.map { seg ->
                    acc = "$acc/$seg"
                    BrowserLocation(seg, anchor.rootUri, acc)
                }
                return baseStack + chain
            }
            return baseStack + targetParent
        }
        return listOf(targetParent)
    }

    private suspend fun performValidatedRename(entry: BrowserEntry, parent: BrowserLocation, name: String): BrowserLocation {
        val checked = validatedFileName(name)
        val actualParent = resolveEntryParent(entry, parent)
        if (entry.location.rootAccess || actualParent.rootAccess) {
            check(entry.location.rootAccess && actualParent.rootAccess) { "不允许跨Root后端重命名" }
            return RootFileIo.rename(entry, actualParent, checked)
        }
        check(browser.list(actualParent).none { it.name == checked && it.location.key != entry.location.key }) { "目标已有同名项目，未覆盖" }
        return if (entry.location.path != null) {
            val file = checkedFile(entry.location); val dest = File(checkedFile(actualParent), checked)
            check(file.renameTo(dest)) { "重命名失败，请检查写入权限" }; BrowserLocation(checked, path = dest.absolutePath)
        } else {
            val renamed = DocumentsContract.renameDocument(resolver, uri(entry.location, true), checked) ?: error("提供方不支持重命名")
            BrowserLocation(checked, entry.location.rootUri, DocumentsContract.getDocumentId(renamed), singleDocumentUri = if (entry.location.singleDocumentUri != null) renamed.toString() else null)
        }
    }

    suspend fun rename(entry: BrowserEntry, parent: BrowserLocation, name: String): BrowserLocation = withContext(Dispatchers.IO) {
        val result = performValidatedRename(entry, parent, name)
        changed(parent); result
    }

    /** Renames an entry from Storage Analyzer and updates cached scan/duplicate results in-place without re-scanning. */
    suspend fun renameKeepingAnalyzerCache(entry: BrowserEntry, fallbackParent: BrowserLocation, name: String): BrowserEntry = withContext(Dispatchers.IO) {
        val actualParent = resolveEntryParent(entry, fallbackParent)
        val renamedLoc = performValidatedRename(entry, actualParent, name)
        browser.invalidateAllDirectories()
        val ext = renamedLoc.title.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val inferredMime = if (entry.isDirectory) entry.mimeType else {
            android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
        val updated = entry.copy(
            location = renamedLoc,
            name = renamedLoc.title,
            mimeType = inferredMime,
            parentLocation = actualParent
        )
        synchronized(scans) {
            for (k in scans.keys.toList()) {
                val c = scans[k] ?: continue
                scans[k] = c.copy(value = c.value.withRenamed(entry, updated))
            }
        }
        synchronized(duplicates) {
            for (k in duplicates.keys.toList()) {
                val c = duplicates[k] ?: continue
                duplicates[k] = c.copy(value = c.value.withRenamed(entry.location.key, updated))
            }
        }
        updated
    }

    private suspend fun deleteOne(entry: BrowserEntry, depth: Int = 0) {
        currentCoroutineContext().ensureActive()
        if (entry.location.rootAccess) { RootFileIo.delete(entry); return }
        check(depth < 64) { "目录嵌套过深，删除已停止" }
        if (entry.location.path != null) {
            val file = checkedFile(entry.location)
            if (file.isDirectory) browser.list(entry.location).forEach { child ->
                check(child.blockedReason == null) { "受保护的子目录不可删除" }; deleteOne(child, depth + 1)
            }
            check(file.delete()) { "删除失败：${entry.name}" }
        } else check(DocumentsContract.deleteDocument(resolver, uri(entry.location, true))) { "提供方拒绝删除：${entry.name}" }
    }
    suspend fun delete(entry: BrowserEntry, parent: BrowserLocation) = withContext(Dispatchers.IO) { deleteOne(entry); changed(parent) }

    /** Deletes an entry from Storage Analyzer and updates cached scan/duplicate results in-place without re-scanning. */
    suspend fun deleteKeepingAnalyzerCache(entry: BrowserEntry) = withContext(Dispatchers.IO) {
        deleteOne(entry)
        browser.invalidateAllDirectories()
        synchronized(scans) {
            for (k in scans.keys.toList()) {
                val c = scans[k] ?: continue
                scans[k] = c.copy(value = c.value.withoutDeleted(listOf(entry)))
            }
        }
        if (!entry.isDirectory) {
            val keySet = setOf(entry.location.key)
            synchronized(duplicates) {
                for (k in duplicates.keys.toList()) {
                    val c = duplicates[k] ?: continue
                    duplicates[k] = c.copy(value = c.value.withoutDeletedKeys(keySet))
                }
            }
        }
    }

    /** Deletes a verified empty directory from Storage Analyzer and updates cached scan stats in-place. */
    suspend fun deleteEmptyDirectoryKeepingAnalyzerCache(dirLocation: BrowserLocation) = withContext(Dispatchers.IO) {
        val children = browser.list(dirLocation)
        check(children.isEmpty()) { "目录「${dirLocation.title}」已不再为空，已跳过删除" }
        val dirEntry = BrowserEntry(dirLocation, dirLocation.title, true, DocumentsContract.Document.MIME_TYPE_DIR, null, null)
        deleteOne(dirEntry)
        browser.invalidateAllDirectories()
        synchronized(scans) {
            for (k in scans.keys.toList()) {
                val c = scans[k] ?: continue
                scans[k] = c.copy(value = c.value.withoutDeleted(emptyList(), listOf(dirLocation)))
            }
        }
    }
    private fun input(location: BrowserLocation): InputStream = if (location.path != null) checkedFile(location).inputStream() else resolver.openInputStream(uri(location)) ?: error("无法读取源文件")
    private fun output(location: BrowserLocation): OutputStream = if (location.path != null) checkedFile(location).outputStream() else resolver.openOutputStream(uri(location, true), "w") ?: error("无法写入目标文件")
    private suspend fun readEditableBytes(location: BrowserLocation): ByteArray = readBoundedEditable(
        if (location.rootAccess) RootFileIo.read(location, MAX_EDIT_TEXT_BYTES + 1).inputStream() else input(location))
    private fun readBoundedEditable(stream: InputStream): ByteArray = stream.use { source ->
        val result = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (result.size() <= MAX_EDIT_TEXT_BYTES) {
            val count = source.read(buffer, 0, minOf(buffer.size, MAX_EDIT_TEXT_BYTES + 1 - result.size()))
            if (count < 0) break
            result.write(buffer, 0, count)
        }
        result.toByteArray()
    }
    suspend fun readText(entry: BrowserEntry): EditableFileText = withContext(Dispatchers.IO) {
        check(supportsTextEditing(entry)) { "此类型不支持纯文本编辑" }
        decodeEditableText(readEditableBytes(entry.location))
    }
    suspend fun createText(parent: BrowserLocation, name: String, text: String): BrowserLocation = withContext(Dispatchers.IO) {
        val bytes = editableTextBytes(text)
        if (parent.rootAccess) { val created = RootFileIo.createText(parent, name, bytes); changed(parent); return@withContext created }
        val created = create(parent, name, "text/plain")
        try {
            output(created).use { it.write(bytes) }
            changed(parent)
            created
        } catch (failure: Exception) {
            withContext(NonCancellable) { runCatching { deleteOne(BrowserEntry(created, created.title, false, "text/plain", null, null)) } }
            throw failure
        }
    }
    suspend fun saveText(entry: BrowserEntry, expectedFingerprint: String, text: String, parent: BrowserLocation? = null) = withContext(Dispatchers.IO) {
        check(supportsTextEditing(entry)) { "此类型不支持纯文本编辑" }
        check(canWrite(entry.location)) { "此文件没有写入授权；请重新选择文件并请求可用写权限，或添加可管理目录" }
        val original = readEditableBytes(entry.location)
        requireUnchangedText(original, expectedFingerprint)
        val bytes = editableTextBytes(text)
        currentCoroutineContext().ensureActive()
        // Once writing starts, finish verification (or retain the recovery original) even if the window is dismissed.
        withContext(NonCancellable) {
            if (entry.location.rootAccess) {
                RootFileIo.write(entry.location, bytes)
            } else if (entry.location.path != null) {
                val file = checkedFile(entry.location)
                val temporary = File.createTempFile(".gmo-text-", ".tmp", file.parentFile)
                try {
                    java.io.FileOutputStream(temporary).use { it.write(bytes); it.fd.sync() }
                    try {
                        java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                        // Keep the original recoverable until the replacement is complete.
                        val backup = File.createTempFile(".gmo-original-", ".tmp", file.parentFile)
                        var keepBackup = false
                        try {
                            java.nio.file.Files.copy(file.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                            try {
                                java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                            } catch (failure: Exception) {
                                try {
                                    java.nio.file.Files.move(backup.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                                } catch (restoreFailure: Exception) {
                                    keepBackup = true
                                    failure.addSuppressed(java.io.IOException("原文保存在 ${backup.absolutePath}，自动恢复失败", restoreFailure))
                                }
                                throw failure
                            }
                        } finally { if (!keepBackup) backup.delete() }
                    }
                } finally { temporary.delete() }
            } else {
                val target = uri(entry.location, true)
                fun replace(value: ByteArray) {
                    resolver.openOutputStream(target, "wt")?.use { it.write(value); it.flush() } ?: error("提供方未提供可写流")
                }
                textRecovery.save(target.toString(), entry.name, original, bytes,
                    { readBoundedEditable(input(entry.location)) }, ::replace)
            }
            // A single-document grant may have no parent location; clear directory rows in either case.
            changed(parent ?: entry.location)
            browser.invalidateDirectory(entry.location)
            clearScanCache()
        }
    }
    suspend fun pendingTextRecovery(entry: BrowserEntry): SafTextRecovery? = withContext(Dispatchers.IO) {
        if (entry.location.path != null || entry.location.rootAccess) null
        else textRecovery.pending(recoveryKey(entry.location))
    }
    // Identifying/exporting an already-private backup must still work after a provider grant is lost.
    private fun recoveryKey(location: BrowserLocation): String {
        val root = Uri.parse(location.rootUri ?: location.singleDocumentUri ?: error("文件位置无效"))
        check(root.scheme == "content")
        return (if (location.singleDocumentUri != null) root else
            DocumentsContract.buildDocumentUriUsingTree(root, requireNotNull(location.documentId))).toString()
    }
    private fun checkedRecovery(entry: BrowserEntry, record: SafTextRecovery) {
        check(!entry.location.rootAccess && entry.location.path == null &&
            recoveryKey(entry.location) == record.targetKey) { "备份不属于当前文件" }
        textRecovery.original(record)
    }
    suspend fun reviewTextRecovery(entry: BrowserEntry, record: SafTextRecovery): SafTextRestoreReview = withContext(Dispatchers.IO) {
        checkedRecovery(entry, record)
        val current = readEditableBytes(entry.location)
        check(current.size <= MAX_EDIT_TEXT_BYTES) { "当前文件已超过编辑上限；未覆盖，可分享原文备份" }
        SafTextRestoreReview(record, textFingerprint(current), current.toString(Charsets.UTF_8).take(400))
    }
    suspend fun restoreTextRecovery(entry: BrowserEntry, review: SafTextRestoreReview, parent: BrowserLocation?) = withContext(Dispatchers.IO) {
        checkedRecovery(entry, review.recovery)
        val target = uri(entry.location, true)
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            textRecovery.restore(review.recovery, review.currentHash, { readBoundedEditable(input(entry.location)) }) { value ->
                resolver.openOutputStream(target, "wt")?.use { it.write(value); it.flush() } ?: error("提供方未提供可写流")
            }
            changed(parent ?: entry.location); browser.invalidateDirectory(entry.location); clearScanCache()
        }
    }
    suspend fun shareTextRecovery(entry: BrowserEntry, record: SafTextRecovery) = withContext(Dispatchers.IO) {
        checkedRecovery(entry, record)
        val content = androidx.core.content.FileProvider.getUriForFile(app, app.packageName + ".files", textRecovery.original(record))
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_STREAM, content)
            clipData = android.content.ClipData.newRawUri("原文备份", content)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        withContext(Dispatchers.Main) {
            app.startActivity(android.content.Intent.createChooser(send, "分享原文备份")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
    }
    private suspend fun copyOne(entry: BrowserEntry, destination: BrowserLocation, depth: Int, onProgress: suspend (String) -> Unit): BrowserLocation {
        currentCoroutineContext().ensureActive(); check(depth < 64) { "目录嵌套过深" }
        val created = create(destination, entry.name, if (entry.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else entry.mimeType)
        try {
            onProgress(entry.name)
            if (entry.isDirectory) browser.list(entry.location).forEach { child ->
                check(child.blockedReason == null) { "无法复制受保护目录" }; copyOne(child, created, depth + 1, onProgress)
            } else input(entry.location).use { source -> output(created).use { target ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = source.read(buffer); if (read < 0) break
                    target.write(buffer, 0, read)
                }
                target.flush()
            } }
            return created
        } catch (failure: Exception) {
            // Remove only the newly-created partial copy, never the source or a pre-existing target.
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching { deleteOne(BrowserEntry(created, entry.name, entry.isDirectory, entry.mimeType, null, null)) }
                    .onFailure { failure.addSuppressed(it) }
            }
            throw failure
        }
    }
    /** ExternalStorageProvider document IDs let mixed SAF/shared paths share the same safety check. */
    private fun physicalPath(location: BrowserLocation): File? = runCatching {
        if (location.path != null) File(location.path).canonicalFile
        else {
            val document = uri(location)
            if (document.authority != "com.android.externalstorage.documents") null
            else {
                val id = DocumentsContract.getDocumentId(document)
                if (!id.startsWith("primary:")) null else {
                    @Suppress("DEPRECATION") val root = Environment.getExternalStorageDirectory()
                    File(root, id.removePrefix("primary:")).canonicalFile
                }
            }
        }
    }.getOrNull()
    /** API 26–28 has no isChildDocument. Fully walk a small tree; fail closed if it cannot be proved safe. */
    private suspend fun isSafDescendantOnLegacy(source: BrowserLocation, target: BrowserLocation): Boolean {
        val sourceId = requireNotNull(source.documentId)
        val targetId = requireNotNull(target.documentId)
        if (sourceId == targetId) return true
        if (Uri.parse(requireNotNull(source.rootUri)).authority == "com.android.externalstorage.documents") {
            val sourceVolume = sourceId.substringBefore(':', "")
            val targetVolume = targetId.substringBefore(':', "")
            if (sourceVolume.isNotEmpty() && targetVolume.isNotEmpty() &&
                sourceId.contains(':') && targetId.contains(':') &&
                listOf(sourceId, targetId).all { id ->
                    val relative = id.substringAfter(':')
                    relative.isEmpty() || relative.split('/').none { it.isEmpty() || it == "." || it == ".." }
                }
            ) {
                val sourceRelative = sourceId.substringAfter(':')
                val targetRelative = targetId.substringAfter(':')
                return sourceVolume == targetVolume &&
                    (sourceRelative.isEmpty() || targetRelative.startsWith("$sourceRelative/"))
            }
        }
        val pending = ArrayDeque<BrowserLocation>()
        val seenIds = HashSet<String>()
        pending.add(source)
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            check(seenIds.size < 2048) { "无法在安全范围内确认目标目录，请选择另一个位置" }
            val current = pending.removeFirst()
            val id = requireNotNull(current.documentId)
            if (!seenIds.add(id)) continue
            val children = try { browser.list(current) } catch (e: CancellationException) { throw e }
            catch (e: Exception) { throw java.io.IOException("无法确认目标是否位于源目录内，请选择另一个位置", e) }
            for (child in children) {
                if (child.blockedReason != null) error("无法完整检查源目录，请选择另一个位置")
                if (!child.isDirectory) continue
                val childId = child.location.documentId ?: error("目录标识缺失，无法确认目标位置")
                if (childId == targetId) return true
                if (childId !in seenIds) {
                    check(pending.size < 2048) { "目录规模超过安全检查上限，请选择另一个位置" }
                    pending.add(child.location)
                }
            }
        }
        return false
    }
    suspend fun transfer(entry: BrowserEntry, sourceParent: BrowserLocation, destination: BrowserLocation, move: Boolean, onProgress: suspend (String) -> Unit) = withContext(Dispatchers.IO) {
        check(entry.blockedReason == null) { entry.blockedReason ?: "无法操作此项目" }
        if (entry.location.rootAccess || destination.rootAccess) {
            check(entry.location.rootAccess && destination.rootAccess) { "禁止私有Root文件跨模式复制/导出；请在Root工作目录内操作" }
            RootFileIo.copy(entry, destination, move); changed(sourceParent); changed(destination); return@withContext
        }
        check(entry.blockedReason == null) { "受保护项目不能复制或移动" }
        check(canWrite(destination)) { "目标位置只读，请先授权写入" }
        if (move) check(canWrite(entry.location)) { "源位置只读，不能移动；可选择复制" }
        check(entry.location.key != destination.key) { "不能复制到自身" }
        val sourcePath = physicalPath(entry.location); val targetPath = physicalPath(destination)
        if (entry.isDirectory && sourcePath != null && targetPath != null) {
            val source = sourcePath.path; val dest = targetPath.path
            check(dest != source && !dest.startsWith(source + File.separator)) { "不能复制目录到自身的子目录" }
        } else if (entry.isDirectory && entry.location.path == null && destination.path == null && uri(entry.location).authority == uri(destination).authority) {
            val inside = if (android.os.Build.VERSION.SDK_INT >= 29) {
                runCatching { DocumentsContract.isChildDocument(resolver, uri(entry.location), uri(destination)) }
                    .getOrElse { error("无法确认目标是否位于源目录内，请选择另一个授权根") }
            } else {
                isSafDescendantOnLegacy(entry.location, destination)
            }
            check(!inside) { "不能复制目录到自身的子目录" }
        }
        copyOne(entry, destination, 0, onProgress)
        if (move) {
            try { currentCoroutineContext().ensureActive(); deleteOne(entry) }
            catch (e: Exception) { changed(destination); throw java.io.IOException("已复制到目标，但源项目未完全删除：${entry.name}；请检查两端，不要重复移动", e) }
        }
        changed(sourceParent); changed(destination)
    }
    fun changed(@Suppress("UNUSED_PARAMETER") location: BrowserLocation) { browser.invalidateAllDirectories(); clearScanCache() }
}
