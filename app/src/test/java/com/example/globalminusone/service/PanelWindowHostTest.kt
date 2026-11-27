package com.example.globalminusone.service

import android.view.WindowManager
import com.example.globalminusone.service.overlay.PanelWindowBackend
import com.example.globalminusone.service.overlay.PanelWindowHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PanelWindowHostTest {
    private class FakeBackend : PanelWindowBackend<Any, String> {
        override val defaultManager = "ordinary"
        override var manager = defaultManager
            private set
        private var currentView = Any()
        var viewReads = 0
        val createdViews = mutableListOf<Any>()
        override val view: Any get() { viewReads++; return currentView }
        override var type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            private set
        val calls = mutableListOf<String>()
        var onOperation: (String) -> Unit = {}

        override fun prepareForAttach(targetType: Int, targetManager: String) {
            type = targetType
            manager = targetManager
            currentView = Any()
            createdViews += currentView
            calls += "prepare:$manager:$type"
            onOperation("prepare")
        }

        private fun perform(operation: String, view: Any) {
            assertSame(currentView, view)
            calls += "$operation:$manager:$type"
            onOperation(operation)
        }

        override fun update(view: Any) = perform("update", view)
        override fun removeImmediate(view: Any) = perform("remove", view)
        override fun add(view: Any) = perform("add", view)
    }

    private class Fixture {
        val backend = FakeBackend()
        val failures = mutableListOf<Pair<String, Throwable?>>()
        val host = PanelWindowHost(backend) { message, failure -> failures += message to failure }
        fun recover(highest: Boolean = false, available: Boolean = true, resolver: () -> String? = { "accessibility" }) =
            host.recover(highest, available, resolver)
    }

    @Test fun repeatedRegistrationUpdatesTheSameRootAndShortCircuitsResolver() {
        val f = Fixture()
        f.recover(resolver = { error("ordinary layer must not resolve accessibility") })
        f.recover(highest = true, available = false, resolver = { error("unavailable accessibility") })
        assertTrue(f.host.registered)
        assertEquals(listOf("prepare:ordinary:2038", "add:ordinary:2038", "update:ordinary:2038"), f.backend.calls)
        assertEquals(1, f.backend.createdViews.size)
        assertEquals(2, f.backend.viewReads)
        assertTrue(f.failures.isEmpty())
    }

    @Test fun migrationResolvesBeforeRemovalAndRebuildsFreshRootBeforeAdd() {
        val f = Fixture()
        f.recover()
        val firstView = f.backend.createdViews.single()
        f.backend.calls.clear()
        val sequence = mutableListOf<String>()
        f.backend.onOperation = { op ->
            sequence += op
            when (op) {
                "remove" -> {
                    assertTrue(f.host.registered)
                    assertEquals("ordinary", f.backend.manager)
                    assertEquals(2038, f.backend.type)
                }
                "add" -> {
                    assertFalse(f.host.registered)
                    assertEquals("accessibility", f.backend.manager)
                    assertEquals(2032, f.backend.type)
                }
            }
        }
        f.recover(highest = true, resolver = { sequence += "resolve"; "accessibility" })
        assertEquals(listOf("resolve", "remove", "prepare", "add"), sequence)
        assertTrue(f.host.registered)
        assertEquals(2, f.backend.createdViews.size)
        assertNotSame(firstView, f.backend.createdViews.last())
        assertEquals(listOf("remove:ordinary:2038", "prepare:accessibility:2032", "add:accessibility:2032"), f.backend.calls)
    }

    @Test fun togglingHighestOverlayOnOffOnRebuildsFreshViewEachTime() {
        val f = Fixture()
        f.recover(highest = false)
        f.recover(highest = true, resolver = { "accessibility" })
        f.recover(highest = false)
        f.recover(highest = true, resolver = { "accessibility" })
        assertEquals(4, f.backend.createdViews.distinct().size)
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, f.host.activeType)
        assertTrue(f.failures.isEmpty())
    }

    @Test fun accessibilityManagerReconnectMigratesEvenWhenTypeUnchanged() {
        val f = Fixture()
        val accOne = String("accessibility-1".toCharArray())
        val accTwo = String("accessibility-2".toCharArray())
        f.recover(highest = true, resolver = { accOne })
        val firstAccView = f.backend.createdViews.last()
        f.backend.calls.clear()

        f.recover(highest = true, resolver = { accTwo })
        assertEquals(
            listOf("remove:accessibility-1:2032", "prepare:accessibility-2:2032", "add:accessibility-2:2032"),
            f.backend.calls
        )
        assertNotSame(firstAccView, f.backend.createdViews.last())
        assertTrue(f.host.registered)
    }

    @Test fun nullAccessibilityManagerFallsBackToOrdinaryWithoutAddingAccessibilityTypeOnDefaultManager() {
        val f = Fixture()
        f.recover()
        f.backend.calls.clear()
        f.recover(highest = true, resolver = { null })
        assertEquals(listOf("update:ordinary:2038"), f.backend.calls)
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, f.backend.type)
    }

    @Test fun unsafeMigrationRemovalIncludingErrorPreservesOldRegistration() {
        for (failure in listOf(IllegalStateException("remove"), AssertionError("remove"))) {
            val f = Fixture()
            f.recover()
            f.backend.calls.clear()
            f.backend.onOperation = { if (it == "remove") throw failure }
            f.recover(highest = true)
            assertTrue(f.host.registered)
            assertEquals("ordinary", f.backend.manager)
            assertEquals(2038, f.backend.type)
            assertEquals(listOf("remove:ordinary:2038"), f.backend.calls)
            assertEquals("Cannot migrate panel overlay", f.failures.single().first)
            assertSame(failure, f.failures.single().second)
        }
    }

    @Test fun alreadyRemovedMigrationToleratesIllegalArgumentException() {
        val f = Fixture()
        f.recover()
        f.backend.calls.clear()
        f.backend.onOperation = { if (it == "remove") throw IllegalArgumentException("gone") }
        f.recover(highest = true, resolver = { "accessibility" })
        assertTrue(f.host.registered)
        assertEquals(2032, f.backend.type)
        assertEquals("accessibility", f.backend.manager)
        assertEquals(listOf("remove:ordinary:2038", "prepare:accessibility:2032", "add:accessibility:2032"), f.backend.calls)
        assertTrue(f.failures.isEmpty())
    }

    @Test fun accessibilityFailureRecordsLastErrorAndFallbackRebuildsOrdinaryView() {
        val f = Fixture()
        f.backend.onOperation = {
            if (it == "add" && f.backend.type == 2032) throw IllegalStateException("accessibility add denied")
        }
        f.recover(highest = true)
        assertTrue(f.host.registered)
        assertEquals(2, f.backend.createdViews.distinct().size)
        assertEquals(
            listOf(
                "prepare:accessibility:2032",
                "add:accessibility:2032",
                "remove:accessibility:2032",
                "prepare:ordinary:2038",
                "add:ordinary:2038"
            ),
            f.backend.calls
        )
        assertEquals("Cannot register panel overlay window", f.failures.single().first)
        assertNotNull(f.host.lastAccessibilityError)
        assertTrue(f.host.lastAccessibilityError!!.contains("IllegalStateException"))

        f.backend.calls.clear()
        f.recover(highest = true, resolver = { error("cached failure must short circuit resolution") })
        assertEquals(listOf("update:ordinary:2038"), f.backend.calls)
        assertEquals(2038, f.backend.type)
    }

    @Test fun resetAllowsAccessibilityRetryWithoutClearingOrdinaryRegistration() {
        val f = Fixture()
        f.backend.onOperation = {
            if (it == "add" && f.backend.type == 2032) throw IllegalStateException("first add")
        }
        f.recover(highest = true)
        assertNotNull(f.host.lastAccessibilityError)
        f.backend.calls.clear()
        f.backend.onOperation = {}
        f.host.resetAccessibilityFailure()
        assertNull(f.host.lastAccessibilityError)
        assertTrue(f.host.registered)
        assertEquals("ordinary", f.backend.manager)
        f.recover(highest = true)
        assertEquals(
            listOf("remove:ordinary:2038", "prepare:accessibility:2032", "add:accessibility:2032"),
            f.backend.calls
        )
        assertTrue(f.host.registered)
        assertNull(f.host.lastAccessibilityError)
    }

    @Test fun ordinaryAndFallbackAddFailuresRemainUnregisteredAndCleanUp() {
        val ordinary = Fixture()
        ordinary.backend.onOperation = { if (it == "add") throw IllegalStateException("add") }
        ordinary.recover()
        assertFalse(ordinary.host.registered)
        assertEquals(listOf("prepare:ordinary:2038", "add:ordinary:2038", "remove:ordinary:2038"), ordinary.backend.calls)
        assertEquals("Cannot register panel overlay window", ordinary.failures.single().first)
        val fallback = Fixture()
        fallback.backend.onOperation = { if (it == "add") throw IllegalStateException("add") }
        fallback.recover(highest = true)
        assertFalse(fallback.host.registered)
        assertEquals(
            listOf(
                "prepare:accessibility:2032",
                "add:accessibility:2032",
                "remove:accessibility:2032",
                "prepare:ordinary:2038",
                "add:ordinary:2038",
                "remove:ordinary:2038"
            ),
            fallback.backend.calls
        )
        assertEquals(listOf("Cannot register panel overlay window", "Cannot restore ordinary panel overlay"), fallback.failures.map { it.first })
    }

    @Test fun staleUpdateAndAlreadyRemovedRootRecoverWithFreshView() {
        val f = Fixture()
        f.recover()
        val initialView = f.backend.createdViews.single()
        f.backend.calls.clear()
        f.backend.onOperation = {
            if (it == "update") throw IllegalStateException("stale update")
            if (it == "remove") throw IllegalArgumentException("already removed")
        }
        f.recover()
        assertEquals(
            listOf("update:ordinary:2038", "remove:ordinary:2038", "prepare:ordinary:2038", "add:ordinary:2038"),
            f.backend.calls
        )
        assertEquals(2, f.failures.size)
        assertNotSame(initialView, f.backend.createdViews.last())
        assertTrue(f.host.registered)
    }

    @Test fun unsafeStaleRemovalDoesNotCreateASecondRoot() {
        val f = Fixture()
        f.recover()
        f.backend.calls.clear()
        f.backend.onOperation = {
            if (it == "update" || it == "remove") throw IllegalStateException(it)
        }
        f.recover()
        assertEquals(listOf("update:ordinary:2038", "remove:ordinary:2038"), f.backend.calls)
        assertFalse(f.host.registered)
        assertEquals("ordinary", f.backend.manager)
        assertEquals(2038, f.backend.type)
    }

    @Test fun resolverFailurePropagatesBeforeChangingResourcesOrRegistration() {
        val f = Fixture()
        f.recover()
        f.backend.calls.clear()
        val failure = AssertionError("resolver")
        try {
            f.recover(highest = true, resolver = { throw failure })
            fail("resolver failure must propagate")
        } catch (caught: AssertionError) {
            assertSame(failure, caught)
        }
        assertTrue(f.host.registered)
        assertEquals("ordinary", f.backend.manager)
        assertEquals(2038, f.backend.type)
        assertTrue(f.backend.calls.isEmpty())
        assertTrue(f.failures.isEmpty())
    }
}
