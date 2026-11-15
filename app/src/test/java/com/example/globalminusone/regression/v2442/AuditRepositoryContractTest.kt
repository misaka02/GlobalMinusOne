package com.example.globalminusone.regression.v2442

import android.content.SharedPreferences
import com.example.globalminusone.MemoryPreferences
import com.example.globalminusone.data.*
import org.junit.Assert.*
import org.junit.Test

class AuditRepositoryContractTest {
    private class FailingPreferences(private val delegate: MemoryPreferences = MemoryPreferences()) : SharedPreferences by delegate {
        var fail = false
        override fun edit(): SharedPreferences.Editor {
            val editor = delegate.edit()
            return object : SharedPreferences.Editor by editor {
                override fun putString(key: String?, value: String?): SharedPreferences.Editor { editor.putString(key, value); return this }
                override fun remove(key: String?): SharedPreferences.Editor { editor.remove(key); return this }
                override fun apply() { if (fail) { fail = false; error("synthetic persistence failure") }; editor.apply() }
            }
        }
    }

    @Test fun criticalWritersExposeFailureWithoutPublishingFalseSuccess() {
        val prefs = FailingPreferences()
        val repo = MinusOneRepository(prefs)
        val before = repo.state.value
        prefs.fail = true
        assertFalse(repo.updateFileBrowserConfig { it.copy(showHidden = !it.showHidden) })
        assertEquals(before, repo.state.value)
        assertNotNull(repo.persistenceError.value)
        assertTrue(repo.updateFileBrowserConfig { it.copy(showHidden = !it.showHidden) })
        assertNull(repo.persistenceError.value)
        prefs.fail = true
        assertFalse(repo.updatePageGridStyle(0, rowGapDp = 29))
        assertNotEquals(29, repo.state.value.pages[0].gridRowGapDp)
    }

    @Test fun missingOrLockedTargetsAreNotAcceptedEdits() {
        val repo = MinusOneRepository(MemoryPreferences())
        assertFalse(repo.updatePageConfig(-1) { it.copy(name = "missing") })
        assertFalse(repo.updateComponent(0, "deleted") { it.copy(title = "missing") })
        assertTrue(repo.setLayoutLocked(true))
        assertFalse(repo.updatePageGridStyle(0, rowGapDp = 23))
    }

    @Test fun themeAndLegacyAppearanceStayAlignedInBothDirections() {
        val repo = MinusOneRepository(MemoryPreferences())
        assertTrue(repo.updateThemeConfig { it.copy(panelWidthPercent = 49, cardCornerRadiusDp = 31) })
        assertEquals(.49f, repo.state.value.appearanceConfig.panelWidthFraction, .0001f)
        assertEquals(31, repo.state.value.appearanceConfig.cardCornerRadiusDp)
        assertTrue(repo.updateAppearanceConfig { it.copy(panelWidthFraction = 3f) })
        assertEquals(repo.state.value.themeConfig.panelWidthPercent / 100f,
            repo.state.value.appearanceConfig.panelWidthFraction, .0001f)
        assertTrue(repo.state.value.themeConfig.panelWidthPercent in 20..100)
    }

    @Test fun explicitLightDarkPresetsStopFollowingSystem() {
        val repo = MinusOneRepository(MemoryPreferences())
        repo.updateThemeConfig { it.copy(followSystemDark = true) }
        repo.applyLightDarkPreset(true)
        assertFalse(repo.state.value.themeConfig.followSystemDark)
        assertTrue(repo.state.value.themeConfig.isDarkMode)
        repo.updateThemeConfig { it.copy(followSystemDark = true) }
        repo.applyLightDarkPreset(false)
        assertFalse(repo.state.value.themeConfig.followSystemDark)
        assertFalse(repo.state.value.themeConfig.isDarkMode)
    }

    @Test fun aProfileCannotFreezeUnconfirmedPanelEdits() {
        val repo = MinusOneRepository(MemoryPreferences())
        repo.setEditMode(true)
        repo.updatePageConfig(0) { it.copy(name = "draft") }
        val before = repo.state.value.profiles
        assertTrue(runCatching { repo.createConfigProfile("draft snapshot") }.isFailure)
        assertEquals(before, repo.state.value.profiles)
        assertTrue(repo.state.value.editMode)
        assertTrue(repo.hasPanelEditChanges())
    }

    @Test fun reauthorizationAndReplacementPreserveDirectoryOrder() {
        val a = FileBrowserRoot("content://test/a", "A")
        val b = FileBrowserRoot("content://test/b", "B")
        val c = FileBrowserRoot("content://test/c", "C")
        assertEquals(listOf(a, b.copy(title = "renewed"), c), mergeGrantedFileRoot(listOf(a, b, c), b.copy(title = "renewed"), null))
        assertEquals(listOf(a, c), mergeGrantedFileRoot(listOf(a, b, c), c, b.uri))
        val d = FileBrowserRoot("content://test/d", "D")
        assertEquals(listOf(a, d, c), mergeGrantedFileRoot(listOf(a, b, c), d, b.uri))
    }
}
