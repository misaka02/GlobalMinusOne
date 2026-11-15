package com.example.globalminusone.regression.v2436

import com.example.globalminusone.ui.panel.*
import org.junit.Assert.*
import org.junit.Test

class R7DiscardViewportTest {
    @Test fun deletingFirstAppAnchorsShortcutButDiscardRestoresAppViewportCheckpoint() {
        val store = PageViewportStore()
        val page = store.page("page1")
        val originalIds = listOf("app-container", "shortcut", "note")
        val original = LinearViewportAnchor("app-container", 0, 0)
        page.linearAnchor = original
        store.beginEditing()
        val editingRevision = page.restoreRevision

        // This is the production EditableLinearPage publisher after removing the first card.
        val draftSpans = listOf(LinearCardSpan("shortcut", 0, 100), LinearCardSpan("note", 116, 90))
        val draftAnchor = requireNotNull(anchorAtLinearScroll(draftSpans, 0))
        assertTrue(page.recordEditingViewport(0, draftAnchor, editingRevision))
        assertEquals(1 to 0, lazyPositionForAnchor(requireNotNull(page.linearAnchor), originalIds))

        store.finishEditing(save = false)
        assertEquals(original, page.linearAnchor)
        assertEquals(0 to 0, lazyPositionForAnchor(requireNotNull(page.linearAnchor), originalIds))
        // An already queued draft collector cannot hide the restored first card again.
        assertFalse(page.recordEditingViewport(0, draftAnchor, editingRevision))
        assertFalse(page.recordLinearAnchor(draftAnchor, editingRevision))
        assertEquals(original, page.linearAnchor)
    }

    @Test fun discardRestoresEachPagesOwnOffsetWithoutNavigatingEveryPageToItsStart() {
        val store = PageViewportStore()
        val first = store.page("page1")
        val second = store.page("page2")
        first.linearAnchor = LinearViewportAnchor("mid-card", 41, 3)
        first.editScrollPx = 615
        second.linearAnchor = LinearViewportAnchor("page2-note", 23, 2)
        second.freeScrollPx = 987
        store.beginEditing()
        first.recordEditingViewport(0, LinearViewportAnchor("shortcut", 0, 0), first.restoreRevision)
        second.recordEditingViewport(65, LinearViewportAnchor("other", 10, 0), second.restoreRevision)
        second.freeScrollPx = 65
        store.beginEditing() // another edit affordance must not capture the already changed anchors.
        store.finishEditing(save = false)

        assertEquals(LinearViewportAnchor("mid-card", 41, 3), first.linearAnchor)
        assertEquals(615, first.editScrollPx)
        assertEquals(LinearViewportAnchor("page2-note", 23, 2), second.linearAnchor)
        assertEquals(987, second.freeScrollPx)
    }

    @Test fun savingAndOrdinaryExitKeepTheFinalPositionAndDoNotIncrementRestoreRevision() {
        val store = PageViewportStore()
        val page = store.page("page1")
        page.linearAnchor = LinearViewportAnchor("app-container", 17, 0)
        store.beginEditing()
        val final = LinearViewportAnchor("shortcut", 31, 1)
        page.recordEditingViewport(533, final, page.restoreRevision)
        val revision = page.restoreRevision
        store.finishEditing(save = true)
        store.finishEditing(save = false) // duplicate completion has no old checkpoint to restore.
        assertEquals(final, page.linearAnchor)
        assertEquals(533, page.editScrollPx)
        assertEquals(revision, page.restoreRevision)
        // Entering and exiting without edits likewise preserves the user's current screen position.
        store.beginEditing(); store.finishEditing(save = true)
        assertEquals(final, page.linearAnchor)
    }

    @Test fun aPageRemovedDuringEditRetainsItsCheckpointAndFirstVisitedPagesHaveAnEntryCheckpoint() {
        val store = PageViewportStore()
        store.page("removed").linearAnchor = LinearViewportAnchor("kept-card", 9, 1)
        store.beginEditing()
        store.retain(emptySet())
        val firstVisit = store.page("first-visit")
        firstVisit.recordEditingViewport(88, LinearViewportAnchor("new-anchor", 7, 0), firstVisit.restoreRevision)
        store.finishEditing(save = false)
        assertEquals(LinearViewportAnchor("kept-card", 9, 1), store.page("removed").linearAnchor)
        assertNull(store.page("first-visit").linearAnchor)
        assertEquals(0, store.page("first-visit").editScrollPx)
    }
}
