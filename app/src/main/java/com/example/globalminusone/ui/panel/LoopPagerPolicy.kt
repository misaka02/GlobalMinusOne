package com.example.globalminusone.ui.panel

/** Virtual slots are lazy Compose pages; only real indices ever enter persisted state. */
internal class LoopPagerPolicy(val pageIds: List<String>) {
    val realPageCount = pageIds.size.coerceAtLeast(1)
    val virtualPageCount = if (realPageCount > 1) Int.MAX_VALUE else 1

    fun realIndex(virtualPage: Int): Int = Math.floorMod(virtualPage, realPageCount)

    fun initialVirtualPage(realIndex: Int): Int {
        if (realPageCount == 1) return 0
        val middle = virtualPageCount.toLong() / 2
        return (middle - middle % realPageCount + realIndex.coerceIn(0, realPageCount - 1))
            .coerceAtMost(virtualPageCount.toLong() - 1).toInt()
    }

    fun targetVirtualPage(currentVirtual: Int, requestedReal: Int, direction: Int = 0): Int {
        if (realPageCount == 1) return 0
        val target = requestedReal.coerceIn(0, realPageCount - 1)
        val current = currentVirtual.coerceIn(0, virtualPageCount - 1).toLong()
        val currentReal = realIndex(current.toInt())
        if (currentReal == target) return current.toInt()
        val forward = Math.floorMod(target - currentReal, realPageCount).toLong()
        val backward = forward - realPageCount
        val distances = when {
            direction > 0 -> listOf(forward, backward)
            direction < 0 -> listOf(backward, forward)
            forward <= -backward -> listOf(forward, backward)
            else -> listOf(backward, forward)
        }
        return distances.map { current + it }
            .first { it >= 0 && it < virtualPageCount }.toInt()
    }

    // Keys remain tied to page identity inside each virtual cycle, including reordered lists.
    fun key(virtualPage: Int): String =
        "${virtualPage / realPageCount}:${pageIds.getOrNull(realIndex(virtualPage)) ?: "empty"}"
}

internal data class LoopPagerNavigation(val sequence: Long, val realIndex: Int, val direction: Int)

internal data class LoopPagerSnapshot(
    val repositoryIndex: Int,
    val currentVirtualPage: Int,
    val settledVirtualPage: Int,
    val scrolling: Boolean,
    val navigation: LoopPagerNavigation?
)

internal sealed interface LoopPagerDecision {
    data class Navigate(val virtualPage: Int) : LoopPagerDecision
    data class Persist(val realIndex: Int) : LoopPagerDecision
}

/** One collector owns both navigation and settled writes, so an old settle cannot undo a request. */
internal class LoopPagerReconciler(private val policy: LoopPagerPolicy, initialRealIndex: Int) {
    private var observedRepositoryIndex = initialRealIndex.coerceIn(0, policy.realPageCount - 1)
    private var consumedNavigationSequence = 0L

    fun reconcile(snapshot: LoopPagerSnapshot): LoopPagerDecision? {
        val repositoryIndex = snapshot.repositoryIndex.coerceIn(0, policy.realPageCount - 1)
        val navigation = snapshot.navigation
        val newNavigation = navigation != null && navigation.sequence != consumedNavigationSequence
        if (newNavigation || repositoryIndex != observedRepositoryIndex) {
            if (navigation != null) consumedNavigationSequence = navigation.sequence
            observedRepositoryIndex = repositoryIndex
            val direction = if (newNavigation && navigation?.realIndex == repositoryIndex) navigation?.direction ?: 0 else 0
            val target = policy.targetVirtualPage(snapshot.currentVirtualPage, repositoryIndex, direction)
            return if (target != snapshot.currentVirtualPage || snapshot.scrolling) {
                LoopPagerDecision.Navigate(target)
            } else null
        }
        if (!snapshot.scrolling) {
            val settled = policy.realIndex(snapshot.settledVirtualPage)
            if (settled != repositoryIndex) {
                observedRepositoryIndex = settled
                return LoopPagerDecision.Persist(settled)
            }
        }
        return null
    }
}
