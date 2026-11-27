package com.example.globalminusone.system.file

/** No path components, NULs or empty names may reach any filesystem/provider mutation. */
internal fun validatedFileName(name: String): String = name.trim().also {
    require(it.isNotBlank() && it !in setOf(".", "..") && '/' !in it && '\\' !in it && '\u0000' !in it) { "名称不能为空或包含路径分隔符" }
    require(it.length <= 255) { "名称过长" }
}

internal fun FileCategoryUsage.including(size: Long?): FileCategoryUsage {
    val known = size?.takeIf { it >= 0 }
    return FileCategoryUsage(Math.addExact(files, 1L), Math.addExact(knownBytes, known ?: 0L),
        Math.addExact(unknownSizes, if (known == null) 1L else 0L))
}

internal fun FileCategoryUsage.excluding(size: Long?): FileCategoryUsage {
    val known = size?.takeIf { it >= 0 }
    val nextFiles = (files - 1L).coerceAtLeast(0L)
    if (nextFiles == 0L) return FileCategoryUsage()
    val nextBytes = (knownBytes - (known ?: 0L)).coerceAtLeast(0L)
    val nextUnknown = (unknownSizes - (if (known == null) 1L else 0L)).coerceAtLeast(0L)
    return FileCategoryUsage(nextFiles, nextBytes, nextUnknown)
}

internal fun FileScanResult.knownRatio(category: FileCategory): Double? =
    if (knownBytes == 0L) null else (groups[category]?.knownBytes ?: 0L).toDouble() / knownBytes

internal enum class AnalyzerSortOrder(val label: String) {
    SIZE_DESC("按大小 ↓"),
    TIME_DESC("按时间 ↓"),
    NAME_ASC("按名称")
}

internal fun sortAnalyzerEntries(entries: List<BrowserEntry>, order: AnalyzerSortOrder): List<BrowserEntry> {
    val nameOrder = compareBy<BrowserEntry> { it.name.lowercase(java.util.Locale.ROOT) }
        .thenBy { it.name }
        .thenBy { it.location.key }
    val comparator = when (order) {
        AnalyzerSortOrder.SIZE_DESC -> compareByDescending<BrowserEntry> { it.size ?: -1L }.then(nameOrder)
        AnalyzerSortOrder.TIME_DESC -> compareByDescending<BrowserEntry> { it.modified ?: -1L }.then(nameOrder)
        AnalyzerSortOrder.NAME_ASC -> nameOrder
    }
    return entries.sortedWith(comparator)
}

/** Updates a cached FileScanResult in-place after deleting files or empty directories so no re-scan is needed. */
internal fun FileScanResult.withoutDeleted(
    deletedEntries: Collection<BrowserEntry>,
    deletedEmptyDirs: Collection<BrowserLocation> = emptyList()
): FileScanResult {
    if (deletedEntries.isEmpty() && deletedEmptyDirs.isEmpty()) return this
    val deletedFileList = deletedEntries.filter { !it.isDirectory }
    val deletedFileKeys = deletedFileList.map { it.location.key }.toSet()
    val deletedDirKeys = (deletedEntries.filter { it.isDirectory }.map { it.location.key } + deletedEmptyDirs.map { it.key }).toSet()

    val updatedGroups = groups.toMutableMap()
    for (entry in deletedFileList) {
        val cat = FileCategory.of(entry)
        val currentUsage = updatedGroups[cat] ?: continue
        val nextUsage = currentUsage.excluding(entry.size)
        if (nextUsage.files <= 0L) updatedGroups.remove(cat) else updatedGroups[cat] = nextUsage
    }

    val updatedCategoryFiles = categoryFiles.mapValues { (_, list) ->
        list.filterNot { it.location.key in deletedFileKeys }
    }.filterValues { it.isNotEmpty() }

    return copy(
        groups = updatedGroups,
        directories = (directories - deletedDirKeys.size.toLong()).coerceAtLeast(1L),
        largeFiles = largeFiles.filterNot { it.location.key in deletedFileKeys },
        apkFiles = apkFiles.filterNot { it.location.key in deletedFileKeys },
        emptyDirectories = emptyDirectories.filterNot { it.key in deletedDirKeys },
        categoryFiles = updatedCategoryFiles
    )
}

