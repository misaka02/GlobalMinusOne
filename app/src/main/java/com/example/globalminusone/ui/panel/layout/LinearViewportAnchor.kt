package com.example.globalminusone.ui.panel.layout

/** A stable scroll position inside a card, with an index fallback if that card is removed. */
internal data class LinearViewportAnchor(
    val cardId: String,
    val offsetPx: Int,
    val fallbackIndex: Int
)

internal data class LinearCardSpan(
    val id: String,
    val startPx: Int,
    val heightPx: Int
)

internal fun anchorFromLazyPosition(
    cardIds: List<String>,
    index: Int,
    offsetPx: Int
): LinearViewportAnchor? {
    if (cardIds.isEmpty()) return null
    val safeIndex = index.coerceIn(0, cardIds.lastIndex)
    return LinearViewportAnchor(cardIds[safeIndex], offsetPx.coerceAtLeast(0), safeIndex)
}

internal fun lazyPositionForAnchor(
    anchor: LinearViewportAnchor,
    cardIds: List<String>
): Pair<Int, Int>? {
    if (cardIds.isEmpty()) return null
    val index = cardIds.indexOf(anchor.cardId).takeIf { it >= 0 }
        ?: anchor.fallbackIndex.coerceIn(0, cardIds.lastIndex)
    return index to anchor.offsetPx.coerceAtLeast(0)
}

/** Maps the editing canvas scroll coordinate to the first card intersecting the viewport top. */
internal fun anchorAtLinearScroll(
    cards: List<LinearCardSpan>,
    scrollPx: Int
): LinearViewportAnchor? {
    if (cards.isEmpty()) return null
    val scroll = scrollPx.coerceAtLeast(0)
    val index = cards.indexOfFirst { scroll < it.startPx + it.heightPx }
        .takeIf { it >= 0 } ?: cards.lastIndex
    val card = cards[index]
    val offset = (scroll - card.startPx).coerceIn(0, card.heightPx.coerceAtLeast(0))
    return LinearViewportAnchor(card.id, offset, index)
}

/** Maps a stable card anchor back to the editing canvas, falling back to its nearest old index. */
internal fun linearScrollForAnchor(
    anchor: LinearViewportAnchor,
    cards: List<LinearCardSpan>
): Int? {
    if (cards.isEmpty()) return null
    val index = cards.indexOfFirst { it.id == anchor.cardId }
        .takeIf { it >= 0 } ?: anchor.fallbackIndex.coerceIn(0, cards.lastIndex)
    val card = cards[index]
    val offset = anchor.offsetPx.coerceIn(0, card.heightPx.coerceAtLeast(0))
    return card.startPx + offset
}
