package com.example.globalminusone.regression.v2420

import com.example.globalminusone.system.BrowserEntry
import com.example.globalminusone.system.BrowserLocation
import com.example.globalminusone.ui.panel.FileDeletionPlan
import org.junit.Assert.*
import org.junit.Test

class FileDeletionPlanTest {
    private val current = BrowserLocation("Current", path = "/storage/emulated/0/Current")
    private val nested = BrowserLocation("Nested", path = "/storage/emulated/0/Other/Nested")
    private fun file(name: String, parent: BrowserLocation? = null, blocked: String? = null) = BrowserEntry(
        BrowserLocation(name, path = "${(parent ?: current).path}/$name"),
        name, false, "text/plain", 10L, 1L, blocked, parent
    )

    @Test fun changingSelectionAfterRequestCannotChangeConfirmedBatch() {
        val first = file("first.txt")
        val second = file("second.txt")
        val mutableSelection = mutableListOf(first, second)
        val plan = FileDeletionPlan.create(mutableSelection, current)
        mutableSelection.clear()
        mutableSelection.add(file("new.txt"))
        assertEquals(listOf("first.txt", "second.txt"), plan.targets.map { it.entry.name })
        assertEquals(listOf(current, current), plan.targets.map { it.parent })
    }

    @Test fun recursiveSearchResultsKeepTheirOwnParentAndDuplicateKeysAreNotDeletedTwice() {
        val nestedFile = file("nested.txt", nested)
        val localFile = file("local.txt")
        val plan = FileDeletionPlan.create(listOf(nestedFile, localFile, nestedFile.copy()), current)
        assertEquals(2, plan.targets.size)
        assertEquals(nested, plan.targets[0].parent)
        assertEquals(current, plan.targets[1].parent)
    }

    @Test fun emptyOrBlockedBatchCannotBecomeDeletionConfirmation() {
        assertThrows(IllegalArgumentException::class.java) { FileDeletionPlan.create(emptyList(), current) }
        assertThrows(IllegalArgumentException::class.java) {
            FileDeletionPlan.create(listOf(file("readonly.txt", blocked = "不可操作")), current)
        }
    }
}
