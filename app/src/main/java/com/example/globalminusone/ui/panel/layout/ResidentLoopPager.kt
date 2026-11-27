package com.example.globalminusone.ui.panel.layout

import androidx.compose.animation.core.animate
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import com.example.globalminusone.data.PageTransition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

internal data class ResidentVisibleSlot(
    val realIndex: Int,
    val pageOffset: Float,
    val zIndex: Float
)

internal fun resolveResidentVisiblePages(position: Float, pageCount: Int): List<ResidentVisibleSlot> {
    if (pageCount <= 0) return emptyList()
    if (pageCount == 1) return listOf(ResidentVisibleSlot(0, 0f, 1f))
    val baseFloor = floor(position.toDouble()).toInt()
    val fraction = position - baseFloor
    return when {
        fraction < 0.0005f -> listOf(
            ResidentVisibleSlot(Math.floorMod(baseFloor, pageCount), 0f, 1f)
        )
        fraction > 0.9995f -> listOf(
            ResidentVisibleSlot(Math.floorMod(baseFloor + 1, pageCount), 0f, 1f)
        )
        else -> listOf(
            ResidentVisibleSlot(Math.floorMod(baseFloor, pageCount), fraction, 1f),
            ResidentVisibleSlot(Math.floorMod(baseFloor + 1, pageCount), fraction - 1f, 2f)
        )
    }
}

internal fun resolveResidentSnapTarget(
    currentPosition: Float,
    dragStartPage: Int,
    velocityPagesPerSec: Float,
    positionalThreshold: Float = 0.38f,
    velocityThresholdPagesPerSec: Float = 1.15f
): Int {
    val delta = currentPosition - dragStartPage
    return if (abs(velocityPagesPerSec) >= velocityThresholdPagesPerSec) {
        if (velocityPagesPerSec > 0f) {
            if (delta < -0.05f) dragStartPage else dragStartPage + 1
        } else {
            if (delta > 0.05f) dragStartPage else dragStartPage - 1
        }
    } else {
        when {
            delta >= positionalThreshold -> dragStartPage + 1
            delta <= -positionalThreshold -> dragStartPage - 1
            else -> dragStartPage
        }
    }
}

internal fun resolveCircularStep(
    currentReal: Int,
    targetReal: Int,
    pageCount: Int,
    direction: Int = 0
): Int {
    if (pageCount <= 1) return 0
    val c = Math.floorMod(currentReal, pageCount)
    val t = Math.floorMod(targetReal, pageCount)
    if (c == t) return 0
    val forward = Math.floorMod(t - c, pageCount)
    val backward = forward - pageCount
    return when {
        direction > 0 -> forward
        direction < 0 -> backward
        forward <= -backward -> forward
        else -> backward
    }
}

/**
 * Keeps all real pages resident in composition so horizontal page swipes perform
 * zero cold compositions, zero View/Canvas disposals, and pure GPU layer transforms.
 */
@Stable
internal class ResidentLoopPagerState(
    initialRealPage: Int,
    val pageCount: Int
) {
    private val clampedInitial = initialRealPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    internal val positionState = mutableFloatStateOf(clampedInitial.toFloat())
    val position: Float get() = positionState.floatValue
    var settledRealPage by mutableIntStateOf(clampedInitial)
        internal set
    var isAnimatingOrDragging by mutableStateOf(false)
        internal set
    internal var pageWidthPx: Float = 1f
    internal var dragStartPage: Int = clampedInitial
    internal var dragActive: Boolean = false

    val currentRealPage: Int by derivedStateOf {
        if (pageCount <= 1) 0 else Math.floorMod(positionState.floatValue.roundToInt(), pageCount)
    }

    val isScrollInProgress: Boolean
        get() = isAnimatingOrDragging || scrollableState.isScrollInProgress

    val scrollableState = ScrollableState { deltaPx ->
        if (pageCount <= 1 || pageWidthPx <= 0f) return@ScrollableState 0f
        if (!dragActive) {
            dragActive = true
            dragStartPage = positionState.floatValue.roundToInt()
            isAnimatingOrDragging = true
        }
        positionState.floatValue += deltaPx / pageWidthPx
        deltaPx
    }

    internal fun normalizeSettled(targetPage: Int) {
        val real = if (pageCount <= 1) 0 else Math.floorMod(targetPage, pageCount)
        positionState.floatValue = real.toFloat()
        settledRealPage = real
        dragStartPage = real
        dragActive = false
        isAnimatingOrDragging = false
    }

    suspend fun animateToRealPage(targetReal: Int, direction: Int, animMs: Int) {
        if (pageCount <= 1) return
        scrollableState.scroll(MutatePriority.Default) {
            val curPos = positionState.floatValue
            val curBase = curPos.roundToInt()
            val step = resolveCircularStep(Math.floorMod(curBase, pageCount), targetReal, pageCount, direction)
            val targetVirtual = curBase + step
            isAnimatingOrDragging = true
            dragActive = false
            try {
                if (animMs <= 0 || abs(targetVirtual - curPos) < 0.0005f) {
                    positionState.floatValue = targetVirtual.toFloat()
                } else {
                    animate(
                        initialValue = curPos,
                        targetValue = targetVirtual.toFloat(),
                        initialVelocity = 0f,
                        animationSpec = resolvePagerSnapAnimationSpec(animMs)
                    ) { value, _ -> positionState.floatValue = value }
                }
                normalizeSettled(targetVirtual)
            } finally {
                dragActive = false
                isAnimatingOrDragging = false
            }
        }
    }
}

