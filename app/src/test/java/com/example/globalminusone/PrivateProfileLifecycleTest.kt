package com.example.globalminusone

import com.example.globalminusone.browser.PrivateProfileLifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateProfileLifecycleTest {
    @Test fun unboundCancellationDeletesImmediately() {
        val lifecycle = PrivateProfileLifecycle<Any>()
        var calls = 0
        assertEquals(true, lifecycle.finishIfUnbound { calls++; true })
        assertEquals(1, calls)
    }

    @Test fun boundViewDefersDeletionAndIgnoresUnrelatedView() {
        val lifecycle = PrivateProfileLifecycle<Any>()
        val privateView = Any()
        lifecycle.bind(privateView)
        var calls = 0
        assertNull(lifecycle.finishIfUnbound { calls++; true })
        assertNull(lifecycle.onViewDestroyed(Any()) { calls++; true })
        assertEquals(0, calls)
        assertEquals(true, lifecycle.onViewDestroyed(privateView) { calls++; true })
        assertEquals(1, calls)
    }

    @Test fun deletionFailureIsReportedAfterDestruction() {
        val lifecycle = PrivateProfileLifecycle<Any>()
        val view = Any()
        lifecycle.bind(view)
        assertNull(lifecycle.finishIfUnbound { true })
        assertEquals(false, lifecycle.onViewDestroyed(view) { false })
    }
}
