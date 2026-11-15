package com.example.globalminusone.data

/** Reauthorization replaces in place; choosing an existing root never duplicates or reorders it. */
internal fun mergeGrantedFileRoot(roots: List<FileBrowserRoot>, root: FileBrowserRoot, replaceUri: String?): List<FileBrowserRoot> {
    var inserted = false
    val updated = roots.mapNotNull { saved ->
        if (saved.uri == root.uri || saved.uri == replaceUri) {
            if (inserted) null else root.also { inserted = true }
        } else saved
    }
    return if (inserted) updated else updated + root
}
