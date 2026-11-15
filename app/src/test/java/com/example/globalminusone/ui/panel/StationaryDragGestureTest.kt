package com.example.globalminusone.ui.panel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.IntSize
import java.util.concurrent.CancellationException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Runs the production detector. The fake supplies events/passes and a virtual timeout only;
 * it contains no hold, movement-budget, drag or ownership decisions. */
@OptIn(ExperimentalCoroutinesApi::class)
class StationaryDragGestureTest {
    private class Events(private val clock: TestCoroutineScheduler, private val timers: CoroutineScope) : PointerInputScope, AwaitPointerEventScope {
        override val density = 1f
        override val fontScale = 1f
        override val size = IntSize(600, 800)
        override val extendedTouchPadding = Size.Zero
        override val viewConfiguration = object : ViewConfiguration {
            override val longPressTimeoutMillis = 500L
            override val doubleTapTimeoutMillis = 300L
            override val doubleTapMinTimeMillis = 40L
            override val touchSlop = 8f
        }
        override var currentEvent = PointerEvent(emptyList())
            private set
        var awaitingPass: PointerEventPass? = null
            private set
        private var pending: Continuation<PointerEvent>? = null
        private var previousPosition = Offset.Zero
        private var previousPressed = false
        private var previousTime = 0L

        override suspend fun <R> awaitPointerEventScope(block: suspend AwaitPointerEventScope.() -> R): R =
            suspendCancellableCoroutine { caller ->
                // Compose's restricted pointer coroutine runs with an empty context;
                // only the outer PointerInputScope coroutine carries the Job/dispatcher.
                caller.invokeOnCancellation { cause ->
                    failPending(cause ?: CancellationException("Pointer scope cancelled"))
                }
                block.startCoroutine(this, object : Continuation<R> {
                    override val context = EmptyCoroutineContext
                    override fun resumeWith(result: Result<R>) { caller.resumeWith(result) }
                })
            }

        override suspend fun awaitPointerEvent(pass: PointerEventPass): PointerEvent =
            suspendCoroutine { continuation ->
                check(pending == null)
                awaitingPass = pass
                pending = continuation
            }

        private fun failPending(error: Throwable) {
            val continuation = pending ?: return
            pending = null
            awaitingPass = null
            continuation.resumeWithException(error)
        }

        private class EventTimeout : CancellationException("Virtual pointer-event timeout")

        private suspend fun <T> timed(timeMillis: Long, block: suspend AwaitPointerEventScope.() -> T): T =
            suspendCoroutine { caller ->
                val timeout = timers.launch { delay(timeMillis); failPending(EventTimeout()) }
                block.startCoroutine(this, object : Continuation<T> {
                    override val context = EmptyCoroutineContext
                    override fun resumeWith(result: Result<T>) {
                        timeout.cancel()
                        caller.resumeWith(result)
                    }
                })
            }

        override suspend fun <T> withTimeoutOrNull(timeMillis: Long, block: suspend AwaitPointerEventScope.() -> T): T? = try {
            timed(timeMillis, block)
        } catch (_: EventTimeout) {
            null
        }

        override suspend fun <T> withTimeout(timeMillis: Long, block: suspend AwaitPointerEventScope.() -> T): T =
            timed(timeMillis, block)

        fun send(pass: PointerEventPass, position: Offset, pressed: Boolean, consumed: Boolean = false): PointerInputChange {
            assertEquals("Event must match the pass requested by the production detector", pass, awaitingPass)
            val change = PointerInputChange(
                id = PointerId(1L), uptimeMillis = clock.currentTime, position = position, pressed = pressed,
                previousUptimeMillis = previousTime, previousPosition = previousPosition,
                previousPressed = previousPressed, isInitiallyConsumed = false, type = PointerType.Touch
            )
            if (consumed) change.consume()
            previousPosition = position
            previousPressed = pressed
            previousTime = clock.currentTime
            val continuation = checkNotNull(pending)
            pending = null
            awaitingPass = null
            currentEvent = PointerEvent(listOf(change))
            continuation.resume(currentEvent)
            return change
        }
    }

    private class Trace {
        val starts = mutableListOf<Long>()
        val deltas = mutableListOf<Offset>()
        var commits = 0
        var rollbacks = 0
        var taps = 0
    }

