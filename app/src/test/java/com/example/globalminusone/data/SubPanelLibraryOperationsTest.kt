package com.example.globalminusone.data

import com.example.globalminusone.data.backup.NativeBackupCodec
import com.example.globalminusone.data.operations.SubPanelConfig
import com.example.globalminusone.data.operations.SubPanelLibraryOperations
import com.example.globalminusone.data.operations.SubPanelOperations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubPanelLibraryOperationsTest {

    @Test
    fun saveInstantiateRenameDeleteAndValidateRoundTrip() {
        var seq = 0
        fun nextId() = "id_${++seq}"

        val base = GlobalMinusOneState()
        val (withSub, sub) = SubPanelOperations.create(base, "常用工具", false, PageLayoutType.GRID, ::nextId)!!
        val ownedPageIdx = withSub.pages.indexOfFirst { it.subPanelId == sub.id }
        val ownedPage = withSub.pages[ownedPageIdx].copy(
            components = listOf(
                ComponentItem(id = "c1", type = ComponentType.SWITCH_WIFI, title = "WiFi", widgetId = 99),
                ComponentItem(id = "c2", type = ComponentType.SUB_PANEL_ENTRY, title = "自引用", subPanelId = sub.id),
                ComponentItem(
                    id = "folder1",
                    type = ComponentType.FOLDER,
                    title = "文件夹",
                    containerComponents = listOf(
                        ComponentItem(id = "c3", type = ComponentType.SWITCH_TORCH, title = "手电筒"),
                        ComponentItem(id = "c4", type = ComponentType.SUB_PANEL_ENTRY, title = "嵌套自引用", subPanelId = sub.id)
                    )
                )
            )
        )
        val populated = withSub.copy(
            pages = withSub.pages.toMutableList().apply { this[ownedPageIdx] = ownedPage }
        )

        val (withTemplate, tpl) = SubPanelLibraryOperations.saveSubPanelToLibrary(
            current = populated,
            subPanelId = sub.id,
            templateName = "工具模板",
            nowMs = 123456L,
            newId = ::nextId
        )!!
        assertEquals(1, withTemplate.subPanelLibrary.size)
        assertEquals("工具模板", tpl.name)
        assertEquals(1, tpl.pages.size)
        assertEquals("", tpl.pages.single().subPanelId)
        assertEquals(-1, tpl.pages.single().components.first().widgetId)
        // Self-referencing SUB_PANEL_ENTRY cards inside source or containers are stripped in the template.
        assertEquals(listOf("WiFi", "文件夹"), tpl.pages.single().components.map { it.title })
        assertEquals(listOf("手电筒"), tpl.pages.single().components.last().containerComponents.map { it.title })

        // Instantiating creates an independent copy with fresh IDs and keeps template untouched.
        val (withClone, clonedSub) = SubPanelLibraryOperations.instantiateFromLibrary(
            current = withTemplate,
            templateId = tpl.id,
            customName = null,
            newId = ::nextId
        )!!
        assertNotSame(sub.id, clonedSub.id)
        assertTrue(clonedSub.id != sub.id)
        val clonedPages = withClone.pages.filter { it.subPanelId == clonedSub.id }
        assertEquals(1, clonedPages.size)
        assertEquals("WiFi", clonedPages.single().components.first().title)
        assertTrue(clonedPages.single().components.first().id != tpl.pages.single().components.first().id)

        // NativeBackupCodec accepts valid state with subPanelLibrary.
        NativeBackupCodec.validateState(withClone)

        // Rename & delete template without affecting already instantiated sub-panel.
        val renamed = SubPanelLibraryOperations.renameTemplate(withClone, tpl.id, "新版工具库")!!
        assertEquals("新版工具库", renamed.subPanelLibrary.single().name)
        val deleted = SubPanelLibraryOperations.deleteTemplate(renamed, tpl.id)!!
        assertTrue(deleted.subPanelLibrary.isEmpty())
        assertTrue(deleted.subPanels.any { it.id == clonedSub.id })

        // Layout lock prevents instantiating from library.
        val locked = renamed.copy(advancedConfig = renamed.advancedConfig.copy(layoutLocked = true))
        assertNull(SubPanelLibraryOperations.instantiateFromLibrary(locked, tpl.id, null, ::nextId))
        assertFalse(renamed.subPanelLibrary.isEmpty())
        assertNotNull(SubPanelLibraryOperations.saveSubPanelToLibrary(deleted, clonedSub.id, null, 200L, ::nextId))
    }
}
