package com.example.globalminusone.service

import org.junit.Assert.*
import org.junit.Test

class ApplicationWindowRegistrationTest {
    @Test fun rejectedIconUpdateKeepsRegistrationForTheOldIconInsteadOfAddingAnother() {
        val icon = ApplicationWindowRegistration()
        icon.show({}, { fail("not registered yet") })
        try {
            icon.show({ fail("must not add an existing icon") }, {
                throw IllegalArgumentException("temporary update failure")
            })
            fail("expected rejection")
        } catch (_: IllegalArgumentException) { }
        assertTrue(icon.registered)
        var removes = 0
        icon.hide { removes++ }
        assertEquals(1, removes)
        assertFalse(icon.registered)
    }

    @Test fun repeatedMinimizeCallbacksUpdatePendingIconWithoutRestoringTheFullWindow() {
        val icon = ApplicationWindowRegistration()
        var wmOwnsIcon = false
        var fullWindowRestored = false
        var updates = 0
        fun updateIcon() {
            try {
                icon.show(add = {
                    check(!wmOwnsIcon) { "View has already been added" }
                    wmOwnsIcon = true // The icon View has not attached yet.
                }, update = {
                    check(wmOwnsIcon)
                    updates++
                })
            } catch (_: IllegalStateException) { fullWindowRestored = true }
        }
        // Suspension/detachment callbacks and the outer minimize both sync the icon.
        repeat(3) { updateIcon() }
        assertTrue(wmOwnsIcon)
        assertFalse(fullWindowRestored)
        assertEquals(2, updates)
    }

    @Test fun registeredWindowMustBeRemovedEvenBeforeViewAttachment() {
        val registration = ApplicationWindowRegistration()
        val calls = mutableListOf<String>()
        var viewAttached = false // addView can return while the first traversal is pending.
        registration.show({ calls += "add" }, { calls += "update" })
        assertFalse(viewAttached)
        registration.hide { calls += "remove" }
        viewAttached = true // A late View callback cannot change WM ownership.
        assertTrue(viewAttached)
        assertFalse(registration.registered)
        assertEquals(listOf("add", "remove"), calls)
    }

    @Test fun repeatedRestoreUpdatesOneRegisteredWindowAndDoubleHideDoesNotRemoveTwice() {
        val registration = ApplicationWindowRegistration()
        val calls = mutableListOf<String>()
        repeat(3) { registration.show({ calls += "add" }, { calls += "update" }) }
        repeat(2) { registration.hide { calls += "remove" } }
        registration.show({ calls += "add" }, { calls += "update" })
        assertEquals(listOf("add", "update", "update", "remove", "add"), calls)
    }

    @Test fun rejectedRemovalRetainsOwnershipForTransparentFallbackAndRetry() {
        val registration = ApplicationWindowRegistration()
        val calls = mutableListOf<String>()
        registration.show({ calls += "add" }, { fail("not yet registered") })
        try {
            registration.hide { throw IllegalArgumentException("temporary rejection") }
            fail("expected rejection")
        } catch (_: IllegalArgumentException) { }
        assertTrue(registration.registered)
        registration.updateIfRegistered { calls += "transparent" }
        registration.hide { calls += "remove-retry" }
        registration.updateIfRegistered { fail("removed windows must not be updated") }
        assertEquals(listOf("add", "transparent", "remove-retry"), calls)
    }

    @Test fun rejectedAddAndIndependentIconNeverLoseTheirSeparateOwnership() {
        val root = ApplicationWindowRegistration()
        val icon = ApplicationWindowRegistration()
        try {
            root.show({ throw IllegalArgumentException("add rejected") }, { fail("no root") })
            fail("expected rejection")
        } catch (_: IllegalArgumentException) { }
        assertFalse(root.registered)
        icon.show({}, { fail("new icon") })
        root.show({}, { fail("new root") })
        root.hide {}
        assertTrue(icon.registered)
        assertFalse(root.registered)
    }
}