private class ResidentLoopPagerFlingBehavior(
    private val state: ResidentLoopPagerState,
    private val readAnimMs: () -> Int
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val width = state.pageWidthPx.coerceAtLeast(1f)
        val startPage = if (state.dragActive) state.dragStartPage else state.positionState.floatValue.roundToInt()
        val velPages = initialVelocity / width
        val target = resolveResidentSnapTarget(state.positionState.floatValue, startPage, velPages)
        val animMs = readAnimMs()
        state.dragActive = false
        try {
            if (animMs <= 0 || abs(target - state.positionState.floatValue) < 0.0005f) {
                state.positionState.floatValue = target.toFloat()
            } else {
                animate(
                    initialValue = state.positionState.floatValue,
                    targetValue = target.toFloat(),
                    initialVelocity = velPages,
                    animationSpec = resolvePagerSnapAnimationSpec(animMs)
                ) { value, _ -> state.positionState.floatValue = value }
            }
            state.normalizeSettled(target)
        } finally {
            state.dragActive = false
            state.isAnimatingOrDragging = false
        }
        return 0f
    }
}

@Composable
internal fun rememberResidentLoopPagerState(initialRealPage: Int, pageCount: Int): ResidentLoopPagerState {
    val normalizedCount = pageCount.coerceAtLeast(1)
    return remember(normalizedCount) {
        ResidentLoopPagerState(initialRealPage = initialRealPage, pageCount = normalizedCount)
    }
}

@Composable
internal fun ResidentLoopPager(
    state: ResidentLoopPagerState,
    pageKey: (Int) -> Any,
    transition: PageTransition,
    pageSwitchMs: Int,
    userScrollEnabled: Boolean,
    modifier: Modifier = Modifier,
    pageContent: @Composable (realPageIndex: Int) -> Unit
) {
    val latestAnimMs by rememberUpdatedState(pageSwitchMs)
    val flingBehavior = remember(state) { ResidentLoopPagerFlingBehavior(state) { latestAnimMs } }
    val reverseDirection = ScrollableDefaults.reverseDirection(
        LocalLayoutDirection.current,
        Orientation.Horizontal,
        reverseScrolling = false
    )
    val density = LocalDensity.current.density

    LaunchedEffect(state) {
        snapshotFlow { state.scrollableState.isScrollInProgress }.collect { active ->
            if (!active && !state.isAnimatingOrDragging) return@collect
            if (!active && abs(state.position - state.position.roundToInt()) > 0.001f) {
                val nearestReal = if (state.pageCount <= 1) 0 else Math.floorMod(state.position.roundToInt(), state.pageCount)
                try {
                    state.animateToRealPage(nearestReal, direction = 0, animMs = latestAnimMs)
                } catch (_: CancellationException) {
                    currentCoroutineContext().ensureActive()
                }
            } else if (!active && abs(state.position - state.position.roundToInt()) <= 0.001f) {
                state.normalizeSettled(state.position.roundToInt())
            }
        }
    }

    Layout(
        content = {
            for (realIndex in 0 until state.pageCount) {
                key(pageKey(realIndex)) {
                    pageContent(realIndex)
                }
            }
        },
        modifier = modifier
            .clipToBounds()
            .scrollable(
                state = state.scrollableState,
                orientation = Orientation.Horizontal,
                enabled = userScrollEnabled && state.pageCount > 1,
                reverseDirection = reverseDirection,
                flingBehavior = flingBehavior
            )
    ) { measurables, constraints ->
        val width = constraints.maxWidth.coerceAtLeast(1)
        val height = constraints.maxHeight.coerceAtLeast(1)
        state.pageWidthPx = width.toFloat()
        val childConstraints = Constraints.fixed(width, height)
        val placeables = measurables.map { it.measure(childConstraints) }
        layout(width, height) {
            val pos = state.position
            val visibleSlots = resolveResidentVisiblePages(pos, placeables.size)
            val widthF = width.toFloat()
            val heightF = height.toFloat()
            for (i in placeables.indices) {
                val slot = visibleSlots.firstOrNull { it.realIndex == i }
                if (slot != null) {
                    placeables[i].placeWithLayer(0, 0, zIndex = slot.zIndex) {
                        applyPageTransitionAnimation(
                            transition = transition,
                            pageOffset = slot.pageOffset,
                            width = widthF,
                            height = heightF,
                            density = density
                        )
                        translationX += -slot.pageOffset * widthF
                    }
                } else {
                    placeables[i].place(width * 2, 0, zIndex = 0f)
                }
            }
        }
    }
}

