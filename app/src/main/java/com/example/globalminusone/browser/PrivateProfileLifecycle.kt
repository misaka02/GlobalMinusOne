package com.example.globalminusone.browser

/** Identity-based lifecycle gate; deletion runs only after the bound view is destroyed. */
internal class PrivateProfileLifecycle<T : Any> {
    private var bound: T? = null
    private var closing = false

    fun bind(view: T) {
        check(!closing && bound == null)
        bound = view
    }

    /** Null means deletion is deferred until the bound view has been destroyed. */
    fun finishIfUnbound(delete: () -> Boolean): Boolean? {
        closing = true
        return if (bound == null) delete() else null
    }

    /** Called strictly after destroy(); an unrelated view cannot release the private profile. */
    fun onViewDestroyed(view: T, delete: () -> Boolean): Boolean? {
        if (bound !== view) return null
        bound = null
        closing = true
        return delete()
    }
}
