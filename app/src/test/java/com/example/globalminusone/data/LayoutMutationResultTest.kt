package com.example.globalminusone.data

import android.content.SharedPreferences
import com.example.globalminusone.MemoryPreferences
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.data.operations.findComponent

class LayoutMutationResultTest {
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
    private data class Fixture(val prefs: FaultPreferences, val repo: MinusOneRepository, val pageId: String)
    private fun fixture(): Fixture {
        val prefs = FaultPreferences()
        val repo = MinusOneRepository(prefs)
        val nested = ComponentItem(id = "child", type = ComponentType.NOTE_CARD, bgColorHex = 0xFF112233L, rotationDeg = 30)
        val folder = ComponentItem(id = "folder", type = ComponentType.FOLDER, containerComponents = listOf(nested))
        assertTrue(repo.updatePageConfig(0) { it.copy(layoutType = PageLayoutType.GRID, components = listOf(folder)) })
        return Fixture(prefs, repo, repo.state.value.pages[0].id)
    }
    private enum class Command { DELETE, RESET_STYLE, LAYOUT, TOGGLE_LAYOUT, FIT_COLUMNS, UPDATE_SIZE }
    private fun execute(command: Command, repo: MinusOneRepository, pageId: String, id: String = "child"): LayoutMutationResult = when (command) {
        Command.DELETE -> repo.tryRemoveComponent(pageId, id)
        Command.RESET_STYLE -> repo.tryResetComponentStyle(pageId, id)
        Command.LAYOUT -> repo.trySetPageLayoutType(pageId, PageLayoutType.FREE)
        Command.TOGGLE_LAYOUT -> repo.tryTogglePageLayoutType(pageId)
        Command.FIT_COLUMNS -> repo.tryFitPageGridColumnCount(pageId, 4, 380f)
        Command.UPDATE_SIZE -> repo.tryUpdateComponent(pageId, id) { it.copy(h = 7) }
    }

    @Test fun rejectedWritesKeepPublishedStateAndCanRetryEachActualAlgorithm() {
        for (command in Command.values()) {
            val (prefs, repo, pageId) = fixture()
            val before = repo.state.value
            prefs.rejectNext = true
            assertEquals(command.name, LayoutMutationResult.WRITE_REJECTED, execute(command, repo, pageId))
            assertEquals(command.name, before, repo.state.value)
            assertNotNull(repo.persistenceError.value)
            assertEquals(command.name, LayoutMutationResult.APPLIED, execute(command, repo, pageId))
            assertNull(repo.persistenceError.value)
            val page = repo.state.value.pages.first { it.id == pageId }
            val child = page.components.findComponent("child")
            when (command) {
                Command.DELETE -> assertNull(child)
                Command.RESET_STYLE -> { assertNull(child!!.bgColorHex); assertEquals(0, child.rotationDeg) }
                Command.LAYOUT, Command.TOGGLE_LAYOUT -> assertEquals(PageLayoutType.FREE, page.layoutType)
                Command.FIT_COLUMNS -> assertEquals(4, page.gridColumnCount)
                Command.UPDATE_SIZE -> assertEquals(7, child!!.h)
            }
        }
    }

    @Test fun lockMissingPageAndMissingNestedCardNeverReportAcceptance() {
        for (command in Command.values()) {
            val (_, repo, pageId) = fixture()
            assertEquals(LayoutMutationResult.TARGET_CHANGED, execute(command, repo, "missing"))
            repo.setLayoutLocked(true)
            val locked = repo.state.value
            assertEquals(LayoutMutationResult.TARGET_CHANGED, execute(command, repo, pageId))
            assertEquals(locked, repo.state.value)
            // Preserve legacy acceptance semantics; new commands report target outcomes above.
            assertTrue(repo.removeComponent(0, "child"))
            repo.setLayoutLocked(false)
            assertTrue(repo.removeComponent(0, "missing"))
            if (command in listOf(Command.DELETE, Command.RESET_STYLE, Command.UPDATE_SIZE)) {
                assertEquals(LayoutMutationResult.TARGET_CHANGED, execute(command, repo, pageId, "missing"))
                assertTrue(repo.removeComponent(0, "child"))
                assertEquals(LayoutMutationResult.TARGET_CHANGED, execute(command, repo, pageId))
            }
        }
    }

    @Test fun stablePageIdentityFollowsReorderingForEveryCommand() {
        for (command in Command.values()) {
            val (_, repo, pageId) = fixture()
            assertTrue(repo.addPage(PageLayoutType.LINEAR, "other") >= 0)
            assertTrue(repo.swapPageRight(0))
            val other = repo.state.value.pages[0]
            assertEquals(pageId, repo.state.value.pages[1].id)
            assertEquals(command.name, LayoutMutationResult.APPLIED, execute(command, repo, pageId))
            assertEquals(command.name, other, repo.state.value.pages[0])
            assertEquals(pageId, repo.state.value.pages[1].id)
        }
    }

    @Test fun validUnchangedCommandsAreAcceptedIncludingStyleAndLayout() {
        val (_, repo, pageId) = fixture()
        assertEquals(LayoutMutationResult.APPLIED, repo.trySetPageLayoutType(pageId, PageLayoutType.GRID))
        assertEquals(LayoutMutationResult.APPLIED, repo.tryUpdateComponent(pageId, "child") { it })
        assertEquals(LayoutMutationResult.APPLIED, repo.tryResetComponentStyle(pageId, "child"))
        val reset = repo.state.value
        assertEquals(LayoutMutationResult.APPLIED, repo.tryResetComponentStyle(pageId, "child"))
        assertEquals(reset, repo.state.value)
        assertEquals(LayoutMutationResult.APPLIED, repo.tryFitPageGridColumnCount(pageId, 4, 380f))
        val fitted = repo.state.value
        assertEquals(LayoutMutationResult.APPLIED, repo.tryFitPageGridColumnCount(pageId, 4, 380f))
        assertEquals(fitted, repo.state.value)
    }
}
