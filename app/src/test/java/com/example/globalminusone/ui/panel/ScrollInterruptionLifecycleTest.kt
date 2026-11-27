package com.example.globalminusone.ui.panel

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollableState
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.ui.panel.gesture.AncestorScrollInterruption
import com.example.globalminusone.ui.panel.gesture.PanelScrollTarget

@OptIn(ExperimentalCoroutinesApi::class)
class ScrollInterruptionLifecycleTest {
    @Test fun cancelledChildTouchCannotStopTheNextGestureInAnOuterAncestor() = runTest {
        val page = ScrollableState { it }
        val container = ScrollableState { it }
        val cleanupEntered = CompletableDeferred<Unit>()
        val releaseCleanup = CompletableDeferred<Unit>()
        val oldContainerFling = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            container.scroll {
                try { awaitCancellation() }
                finally {
                    // Native stop can suspend while the interrupted mutation cleans up.
                    withContext(NonCancellable) { cleanupEntered.complete(Unit); releaseCleanup.await() }
                }
            }
        }
        val interruption = AncestorScrollInterruption(backgroundScope,
            PanelScrollTarget(container, PanelScrollTarget(page)))
        runCurrent()
        assertTrue(cleanupEntered.isCompleted)
        interruption.finish() // child UP/CANCEL or viewport disposal
        val nextPageGesture = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            page.scroll(MutatePriority.UserInput) { awaitCancellation() }
        }
        releaseCleanup.complete(Unit)
        runCurrent()
        assertTrue("The expired stop must not reach and cancel a later page gesture", nextPageGesture.isActive)
        assertTrue(page.isScrollInProgress)
        assertFalse(container.isScrollInProgress)
        assertTrue(oldContainerFling.isCompleted)
        nextPageGesture.cancelAndJoin()
    }

    @Test fun repeatedChildTouchesStopAllPreviousNativeInertiaAndReleaseForNextDrag() = runTest {
        val page = ScrollableState { it }
        val container = ScrollableState { it }
        val target = PanelScrollTarget(container, PanelScrollTarget(page))
        repeat(8) {
            val outer = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                page.scroll { awaitCancellation() }
            }
            val inner = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                container.scroll { awaitCancellation() }
            }
            val interruption = AncestorScrollInterruption(backgroundScope, target)
            runCurrent()
            assertFalse(page.isScrollInProgress)
            assertFalse(container.isScrollInProgress)
            assertTrue(outer.isCompleted)
            assertTrue(inner.isCompleted)
            interruption.finish()
            var distance = 0f
            container.scroll(MutatePriority.UserInput) { distance += scrollBy(24f) }
            assertEquals(24f, distance, 0f)
        }
    }
}
