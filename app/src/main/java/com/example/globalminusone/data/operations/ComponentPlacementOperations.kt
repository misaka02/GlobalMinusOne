package com.example.globalminusone.data.operations

import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.PageLayoutType

/** Initial geometry only; destination checks and acceptance remain in the repository. */
internal object ComponentPlacementOperations {
    fun inPage(targetPage: PageConfig, item: ComponentItem, columns: Int, nextY: () -> Int): ComponentItem {
        return when (targetPage.layoutType) {
            PageLayoutType.FREE -> {
                val clampedW = item.w.coerceIn(1, columns)
                val clampedH = item.h.coerceAtLeast(1)
                val resolvedY = if (item.y == 0 && targetPage.components.isNotEmpty()) {
                    nextY()
                } else {
                    item.y.coerceAtLeast(0)
                }
                val resolvedX = if (item.x == 0 && targetPage.components.isNotEmpty()) {
                    ((columns - clampedW) / 2).coerceIn(0, columns - clampedW)
                } else {
                    item.x.coerceIn(0, (columns - clampedW).coerceAtLeast(0))
                }
                item.copy(x = resolvedX, y = resolvedY, w = clampedW, h = clampedH)
            }
            PageLayoutType.GRID -> item.copy(
                w = item.w.coerceIn(1, columns),
                h = item.h.coerceAtLeast(1)
            )
            PageLayoutType.LINEAR -> item.copy(w = columns)
        }
    }

    fun inContainer(item: ComponentItem, columns: Int, nextY: () -> Int): ComponentItem {
        val width = item.w.coerceIn(1, columns)
        return item.copy(
            x = item.x.coerceIn(0, columns - width),
            y = if (item.y == 0) nextY() else item.y.coerceAtLeast(0),
            w = width, h = item.h.coerceAtLeast(1)
        )
    }
}
