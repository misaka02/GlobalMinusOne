package com.example.globalminusone.ui.panel.cards

import java.text.Normalizer
import java.util.Locale

internal data class AppLetterGroup<T>(val letter: String, val apps: List<T>)

// transliterate is injected so this ordering policy can be tested without Android ICU.
internal fun appIndexLetter(label: String, transliterate: (String) -> String = { it }): String {
    val normalized = Normalizer.normalize(transliterate(label.trim()), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").uppercase(Locale.ROOT)
    return normalized.firstOrNull()?.takeIf { it in 'A'..'Z' }?.toString() ?: "#"
}

internal fun <T> groupAppsByLetter(apps: List<T>, label: (T) -> String, transliterate: (String) -> String): List<AppLetterGroup<T>> =
    apps.groupBy { appIndexLetter(label(it), transliterate) }
        .toSortedMap(compareBy<String> { if (it == "#") 1 else 0 }.thenBy { it })
        .map { (letter, items) -> AppLetterGroup(letter, items.sortedBy { label(it).lowercase(Locale.ROOT) }) }

// Grid indices include one full-width heading per group, independent of column count.
internal fun appGroupOffsets(groupSizes: List<Int>): List<Int> {
    var next = 0
    return groupSizes.map { size -> next.also { next += size + 1 } }
}

internal fun appIndexAtPosition(y: Float, height: Float, count: Int): Int? =
    if (height <= 0f || count <= 0 || !y.isFinite()) null
    else ((y / height) * count).toInt().coerceIn(0, count - 1)


internal fun <T> filterAppGroups(groups: List<AppLetterGroup<T>>, predicate: (T) -> Boolean): List<AppLetterGroup<T>> =
    groups.mapNotNull { group -> group.apps.filter(predicate).takeIf { it.isNotEmpty() }?.let { AppLetterGroup(group.letter, it) } }

/** Viewport geometry only: an offscreen remnant must not label the next visible group. */
internal data class AppViewportItem(val index: Int, val top: Int, val height: Int)
internal fun visibleAppGroupIndex(offsets: List<Int>, items: List<AppViewportItem>,
    viewportStart: Int, viewportEnd: Int, fallbackItemIndex: Int): Int? =
    visibleAppGroupIndex(offsets, items, viewportStart, viewportEnd, fallbackItemIndex,
        { it.index }, { it.top }, { it.height })

/** Reads the live viewport without copying Compose's visible item list on every scroll layout. */
internal inline fun <T> visibleAppGroupIndex(offsets: List<Int>, items: List<T>,
    viewportStart: Int, viewportEnd: Int, fallbackItemIndex: Int,
    indexOf: (T) -> Int, topOf: (T) -> Int, heightOf: (T) -> Int): Int? {
    if (offsets.isEmpty()) return null
    var firstFull = Int.MAX_VALUE
    var firstVisible = Int.MAX_VALUE
    if (viewportEnd > viewportStart) for (item in items) {
        val index = indexOf(item)
        val top = topOf(item).toLong()
        val height = heightOf(item)
        val bottom = top + height
        if (index < 0 || height <= 0 || top >= viewportEnd || bottom <= viewportStart) continue
        if (index < firstVisible) firstVisible = index
        if (top >= viewportStart && bottom <= viewportEnd && index < firstFull) firstFull = index
    }
    val itemIndex = when {
        firstFull != Int.MAX_VALUE -> firstFull
        firstVisible != Int.MAX_VALUE -> firstVisible
        else -> fallbackItemIndex
    }.coerceAtLeast(0)
    return offsets.indexOfLast { it <= itemIndex }.coerceIn(0, offsets.lastIndex)
}
