package com.example.globalminusone.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.io.File
import java.util.Locale

/** Only bookmarks and display preferences are persisted; file contents never enter app state. */
@Immutable
@Serializable
data class FileBrowserConfig(
    val roots: List<FileBrowserRoot> = emptyList(),
    val sortOrder: FileBrowserSortOrder = FileBrowserSortOrder.NAME,
    val sortDescending: Boolean = false,
    val showHidden: Boolean = false,
    val windowWidthFraction: Float = .9f,
    val windowHeightFraction: Float = .68f,
    val windowXFraction: Float = .05f,
    val windowYFraction: Float = .14f,
    val favorites: List<FileBrowserBookmark> = emptyList(),
    val recent: List<FileBrowserBookmark> = emptyList()
)

@Immutable
@Serializable
data class FileBrowserBookmark(
    val title: String, val rootUri: String? = null, val documentId: String? = null,
    val path: String? = null, val singleDocumentUri: String? = null,
    val directory: Boolean = true, val mimeType: String = "application/octet-stream", val rootAccess: Boolean = false
)

@Immutable
@Serializable
data class FileBrowserRoot(val uri: String, val title: String)

@Serializable
enum class FileBrowserSortOrder(val label: String) {
    NAME("名称"), MODIFIED("修改时间"), SIZE("大小")
}

/** Pure policies shared by IO and tests. Canonical paths prevent traversal and symlink escape. */
object FileBrowserPolicy {
    const val TEXT_PREVIEW_BYTES = 256 * 1024
    const val IMAGE_INPUT_BYTES = 32L * 1024 * 1024

    fun allowsSharedPath(root: File, candidate: File): Boolean = try {
        val base = root.canonicalFile.path
        val path = candidate.canonicalFile.path
        if (path != base && !path.startsWith(base + File.separator)) {
            false
        } else {
            val parts = path.removePrefix(base).trimStart(File.separatorChar)
                .split(File.separatorChar).map { it.lowercase(Locale.ROOT) }
            !(parts.size >= 2 && parts[0] == "android" && parts[1] in setOf("data", "obb"))
        }
    } catch (_: Exception) { false }

    /** Power-of-two sampling bounds both dimensions and decoded pixel count. */
    fun imageSampleSize(width: Int, height: Int): Int {
        require(width > 0 && height > 0)
        var sample = 1
        fun sampled(value: Int) = (value.toLong() + sample - 1) / sample
        while (sampled(width) > 2048 || sampled(height) > 2048 ||
            sampled(width) * sampled(height) > 2_097_152L) {
            sample *= 2
        }
        return sample
    }
}
