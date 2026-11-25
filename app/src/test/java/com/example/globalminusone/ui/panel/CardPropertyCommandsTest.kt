package com.example.globalminusone.ui.panel

import android.content.SharedPreferences
import com.example.globalminusone.MemoryPreferences
import com.example.globalminusone.data.*
import org.junit.Assert.*
import org.junit.Test

class CardPropertyCommandsTest {
    private class FaultPreferences(private val delegate: MemoryPreferences = MemoryPreferences()) : SharedPreferences by delegate {
        var rejectNext = false
        override fun edit(): SharedPreferences.Editor {
            val editor = delegate.edit()
            return object : SharedPreferences.Editor by editor {
                override fun putString(key: String?, value: String?): SharedPreferences.Editor { editor.putString(key, value); return this }
                override fun remove(key: String?): SharedPreferences.Editor { editor.remove(key); return this }
                override fun apply() {
                    if (rejectNext) { rejectNext = false; error("synthetic write rejection") }
                    editor.apply()
                }
            }
        }
    }
    private val child = ComponentItem(id = "child", type = ComponentType.NOTE_CARD,
        w = 2, h = 17, bgColorHex = 0xFF123456L, rotationDeg = 45, autoHeight = false)
    private val parent = ComponentItem(id = "parent", type = ComponentType.FOLDER, containerComponents = listOf(child))
    private fun fixture(): Pair<FaultPreferences, MinusOneRepository> {
        val prefs = FaultPreferences(); val repo = MinusOneRepository(prefs)
        assertTrue(repo.updatePageConfig(0) { it.copy(components = listOf(parent)) })
        return prefs to repo
    }

    @Test fun actualPropertyEntryRetainsStateOnRejectionThenAcceptsRetry() {
        for (action in CardPropertyAction.entries) {
            val (prefs, repo) = fixture()
            val target = PropertyCardTarget(repo.state.value.pages[0].id, child.id)
            val before = repo.state.value
            prefs.rejectNext = true
            assertEquals(action.name, LayoutMutationResult.WRITE_REJECTED, executeCardPropertyAction(repo, target, action))
            assertEquals(before, repo.state.value)
            assertNotNull(repo.persistenceError.value)
            assertEquals(action.name, LayoutMutationResult.APPLIED, executeCardPropertyAction(repo, target, action))
            val current = repo.state.value.pages[0].components.findComponent(child.id)
            when (action) {
                CardPropertyAction.DELETE -> assertNull(current)
                CardPropertyAction.RESET_STYLE -> { assertNull(current!!.bgColorHex); assertEquals(0, current.rotationDeg) }
                CardPropertyAction.RESTORE_SIZE -> {
                    val expected = restoreDefaultCardSize(child, before.advancedConfig.gridColumns)
                    assertEquals(expected.w, current!!.w); assertEquals(expected.h, current.h)
                    assertEquals(expected.autoHeight, current.autoHeight)
                }
            }
        }
    }

    @Test fun propertyCommandsFollowStablePageAndRejectLockedOrDeletedTargets() {
        for (action in CardPropertyAction.entries) {
            val (_, repo) = fixture()
            val target = PropertyCardTarget(repo.state.value.pages[0].id, child.id)
            repo.addPage(PageLayoutType.LINEAR, "other")
            assertTrue(repo.swapPageRight(0))
            val other = repo.state.value.pages[0]
            repo.setLayoutLocked(true)
            assertEquals(LayoutMutationResult.TARGET_CHANGED, executeCardPropertyAction(repo, target, action))
            repo.setLayoutLocked(false)
            assertEquals(LayoutMutationResult.APPLIED, executeCardPropertyAction(repo, target, action))
            assertEquals(other, repo.state.value.pages[0])
            if (action != CardPropertyAction.DELETE) assertTrue(repo.removeComponent(1, child.id))
            assertEquals(LayoutMutationResult.TARGET_CHANGED, executeCardPropertyAction(repo, target, action))
        }
    }

    @Test fun sizeRestorationUsesCurrentNestedParentAfterTreeChanges() {
        val (_, repo) = fixture()
        val file = ComponentItem(id = child.id, type = ComponentType.FILE_SHORTCUT, w = 2, h = 29, autoHeight = false)
        val target = PropertyCardTarget(repo.state.value.pages[0].id, child.id)
        // A previously opened editor must resolve the child's new parent when executing, not retain the old parent.
        val newParent = parent.copy(id = "new-parent", containerPopupWidthDp = 340, containerComponents = listOf(file))
        assertTrue(repo.updatePageConfig(0) { it.copy(components = listOf(parent.copy(containerComponents = emptyList()), newParent)) })
        val state = repo.state.value
        val expected = repo.prepareNewComponent(0, restoreDefaultCardSize(file, state.advancedConfig.gridColumns), newParent.id)
        assertEquals(LayoutMutationResult.APPLIED, executeCardPropertyAction(repo, target, CardPropertyAction.RESTORE_SIZE))
        val current = repo.state.value.pages[0].components.findComponent(child.id)!!
        assertEquals(expected.w, current.w); assertEquals(expected.h, current.h)
        assertEquals(expected.autoHeight, current.autoHeight)
        assertTrue(repo.state.value.pages[0].components.findComponent(parent.id)!!.containerComponents.isEmpty())
    }
}
