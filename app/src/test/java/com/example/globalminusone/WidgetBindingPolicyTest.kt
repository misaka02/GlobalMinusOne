package com.example.globalminusone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetBindingPolicyTest {
    @Test
    fun pickerKeepsAllocatedIdWhenResultOmitsExtras() {
        assertEquals(42, WidgetBindingPolicy.ownedIdForResult(42, null))
        assertEquals(42, WidgetBindingPolicy.ownedIdForResult(42, 42))
    }

    @Test
    fun pickerCannotClaimAnotherOrUnallocatedId() {
        assertNull(WidgetBindingPolicy.ownedIdForResult(42, 17))
        assertNull(WidgetBindingPolicy.ownedIdForResult(42, 0))
        assertNull(WidgetBindingPolicy.ownedIdForResult(null, 17))
        assertNull(WidgetBindingPolicy.ownedIdForResult(0, null))
    }

    @Test
    fun cancelledOrFailedFlowOnlyReleasesOwnedUnsavedIdsOnce() {
        assertEquals(setOf(42), WidgetBindingPolicy.idsToRelease(listOf(42, 42), emptySet()))
        assertEquals(emptySet<Int>(), WidgetBindingPolicy.idsToRelease(listOf(42), setOf(42)))
        assertEquals(setOf(43), WidgetBindingPolicy.idsToRelease(listOf(42, 43, 0, -1), setOf(42, 17)))
    }
}
