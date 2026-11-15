package com.example.globalminusone

import com.example.globalminusone.service.ensureOverlayWindowRegistered
import org.junit.Assert.*
import org.junit.Test

class OverlayWindowRegistrationTest {
    private class Window(val id: String, var attached: Boolean = false)

    @Test fun successfulAddBeforeAsyncAttachmentIsRetainedAndUpdatedWithoutDuplicateAdd() {
        val window = Window("pending-attach")
        val registered = mutableSetOf<Window>()
        var adds = 0
        fun reconcile(existing: Window?) = ensureOverlayWindowRegistered(existing,
            create = { window },
            update = { check(it in registered) },
            remove = { registered.remove(it); Unit },
            add = { check(registered.add(it)); adds++ },
            onFailure = { throw AssertionError(it) })
        assertSame(window, reconcile(null))
        assertFalse(window.attached)
        assertSame(window, reconcile(window))
        assertEquals(1, adds)
    }

    @Test fun staleHandleIsRemovedAndReplacedInTheSameReconciliation() {
        val stale = Window("stale")
        val replacement = Window("replacement")
        val events = mutableListOf<String>()
        val result = ensureOverlayWindowRegistered(stale,
            create = { events += "create"; replacement },
            update = { events += "update"; throw IllegalArgumentException("not registered") },
            remove = { events += "remove:${it.id}" },
            add = { events += "add:${it.id}" }, onFailure = { events += "failure" })
        assertSame(replacement, result)
        assertEquals(listOf("update", "failure", "remove:stale", "create", "add:replacement"), events)
    }

    @Test fun failedReplacementClearsReferenceAndCleansPartialRegistrationWithoutRetryLoop() {
        val stale = Window("stale")
        val replacement = Window("replacement")
        val removed = mutableListOf<Window>()
        var adds = 0
        val result = ensureOverlayWindowRegistered(stale, create = { replacement },
            update = { throw IllegalArgumentException("lost") },
            remove = { removed += it },
            add = { adds++; throw SecurityException("permission revoked") }, onFailure = {})
        assertNull(result)
        assertEquals(listOf(stale, replacement), removed)
        assertEquals(1, adds)
    }

    @Test fun missingOldRegistrationDoesNotPreventOneReplacementAttempt() {
        val replacement = Window("new")
        val result = ensureOverlayWindowRegistered(Window("gone"), create = { replacement },
            update = { throw IllegalArgumentException("gone") },
            remove = { throw IllegalArgumentException("already removed") }, add = {}, onFailure = {})
        assertSame(replacement, result)
    }

    @Test fun unresolvedRemovalFailureDoesNotCreateADuplicateRoot() {
        var created = false
        val result = ensureOverlayWindowRegistered(Window("registered"),
            create = { created = true; Window("duplicate") },
            update = { throw SecurityException("update denied") },
            remove = { throw SecurityException("remove denied") },
            add = { fail("Old root was not safely removed") }, onFailure = {})
        assertNull(result)
        assertFalse(created)
    }
}