    private fun TestScope.detector(events: Events, ownership: PanelGestureOwnership, trace: Trace) =
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Only the event-await receiver is restricted. Enter the detector through
            // its real PointerInputScope type, rather than leaking the fake's second interface.
            val input: PointerInputScope = events
            input.detectStationaryDragGestures(
                ownership = ownership,
                onTap = { trace.taps++ },
                onDragStart = {
                    assertTrue(ownership.horizontalActive)
                    assertTrue(ownership.verticalActive)
                    trace.starts += testScheduler.currentTime
                },
                onDragEnd = { trace.commits++ },
                onDragCancel = { trace.rollbacks++ },
                onDrag = { _, delta -> trace.deltas += delta }
            )
        }

    @Test fun downAndJitterLeaveScrollingFreeUntilTimeoutThenUpCommitsActualDrag() = runTest {
        val events = Events(testScheduler, backgroundScope)
        val ownership = PanelGestureOwnership()
        val trace = Trace()
        val job = detector(events, ownership, trace)
        events.send(PointerEventPass.Main, Offset(100f, 100f), true)
        runCurrent()
        assertFalse(ownership.active)
        advanceTimeBy(100)
        events.send(PointerEventPass.Final, Offset(100.5f, 100.5f), true)
        runCurrent()
        assertFalse(ownership.active)
        advanceTimeBy(399)
        runCurrent()
        assertTrue(trace.starts.isEmpty())
        assertFalse(ownership.verticalActive)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(500L), trace.starts)
        assertTrue(ownership.active)
        events.send(PointerEventPass.Main, Offset(120.5f, 130.5f), true)
        runCurrent()
        assertEquals(listOf(Offset(20f, 30f)), trace.deltas)
        events.send(PointerEventPass.Main, Offset(120.5f, 130.5f), false)
        runCurrent()
        assertEquals(1, trace.commits)
        assertEquals(0, trace.rollbacks)
        assertFalse(ownership.active)
        job.cancelAndJoin()
    }

    @Test fun movementConsumedByOuterScrollAbandonsHoldWithoutStartingDrag() = runTest {
        val events = Events(testScheduler, backgroundScope)
        val ownership = PanelGestureOwnership()
        val trace = Trace()
        val job = detector(events, ownership, trace)
        events.send(PointerEventPass.Main, Offset(100f, 100f), true)
        runCurrent()
        advanceTimeBy(50)
        // Within the 3dp drift budget: consumption itself must cancel the hold.
        events.send(PointerEventPass.Final, Offset(100f, 101f), true, consumed = true)
        runCurrent()
        assertFalse(ownership.active)
        events.send(PointerEventPass.Final, Offset(100f, 101f), false, consumed = true)
        runCurrent()
        advanceTimeBy(600)
        runCurrent()
        assertTrue(trace.starts.isEmpty())
        assertEquals(0, trace.commits)
        assertEquals(0, trace.rollbacks)
        assertEquals(0, trace.taps)
        assertFalse(ownership.active)
        job.cancelAndJoin()
    }

    @Test fun consumedCancelReleaseRollsBackInsteadOfCommitting() = runTest {
        val events = Events(testScheduler, backgroundScope)
        val ownership = PanelGestureOwnership()
        val trace = Trace()
        val job = detector(events, ownership, trace)
        events.send(PointerEventPass.Main, Offset(100f, 100f), true)
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        events.send(PointerEventPass.Main, Offset(125f, 145f), true)
        runCurrent()
        events.send(PointerEventPass.Main, Offset(125f, 145f), false, consumed = true)
        runCurrent()
        assertEquals(0, trace.commits)
        assertEquals(1, trace.rollbacks)
        assertFalse(ownership.active)
        job.cancelAndJoin()
    }

    @Test fun coroutineCancellationDuringWaitAndDuringDragAlwaysReleasesOwnership() = runTest {
        for (afterHold in listOf(false, true)) {
            val events = Events(testScheduler, backgroundScope)
            val ownership = PanelGestureOwnership()
            val trace = Trace()
            val job = detector(events, ownership, trace)
            events.send(PointerEventPass.Main, Offset(100f, 100f), true)
            runCurrent()
            if (afterHold) {
                advanceTimeBy(500)
                runCurrent()
                assertTrue(ownership.active)
            } else {
                advanceTimeBy(100)
                assertFalse(ownership.active)
            }
            job.cancelAndJoin()
            assertFalse(ownership.horizontalActive)
            assertFalse(ownership.verticalActive)
            assertEquals(0, trace.commits)
            assertEquals(if (afterHold) 1 else 0, trace.rollbacks)
        }
    }

    @Test fun fastUnobservedReleaseOutsideStationaryBudgetDoesNotTapOrStartDrag() = runTest {
        val events = Events(testScheduler, backgroundScope)
        val ownership = PanelGestureOwnership()
        val trace = Trace()
        val job = detector(events, ownership, trace)
        events.send(PointerEventPass.Main, Offset(100f, 100f), true)
        runCurrent()
        advanceTimeBy(100)
        // A fast swipe can deliver DOWN and UP without any intervening MOVE event.
        events.send(PointerEventPass.Final, Offset(100f, 200f), false)
        runCurrent()
        assertTrue(trace.starts.isEmpty())
        assertEquals(0, trace.taps)
        assertEquals(0, trace.commits)
        assertEquals(0, trace.rollbacks)
        assertFalse(ownership.active)
        job.cancelAndJoin()
    }

    @Test fun fastUnobservedReleaseWithinStationaryBudgetStillTaps() = runTest {
        val events = Events(testScheduler, backgroundScope)
        val ownership = PanelGestureOwnership()
        val trace = Trace()
        val job = detector(events, ownership, trace)
        events.send(PointerEventPass.Main, Offset(100f, 100f), true)
        runCurrent()
        advanceTimeBy(100)
        events.send(PointerEventPass.Final, Offset(101f, 101f), false)
        runCurrent()
        assertEquals(1, trace.taps)
        assertTrue(trace.starts.isEmpty())
        assertEquals(0, trace.commits)
        assertEquals(0, trace.rollbacks)
        assertFalse(ownership.active)
        job.cancelAndJoin()
    }

    @Test fun heldDragCanMoveAboveViewportAndCommitsOnlyOnActualUp() = runTest {
        val events = Events(testScheduler, backgroundScope)
        val ownership = PanelGestureOwnership()
        val trace = Trace()
        val job = detector(events, ownership, trace)
        events.send(PointerEventPass.Main, Offset(100f, 100f), true)
        runCurrent()
        advanceTimeBy(500)
        runCurrent()

        assertEquals(1, trace.starts.size)
        assertTrue(ownership.active)
        events.send(PointerEventPass.Main, Offset(120f, -1f), true)
        runCurrent()
        assertTrue("The gesture remains owned after moving above the viewport", ownership.active)
        assertEquals(0, trace.commits)
        assertEquals(0, trace.rollbacks)

        events.send(PointerEventPass.Main, Offset(120f, -1f), false)
        runCurrent()
        assertEquals(1, trace.commits)
        assertEquals(0, trace.rollbacks)
        assertFalse(ownership.active)
        job.cancelAndJoin()
    }
}