@Composable
internal fun BindResidentLoopPagerReconciler(
    pagerState: ResidentLoopPagerState,
    initialLocalPage: Int,
    visibleTrueIndices: List<Int>,
    readRepositoryIndex: () -> Int?,
    readNavigation: () -> LoopPagerNavigation?,
    readAnimMs: () -> Int,
    onPersistTruePage: (Int) -> Unit
) {
    val latestRepoIndex by rememberUpdatedState(readRepositoryIndex)
    val latestNav by rememberUpdatedState(readNavigation)
    val latestAnimMs by rememberUpdatedState(readAnimMs)
    val latestPersist by rememberUpdatedState(onPersistTruePage)
    val latestVisibleTrueIndices by rememberUpdatedState(visibleTrueIndices)
    LaunchedEffect(pagerState, visibleTrueIndices) {
        var observedRepo = initialLocalPage.coerceIn(0, (pagerState.pageCount - 1).coerceAtLeast(0))
        var consumedSeq = 0L
        var persistJob: Job? = null
        var pendingTruePage: Int? = null
        try {
            snapshotFlow {
                val repoIndex = latestRepoIndex() ?: return@snapshotFlow null
                listOf(repoIndex, pagerState.settledRealPage, pagerState.isScrollInProgress, latestNav())
            }.collect { event ->
                val repoRaw = event?.get(0) as? Int ?: return@collect
                val repoIdx = repoRaw.coerceIn(0, (pagerState.pageCount - 1).coerceAtLeast(0))
                val settled = event[1] as Int
                val scrolling = event[2] as Boolean
                val nav = event[3] as? LoopPagerNavigation
                val newNav = nav != null && nav.sequence != consumedSeq
                if (newNav || repoIdx != observedRepo) {
                    if (nav != null) consumedSeq = nav.sequence
                    observedRepo = repoIdx
                    val dir = if (newNav && nav?.realIndex == repoIdx) nav?.direction ?: 0 else 0
                    val needsMove = pagerState.currentRealPage != repoIdx ||
                        abs(pagerState.position - pagerState.position.roundToInt()) > 0.001f ||
                        scrolling
                    if (needsMove) {
                        persistJob?.cancel()
                        pendingTruePage = null
                        try {
                            pagerState.animateToRealPage(repoIdx, dir, latestAnimMs())
                        } catch (_: CancellationException) {
                            currentCoroutineContext().ensureActive()
                        }
                    }
                } else if (!scrolling && settled != repoIdx) {
                    observedRepo = settled
                    visibleTrueIndices.getOrNull(settled)?.let { targetTrue ->
                        persistJob?.cancel()
                        pendingTruePage = targetTrue
                        persistJob = launch {
                            delay(280L)
                            pendingTruePage = null
                            latestPersist(targetTrue)
                        }
                    }
                }
            }
        } finally {
            persistJob?.cancel()
            val pending = pendingTruePage
            if (pending != null && latestVisibleTrueIndices == visibleTrueIndices && pending in latestVisibleTrueIndices) {
                latestPersist(pending)
            }
        }
    }
}
