package com.example.globalminusone.service

import android.view.WindowManager
import com.example.globalminusone.service.overlay.ManagedOverlaySlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayWindowLayerSlotTest {

    private class FakeWindowView(val owner: String)

    @Test
    fun syncRecreatesViewWithMatchingOwnerAcrossOverlayLevels() {
        val defaultMgr = "default-wm"
        val accMgr = "accessibility-wm"
        val slot = ManagedOverlaySlot<FakeWindowView, String>(defaultMgr)
        val calls = mutableListOf<String>()

        fun runSync(highest: Boolean, accessible: Boolean): FakeWindowView? =
            slot.sync(
                highestOverlay = highest,
                accessibilityAvailable = accessible,
                resolveAccessibilityManager = { accMgr },
                createView = { mgr -> FakeWindowView(mgr) },
                add = { m, v, type ->
                    assertEquals(m, v.owner)
                    calls += "add:$m:$type"
                },
                update = { m, v, type ->
                    assertEquals(m, v.owner)
                    calls += "update:$m:$type"
                },
                removeImmediate = { m, _ -> calls += "remove:$m" },
                onBeforeRemove = { calls += "before-remove" }
            )

        val ordinaryView = runSync(highest = false, accessible = true)!!
        assertEquals(defaultMgr, ordinaryView.owner)
        assertSame(ordinaryView, slot.view)
        assertEquals(defaultMgr, slot.registeredManager)
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, slot.registeredType)

        // Repeated sync in same mode updates the exact same View in place.
        val updatedView = runSync(highest = false, accessible = true)
        assertSame(ordinaryView, updatedView)
        assertEquals(listOf("add:default-wm:2038", "update:default-wm:2038"), calls)

        // Turning on highestOverlayLevel removes old View and builds a new View owned by accessibility-wm.
        calls.clear()
        val accView = runSync(highest = true, accessible = true)!!
        assertNotSame(ordinaryView, accView)
        assertEquals(accMgr, accView.owner)
        assertEquals(listOf("before-remove", "remove:default-wm", "add:accessibility-wm:2032"), calls)
        assertEquals(accMgr, slot.registeredManager)
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, slot.registeredType)

        // Turning off highestOverlayLevel recreates a clean View owned by default-wm.
        calls.clear()
        val restoredView = runSync(highest = false, accessible = true)!!
        assertNotSame(accView, restoredView)
        assertEquals(defaultMgr, restoredView.owner)
        assertEquals(listOf("before-remove", "remove:accessibility-wm", "add:default-wm:2038"), calls)
        assertEquals(defaultMgr, slot.registeredManager)
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, slot.registeredType)
    }

    @Test
    fun accessibilityAddFailureRecreatesFallbackViewAndRecordsError() {
        val defaultMgr = "default-wm"
        val accMgr = "accessibility-wm"
        val slot = ManagedOverlaySlot<FakeWindowView, String>(defaultMgr)
        var failAccAdd = true
        val errors = mutableListOf<Throwable>()
        val updatedWith = mutableListOf<String>()

        val result = slot.sync(
            highestOverlay = true,
            accessibilityAvailable = true,
            resolveAccessibilityManager = { accMgr },
            createView = { mgr -> FakeWindowView(mgr) },
            add = { m, _, _ ->
                if (m == accMgr && failAccAdd) throw IllegalStateException("acc add denied")
            },
            update = { _, _, _ -> },
            removeImmediate = { _, _ -> },
            onFailure = { errors += it }
        )!!

        assertEquals(1, errors.size)
        assertEquals(defaultMgr, result.owner)
        assertEquals(defaultMgr, slot.registeredManager)
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, slot.registeredType)
        assertNotNull(slot.lastAccessibilityError)
        assertTrue(slot.lastAccessibilityError!!.contains("IllegalStateException"))

        // Reset clears error and allows retrying with a fresh accessibility-owned View.
        failAccAdd = false
        slot.resetAccessibilityFailure()
        assertNull(slot.lastAccessibilityError)
        val retried = slot.sync(
            highestOverlay = true,
            accessibilityAvailable = true,
            resolveAccessibilityManager = { accMgr },
            createView = { mgr -> FakeWindowView(mgr) },
            add = { _, _, _ -> },
            update = { _, _, _ -> },
            removeImmediate = { _, _ -> }
        )!!
        assertNotSame(result, retried)
        assertEquals(accMgr, retried.owner)
        assertEquals(accMgr, slot.registeredManager)
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, slot.registeredType)
        assertNull(slot.lastAccessibilityError)

        slot.updateLayout(retried) { mgr, _, registeredType ->
            updatedWith += "$mgr:$registeredType"
        }
        assertEquals(listOf("accessibility-wm:2032"), updatedWith)

        slot.remove(removeImmediate = { mgr, _ -> assertEquals(accMgr, mgr) })
        assertNull(slot.view)
        assertNull(slot.registeredManager)
    }
}
