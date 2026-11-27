package com.example.globalminusone.ui.panel.file

/** One ordered dirty-pane decision at a time; retained callbacks cannot finish a later close. */
internal class FileWorkspaceCloseSequence {
    private var generation = 0L
    private var pendingPane: Int? = null
    private var disposed = false

    fun begin(): Long? {
        if (disposed || pendingPane != null) return null
        pendingPane = 0
        return ++generation
    }

    fun isPending(token: Long, pane: Int): Boolean =
        !disposed && token == generation && pendingPane == pane

    fun accept(token: Long, pane: Int): Boolean {
        if (!isPending(token, pane)) return false
        pendingPane = pane + 1
        return true
    }

    fun cancel(token: Long) {
        if (token == generation) invalidate()
    }

    fun invalidate() {
        generation++
        pendingPane = null
    }

    fun dispose() {
        invalidate()
        disposed = true
    }
}
