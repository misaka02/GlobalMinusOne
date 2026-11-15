package com.example.globalminusone.regression.v2440

import com.example.globalminusone.ActionPickerKind
import com.example.globalminusone.ActionPickerRequest
import org.junit.Assert.*
import org.junit.Test

class ActionPickerRequestTest {
    @Test fun contactRouteKeepsItsOriginalNestedTargetAndInsertionAnchor() {
        val request = ActionPickerRequest.fromLegacy("CONTACT", "original-page", "nested-folder", "before-card")!!
        assertEquals(ActionPickerKind.CONTACT, request.kind)
        assertEquals("original-page", request.pageId)
        assertEquals("nested-folder", request.containerId)
        assertEquals("before-card", request.beforeComponentId)
    }

    @Test fun missingTargetIsNotInventedAndLegacyActivityFallbackIsPreserved() {
        assertNull(ActionPickerRequest.fromLegacy("CONTACT", null, "folder", null))
        assertNull(ActionPickerRequest.fromLegacy("ACTIVITY", "  ", null, null))
        assertEquals(ActionPickerKind.ACTIVITY, ActionPickerRequest.fromLegacy(null, "page", null, null)!!.kind)
        assertEquals(ActionPickerKind.ACTIVITY, ActionPickerRequest.fromLegacy("old-kind", "page", null, null)!!.kind)
        // Empty IDs remain explicit invalid targets, not implicit top-level placement.
        assertEquals("", ActionPickerRequest.fromLegacy("ACTIVITY", "page", "", "")!!.containerId)
    }
}