/** Updates a cached FileScanResult in-place after renaming a file (including extension/category transitions). */
internal fun FileScanResult.withRenamed(oldEntry: BrowserEntry, updatedEntry: BrowserEntry): FileScanResult {
    if (oldEntry.isDirectory) {
        return copy(emptyDirectories = emptyDirectories.map { if (it.key == oldEntry.location.key) updatedEntry.location else it })
    }
    val oldKey = oldEntry.location.key
    val oldCat = FileCategory.of(oldEntry)
    val newCat = FileCategory.of(updatedEntry)
    val updatedGroups = groups.toMutableMap()
    if (oldCat != newCat) {
        updatedGroups[oldCat]?.let { prev ->
            val next = prev.excluding(oldEntry.size)
            if (next.files <= 0L) updatedGroups.remove(oldCat) else updatedGroups[oldCat] = next
        }
        updatedGroups[newCat] = (updatedGroups[newCat] ?: FileCategoryUsage()).including(updatedEntry.size)
    }

    val updatedCategoryFiles = categoryFiles.toMutableMap()
    if (oldCat == newCat) {
        updatedCategoryFiles[oldCat]?.let { list ->
            updatedCategoryFiles[oldCat] = list.map { if (it.location.key == oldKey) updatedEntry else it }
        }
    } else {
        updatedCategoryFiles[oldCat]?.let { list ->
            val filtered = list.filterNot { it.location.key == oldKey }
            if (filtered.isEmpty()) updatedCategoryFiles.remove(oldCat) else updatedCategoryFiles[oldCat] = filtered
        }
        val targetList = (updatedCategoryFiles[newCat].orEmpty().filterNot { it.location.key == oldKey } + updatedEntry)
        updatedCategoryFiles[newCat] = sortAnalyzerEntries(targetList, AnalyzerSortOrder.SIZE_DESC)
    }

    val updatedLarge = largeFiles.map { if (it.location.key == oldKey) updatedEntry else it }
    val filteredApks = apkFiles.filterNot { it.location.key == oldKey }
    val updatedApks = if (updatedEntry.name.endsWith(".apk", ignoreCase = true)) {
        sortAnalyzerEntries(filteredApks + updatedEntry, AnalyzerSortOrder.SIZE_DESC)
    } else filteredApks

    return copy(
        groups = updatedGroups,
        largeFiles = updatedLarge,
        apkFiles = updatedApks,
        categoryFiles = updatedCategoryFiles
    )
}

private val COPY_LIKE_STEM_REGEX = Regex(
    """(?i)(?:[ _-]+copy(?:\d*)|[ _-]+duplicate(?:\d*)|\s*\(\d+\)|[ _-]*副本\d*)$"""
)

/**
 * Deterministically ranks entries within a duplicate group so index 0 is the safest original to keep:
 * 1. Prefer stems without copy/duplicate suffixes (`report.pdf` over `report_duplicate.pdf` / `report (1).pdf` / `report副本.pdf`);
 * 2. Prefer earliest known `modified` timestamp;
 * 3. Prefer shallower directory depth;
 * 4. Prefer shorter filename;
 * 5. Deterministic `location.key` tie-breaker.
 */
