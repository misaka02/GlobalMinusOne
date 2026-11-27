package com.example.globalminusone.ui.panel

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.ui.panel.container.FolderDragOrder
import com.example.globalminusone.ui.panel.container.FolderDragSlot
import com.example.globalminusone.ui.panel.container.StableDragMotion

class FolderDragStabilityTest {
    @Test fun movingTileAndScrollingCanvasCannotMoveAStationaryPhysicalPointer() {
        val motion = StableDragMotion(Offset(120f, 300f))
        // Each event is delivered relative to the tile's *new* position. Include the
        // translating popup origin and canvas scroll when mapping back to screen space.
        var total = Offset.Zero
        listOf(Offset(20f, 40f), Offset(30f, 60f), Offset(-10f, 100f)).forEach { origin ->
            val local = Offset(120f, 300f) - origin
            total += motion.sample(origin + local)
        }
        assertEquals(Offset.Zero, total)
        assertEquals(Offset(11f, -23f), motion.sample(Offset(131f, 277f)))
        assertEquals(Offset.Zero, motion.sample(Offset(Float.NaN, 999f)))
        assertEquals(Offset(1f, 1f), motion.sample(Offset(132f, 278f)))
    }

    private val slots = listOf(
        FolderDragSlot("a", 0f, 0f, 10f, 10f),
        FolderDragSlot("b", 12f, 0f, 10f, 10f),
        FolderDragSlot("c", 0f, 12f, 10f, 10f),
        FolderDragSlot("d", 12f, 12f, 10f, 10f))

    @Test fun previewCannotFlipBackWhenAnimatedSiblingMovesOutOfTheDestination() {
        val drag = FolderDragOrder("a", slots)
        repeat(30) { assertEquals(listOf("b", "a", "c", "d"), drag.preview(12f, 0f)) }
        assertEquals(listOf("b", "c", "d", "a"), drag.preview(12f, 12f))
        assertEquals(listOf("b", "a", "c", "d"), drag.preview(12f, 0f))
        assertEquals(listOf("a", "b", "c", "d"), drag.preview(0f, 0f))
    }

    @Test fun nonuniformCardsGapsAndReverseDraggingRetainEveryIdExactlyOnce() {
        val mixed = listOf(FolderDragSlot("tall", 0f, 0f, 20f, 40f),
            FolderDragSlot("short", 24f, 0f, 10f, 10f), FolderDragSlot("wide", 0f, 44f, 34f, 10f))
        val drag = FolderDragOrder("wide", mixed)
        assertEquals(listOf("wide", "tall", "short"), drag.preview(-7f, 15f))
        assertEquals(listOf("tall", "wide", "short"), drag.preview(12f, 0f))
        listOf(-100f, 0f, 20f, 200f).forEach { x ->
            val result = drag.preview(x, 20f)
            assertEquals(3, result.size)
            assertEquals(mixed.map { it.id }.toSet(), result.toSet())
        }
    }
}
