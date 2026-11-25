package com.example.globalminusone.service

import android.view.WindowManager
import org.junit.Assert.*
import org.junit.Test

class PanelWindowHostTest {
    private class FakeBackend : PanelWindowBackend<Any, String> {
        override val defaultManager = "ordinary"
        override var manager = defaultManager
        private val root = Any()
        var viewReads = 0
        override val view: Any get() { viewReads++; return root }
        override var type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val calls = mutableListOf<String>()
        var onOperation: (String) -> Unit = {}
        private fun perform(operation: String, view: Any) {
            assertSame(root, view)
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
        assertEquals(listOf("add:ordinary:2038", "update:ordinary:2038"), f.backend.calls)
        assertEquals(2, f.backend.viewReads)
        assertTrue(f.failures.isEmpty())
    }

    @Test fun migrationResolvesBeforeRemovalAndPublishesNewResourcesBeforeAdd() {
        val f = Fixture()
        f.recover()
        f.backend.calls.clear()
        val sequence = mutableListOf<String>()
        f.backend.onOperation = { op ->
            sequence += op
            if (op == "remove") {
                assertTrue(f.host.registered)
                assertEquals("ordinary", f.backend.manager)
                assertEquals(2038, f.backend.type)
            } else if (op == "add") {
                assertFalse(f.host.registered)
                assertEquals("accessibility", f.backend.manager)
                assertEquals(2032, f.backend.type)
            }
        }
        f.recover(highest = true, resolver = { sequence += "resolve"; "accessibility" })
        assertEquals(listOf("resolve", "remove", "add"), sequence)
        assertTrue(f.host.registered)
        assertEquals(listOf("remove:ordinary:2038", "add:accessibility:2032"), f.backend.calls)
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
        f.recover(highest = true, resolver = { null })
        assertTrue(f.host.registered)
        assertEquals(2032, f.backend.type)
        assertEquals("ordinary", f.backend.manager)
        assertEquals(listOf("remove:ordinary:2038", "add:ordinary:2032"), f.backend.calls)
        assertTrue(f.failures.isEmpty())
    }

    @Test fun accessibilityAddFailureCleansUpThenFallsBackAndCachesFailure() {
        val f = Fixture()
        f.backend.onOperation = {
            if (it == "add" && f.backend.type == 2032) throw IllegalStateException("accessibility add")
        }
        f.recover(highest = true)
        assertTrue(f.host.registered)
        assertEquals(listOf("add:accessibility:2032", "remove:accessibility:2032", "add:ordinary:2038"), f.backend.calls)
        assertEquals("Cannot register panel overlay window", f.failures.single().first)
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
        f.backend.calls.clear()
        f.backend.onOperation = {}
        f.host.resetAccessibilityFailure()
        assertTrue(f.host.registered)
        assertEquals("ordinary", f.backend.manager)
        f.recover(highest = true)
        assertEquals(listOf("remove:ordinary:2038", "add:accessibility:2032"), f.backend.calls)
        assertTrue(f.host.registered)
    }

    @Test fun ordinaryAndFallbackAddFailuresRemainUnregisteredAndCleanUp() {
        val ordinary = Fixture()
        ordinary.backend.onOperation = { if (it == "add") throw IllegalStateException("add") }
        ordinary.recover()
        assertFalse(ordinary.host.registered)
        assertEquals(listOf("add:ordinary:2038", "remove:ordinary:2038"), ordinary.backend.calls)
        assertEquals("Cannot register panel overlay window", ordinary.failures.single().first)
        val fallback = Fixture()
        fallback.backend.onOperation = { if (it == "add") throw IllegalStateException("add") }
        fallback.recover(highest = true)
        assertFalse(fallback.host.registered)
        assertEquals(listOf("add:accessibility:2032", "remove:accessibility:2032", "add:ordinary:2038", "remove:ordinary:2038"), fallback.backend.calls)
        assertEquals(listOf("Cannot register panel overlay window", "Cannot restore ordinary panel overlay"), fallback.failures.map { it.first })
    }

    @Test fun staleUpdateAndAlreadyRemovedRootRecoverInOriginalOrder() {
        val f = Fixture()
        f.recover()
        f.backend.calls.clear()
        f.backend.onOperation = {
            if (it == "update") throw IllegalStateException("stale update")
            if (it == "remove") throw IllegalArgumentException("already removed")
            if (it == "add") assertTrue(f.host.registered) // Assignment still follows the whole helper call.
        }
        f.recover()
        assertEquals(listOf("update:ordinary:2038", "remove:ordinary:2038", "add:ordinary:2038"), f.backend.calls)
        assertEquals(2, f.failures.size)
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