internal fun orderDuplicateEntriesKeepingOriginal(entries: List<BrowserEntry>): List<BrowserEntry> {
    if (entries.size <= 1) return entries
    fun copyPenalty(entry: BrowserEntry): Int {
        val stem = entry.name.substringBeforeLast('.', entry.name)
        return if (COPY_LIKE_STEM_REGEX.containsMatchIn(stem)) 1 else 0
    }
    fun pathDepth(entry: BrowserEntry): Int {
        val raw = (entry.location.path ?: entry.location.documentId ?: entry.location.key)
            .replace('\\', '/')
            .trim('/')
        return if (raw.isEmpty()) 0 else raw.count { it == '/' }
    }
    return entries.sortedWith(
        compareBy<BrowserEntry> { copyPenalty(it) }
            .thenBy { it.modified?.takeIf { ts -> ts > 0L } ?: Long.MAX_VALUE }
            .thenBy { pathDepth(it) }
            .thenBy { it.name.length }
            .thenBy { it.location.key }
    )
}

internal fun List<DuplicateFileGroup>.withoutDeletedKeys(deletedKeys: Set<String>): List<DuplicateFileGroup> {
    if (deletedKeys.isEmpty()) return this
    return mapNotNull { group ->
        val remaining = orderDuplicateEntriesKeepingOriginal(
            group.entries.filterNot { it.location.key in deletedKeys }
        )
        if (remaining.size >= 2) group.copy(entries = remaining) else null
    }.sortedByDescending { it.wastedBytes }
}

internal fun List<DuplicateFileGroup>.withRenamed(oldKey: String, updatedEntry: BrowserEntry): List<DuplicateFileGroup> =
    map { group ->
        if (group.entries.any { it.location.key == oldKey }) {
            group.copy(
                entries = orderDuplicateEntriesKeepingOriginal(
                    group.entries.map { if (it.location.key == oldKey) updatedEntry else it }
                )
            )
        } else group
    }

/** Selects all redundant copies while keeping the best original copy of each duplicate group untouched. */
internal fun selectRedundantDuplicateKeys(groups: List<DuplicateFileGroup>): Set<String> =
    groups.flatMap { group ->
        if (group.entries.size <= 1) emptyList()
        else orderDuplicateEntriesKeepingOriginal(group.entries).drop(1).map { it.location.key }
    }.toSet()

/** Formats a concise relative parent folder label under the analyzed root directory. */
internal fun relativeAnalyzerParentLabel(root: BrowserLocation, entry: BrowserEntry): String {
    val entryPath = entry.location.path
    val rootPath = root.path
    if (entryPath != null && rootPath != null) {
        val cleanRoot = rootPath.replace('\\', '/').trimEnd('/')
        val rawParent = (entry.parentLocation?.path ?: entryPath.replace('\\', '/').substringBeforeLast('/', ""))
            .replace('\\', '/').trimEnd('/')
        if (rawParent.isEmpty() || rawParent.equals(cleanRoot, ignoreCase = false)) return "当前目录"
        if (rawParent.startsWith("$cleanRoot/")) {
            return rawParent.removePrefix("$cleanRoot/").ifBlank { "当前目录" }
        }
        return entry.parentLocation?.title?.ifBlank { "子目录" } ?: rawParent.substringAfterLast('/').ifBlank { "子目录" }
    }
    val rootDocId = root.documentId
    val parentDocId = entry.parentLocation?.documentId
    if (rootDocId != null && parentDocId != null) {
        if (parentDocId == rootDocId) return "当前目录"
        if (parentDocId.startsWith("$rootDocId/")) {
            return parentDocId.removePrefix("$rootDocId/").ifBlank { "当前目录" }
        }
        return entry.parentLocation.title.ifBlank { "子目录" }
    }
    val entryDocId = entry.location.documentId
    if (rootDocId != null && entryDocId != null && entryDocId.startsWith("$rootDocId/")) {
        val rel = entryDocId.removePrefix("$rootDocId/")
        return if ('/' in rel) rel.substringBeforeLast('/') else "当前目录"
    }
    return entry.parentLocation?.title?.takeIf { it.isNotBlank() && it != root.title } ?: "当前目录"
}

