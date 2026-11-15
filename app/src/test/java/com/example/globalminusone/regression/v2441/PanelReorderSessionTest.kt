package com.example.globalminusone.regression.v2441

import com.example.globalminusone.MemoryPreferences
import com.example.globalminusone.data.*
import com.example.globalminusone.ui.panel.hasLinearCardTools
import org.junit.Assert.*
import org.junit.Test

class PanelReorderSessionTest {
    private val order = listOf("a", "b", "c")
    private fun repository(prefs: MemoryPreferences = MemoryPreferences()) = MinusOneRepository(prefs).apply {
        updatePageConfig(0) { it.copy(id = "line", layoutType = PageLayoutType.LINEAR,
            components = order.map { id -> ComponentItem(id = id, type = ComponentType.TEXT_LABEL, title = id) }) }
    }
    private fun MinusOneRepository.ids() = state.value.pages.first { it.id == "line" }.components.map { it.id }

    @Test fun backDismissesCardToolsBeforeResolvingTheEdit() {
        val repo = repository()
        assertTrue(repo.setEditMode(true))
        assertTrue(repo.state.value.hasLinearCardTools())
        repo.selectComponent(null)
        assertTrue(repo.state.value.editMode)
        assertFalse(repo.state.value.hasLinearCardTools())
        assertFalse(repo.hasPanelEditChanges())
        repo.selectComponent("not-a-card")
        assertFalse(repo.state.value.hasLinearCardTools())
    }

    @Test fun dropIsDraftUntilExplicitSaveAndClearingToolsDoesNotLoseIt() {
        val prefs = MemoryPreferences()
        val repo = repository(prefs)
        assertNull(repo.beginLinearReorder("line"))
        repo.setEditMode(true)
        val drag = requireNotNull(repo.beginLinearReorder("line"))
        assertTrue(repo.commitLinearReorder(drag, listOf("c", "a", "b")))
        repo.selectComponent(null)
        assertTrue(repo.state.value.editMode)
        assertTrue(repo.hasPanelEditChanges())
        assertEquals(order, MinusOneRepository(prefs).ids())
        assertTrue(repo.finishPanelEdit(save = true))
        assertEquals(listOf("c", "a", "b"), MinusOneRepository(prefs).ids())
        assertFalse(repo.hasPanelEditChanges())
    }

    @Test fun droppingThenDiscardingRestoresOrderAndThemeTogether() {
        val prefs = MemoryPreferences()
        val repo = repository(prefs)
        val oldTheme = repo.state.value.themeConfig
        repo.setEditMode(true)
        assertTrue(repo.commitLinearReorder(requireNotNull(repo.beginLinearReorder("line")), order.reversed()))
        repo.updateThemeConfig { it.copy(pageTransition = PageTransition.SCALE) }
        assertTrue(repo.hasPanelEditChanges())
        assertTrue(repo.finishPanelEdit(save = false))
        assertEquals(order, repo.ids())
        assertEquals(oldTheme, repo.state.value.themeConfig)
        assertEquals(order, MinusOneRepository(prefs).ids())
    }

    @Test fun oldSessionCannotDropIntoNewSessionEvenWithIdenticalConfiguration() {
        val repo = repository()
        repo.setEditMode(true)
        val stale = requireNotNull(repo.beginLinearReorder("line"))
        repo.finishPanelEdit(save = false)
        assertFalse(repo.commitLinearReorder(stale, order.reversed()))
        repo.setEditMode(true)
        assertFalse(repo.commitLinearReorder(stale, order.reversed()))
        assertFalse(repo.hasPanelEditChanges())
        assertEquals(order, repo.ids())
    }

    @Test fun dropUsesPageIdentityAndKeepsLatestObjects() {
        val repo = repository()
        repo.setEditMode(true)
        val drag = requireNotNull(repo.beginLinearReorder("line"))
        repo.updateComponent(0, "a") { it.copy(title = "edited while dragging", h = 17) }
        assertTrue(repo.swapPageRight(0))
        assertTrue(repo.commitLinearReorder(drag, order.reversed()))
        val page = repo.state.value.pages.first { it.id == "line" }
        assertEquals(order.reversed(), page.components.map { it.id })
        assertEquals(17, page.components.last().h)
        assertEquals("edited while dragging", page.components.last().title)
    }

    @Test fun staleTreeInvalidOrderAndChangedLayoutRejectDrop() {
        val repo = repository()
        repo.setEditMode(true)
        val drag = requireNotNull(repo.beginLinearReorder("line"))
        assertFalse(repo.commitLinearReorder(drag, null))
        assertFalse(repo.commitLinearReorder(drag, listOf("a", "a", "c")))
        repo.removeComponent(0, "b")
        assertFalse(repo.commitLinearReorder(drag, order.reversed()))
        assertEquals(listOf("a", "c"), repo.ids())
        repo.updatePageConfig(0) { it.copy(layoutType = PageLayoutType.GRID) }
        assertNull(repo.beginLinearReorder("line"))
    }

    @Test fun themeOnlyEditIsDirtyAndSavedWithExplicitCompletion() {
        val prefs = MemoryPreferences()
        val repo = repository(prefs)
        repo.setEditMode(true)
        val old = repo.state.value.themeConfig.animationDurationMs
        repo.updateThemeConfig { it.copy(animationDurationMs = old + 1) }
        assertTrue(repo.hasPanelEditChanges())
        assertEquals(old, MinusOneRepository(prefs).state.value.themeConfig.animationDurationMs)
        assertTrue(repo.finishPanelEdit(save = true))
        assertEquals(old + 1, MinusOneRepository(prefs).state.value.themeConfig.animationDurationMs)
    }
}
