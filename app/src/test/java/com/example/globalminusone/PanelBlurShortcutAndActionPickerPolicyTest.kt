package com.example.globalminusone

import com.example.globalminusone.data.DefaultPresets
import com.example.globalminusone.service.PanelWindowBlurPolicy
import com.example.globalminusone.system.ShortcutDiscoveryPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelBlurShortcutAndActionPickerPolicyTest {

    @Test
    fun panelWindowBlurPolicy_defersBlurUntilPanelOpenSettlesAndClearsImmediatelyOnClose() {
        assertEquals(
            0,
            PanelWindowBlurPolicy.effectiveBlurRadius(
                configuredRadius = 25,
                authorizationPending = false,
                panelVisible = true,
                panelClosing = false,
                openProgress = 0.65f
            )
        )
        assertEquals(
            0,
            PanelWindowBlurPolicy.effectiveBlurRadius(
                configuredRadius = 25,
                authorizationPending = false,
                panelVisible = true,
                panelClosing = true,
                openProgress = 1.0f
            )
        )
        assertEquals(
            0,
            PanelWindowBlurPolicy.effectiveBlurRadius(
                configuredRadius = 25,
                authorizationPending = true,
                panelVisible = true,
                panelClosing = false,
                openProgress = 1.0f
            )
        )
        assertEquals(
            25,
            PanelWindowBlurPolicy.effectiveBlurRadius(
                configuredRadius = 25,
                authorizationPending = false,
                panelVisible = true,
                panelClosing = false,
                openProgress = 1.0f
            )
        )
    }

    @Test
    fun shortcutDiscoveryPolicy_filtersUninstalledPackagesWhileRetainingBuiltinTools() {
        val (filtered, _) = ShortcutDiscoveryPolicy.filterBuiltInPresets(
            presets = DefaultPresets.builtInShortcuts,
            installedPackages = setOf("com.tencent.mm"),
            includeUninstalled = false
        )
        assertTrue(filtered.any { it.intentUri == "action:screenshot_region" })
        assertTrue(filtered.any { it.intentUri == "action:screen_assistant" })
        assertTrue(filtered.any { it.intentUri == "action:breeno_touch" })
        assertTrue(filtered.any { it.packageName == "com.tencent.mm" })
        assertFalse(filtered.any { it.packageName == "com.eg.android.AlipayGphone" })
    }

    @Test
    fun shortcutDiscoveryPolicy_roundTripsAppShortcutUriAndRejectsMalformed() {
        val uri = ShortcutDiscoveryPolicy.buildAppShortcutUri("com.example.app", "compose_note")
        assertEquals("appshortcut://com.example.app/compose_note", uri)
        assertEquals("com.example.app" to "compose_note", ShortcutDiscoveryPolicy.parseAppShortcutUri(uri))
        assertNull(ShortcutDiscoveryPolicy.parseAppShortcutUri("appshortcut://onlypkg"))
        assertNull(ShortcutDiscoveryPolicy.parseAppShortcutUri("intent:#Intent;end"))
    }

    @Test
    fun actionPickerRequest_supportsCreateShortcutRouteAndComponent() {
        val req = ActionPickerRequest.fromLegacy(
            kind = ActionPickerKind.CREATE_SHORTCUT.name,
            pageId = "page-1",
            containerId = "folder-9",
            beforeId = "anchor-2",
            shortcutComponent = "com.example.app/.ShortcutCreatorActivity"
        )!!
        assertEquals(ActionPickerKind.CREATE_SHORTCUT, req.kind)
        assertEquals("page-1", req.pageId)
        assertEquals("folder-9", req.containerId)
        assertEquals("anchor-2", req.beforeComponentId)
        assertEquals("com.example.app/.ShortcutCreatorActivity", req.shortcutComponent)
    }
}