data class FileChecksums(
    val md5: String,
    val sha1: String,
    val sha256: String,
    val crc32: String,
    val byteCount: Long
) {
    /** Compares a clipboard or user-supplied hash against calculated digests. */
    fun matchedAlgorithm(candidateRaw: String): String? {
        val clean = candidateRaw.trim().lowercase( java.util.Locale.ROOT )
            .removePrefix("0x")
        if (clean.isEmpty()) return null
        return when (clean) {
            md5.lowercase(java.util.Locale.ROOT) -> "MD5"
            sha1.lowercase(java.util.Locale.ROOT) -> "SHA-1"
            sha256.lowercase(java.util.Locale.ROOT) -> "SHA-256"
            crc32.lowercase(java.util.Locale.ROOT) -> "CRC32"
            else -> null
        }
    }
}

internal fun computeStreamChecksums(
    stream: java.io.InputStream,
    maxBytes: Long = 512L * 1024 * 1024,
    checkCancelled: () -> Unit = {},
    onProgress: (Long) -> Unit = {}
): FileChecksums = stream.use { input ->
    val md5 = java.security.MessageDigest.getInstance("MD5")
    val sha1 = java.security.MessageDigest.getInstance("SHA-1")
    val sha256 = java.security.MessageDigest.getInstance("SHA-256")
    val crc32 = java.util.zip.CRC32()
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        checkCancelled()
        val read = input.read(buffer)
        if (read < 0) break
        if (read == 0) continue
        total += read
        check(total <= maxBytes) { "文件超过 512 MB 校验上限" }
        md5.update(buffer, 0, read)
        sha1.update(buffer, 0, read)
        sha256.update(buffer, 0, read)
        crc32.update(buffer, 0, read)
        onProgress(total)
    }
    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }
    FileChecksums(
        md5 = md5.digest().toHex(),
        sha1 = sha1.digest().toHex(),
        sha256 = sha256.digest().toHex(),
        crc32 = "%08x".format(java.util.Locale.ROOT, crc32.value),
        byteCount = total
    )
}

/**
 * Fast, ReDoS-free in-memory filename filter inspired by MT Manager.
 * Supports plain substrings, comma/semicolon-separated extension lists (e.g. `*.apk, *.zip`),
 * and `*` / `?` glob wildcards.
 */
internal fun matchesQuickFilter(entryName: String, filterQuery: String): Boolean {
    val trimmed = filterQuery.trim()
    if (trimmed.isEmpty()) return true
    val tokens = trimmed.split(',', ';', '|').map { it.trim() }.filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return true
    val candidate = entryName.lowercase(java.util.Locale.ROOT)
    return tokens.any { rawToken ->
        val token = rawToken.lowercase(java.util.Locale.ROOT)
        when {
            token.startsWith("*.") && token.indexOf('*', 2) < 0 && '?' !in token ->
                candidate.endsWith(token.substring(1))
            token.startsWith(".") && '*' !in token && '?' !in token ->
                candidate.endsWith(token)
            '*' in token || '?' in token ->
                globMatchLinear(candidate, token)
            else ->
                candidate.contains(token)
        }
    }
}

private fun globMatchLinear(text: String, pattern: String): Boolean {
    var tIdx = 0
    var pIdx = 0
    var starIdx = -1
    var matchIdx = 0
    while (tIdx < text.length) {
        when {
            pIdx < pattern.length && (pattern[pIdx] == '?' || pattern[pIdx] == text[tIdx]) -> {
                tIdx++
                pIdx++
            }
            pIdx < pattern.length && pattern[pIdx] == '*' -> {
                starIdx = pIdx
                matchIdx = tIdx
                pIdx++
            }
            starIdx != -1 -> {
                pIdx = starIdx + 1
                matchIdx++
                tIdx = matchIdx
            }
            else -> return false
        }
    }
    while (pIdx < pattern.length && pattern[pIdx] == '*') pIdx++
    return pIdx == pattern.length
}
