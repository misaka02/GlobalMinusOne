package com.example.globalminusone

import android.content.SharedPreferences
import com.example.globalminusone.data.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MinusOneRepositoryTest {
    private val json = Json { encodeDefaults = true }

    @Test fun asynchronousActionChoicesKeepStablePageAndContainerAndNeverFallBackToTheTopLevel() {
        val prefs = MemoryPreferences()
        val repository = MinusOneRepository(prefs)
        val pageId = repository.state.value.pages[0].id
        val box = ComponentItem(id = "choices-box", type = ComponentType.CONTAINER_BOX)
        repository.updatePageConfig(0) { it.copy(components = listOf(box)) }
        val choices = listOf(
            ComponentItem(id = "activity", type = ComponentType.CUSTOM_SHORTCUT, intentUri = "intent:#Intent;component=test/.PublicActivity;end"),
            ComponentItem(id = "contact", type = ComponentType.CUSTOM_SHORTCUT, intentUri = "content://com.android.contacts/contacts/lookup/synthetic"),
            ComponentItem(id = "shell", type = ComponentType.SHELL_COMMAND, textContent = "printf synthetic")
        )
        choices.forEach { assertTrue(repository.placeMenuComponent(pageId, box.id, it)) }
        val restored = MinusOneRepository(prefs).state.value.pages.first { it.id == pageId }
        assertEquals(listOf(box.id), restored.components.map { it.id })
        assertEquals(choices.map { it.id }, restored.components.single().containerComponents.map { it.id })
        assertEquals("printf synthetic", restored.components.findComponent("shell")!!.textContent)
        repository.removeComponent(0, box.id)
        val unchanged = repository.state.value
        assertFalse(repository.placeMenuComponent(pageId, box.id, ComponentItem(type = ComponentType.SHELL_COMMAND)))
        assertFalse(repository.placeMenuComponent("deleted-page", null, ComponentItem(type = ComponentType.CUSTOM_SHORTCUT)))
        assertEquals(unchanged, repository.state.value)
        repository.setLayoutLocked(true)
        assertFalse(repository.placeMenuComponent(pageId, null, ComponentItem(type = ComponentType.CUSTOM_SHORTCUT)))
    }

    @Test
    fun repeatedProfileCreation_keepsSnapshotsIndependentAndStorageLinear() {
        val prefs = MemoryPreferences()
        val repository = MinusOneRepository(prefs)
        repository.setEditMode(true)
        assertTrue(repository.finishPanelEdit(save = true))
        val storedSizes = mutableListOf<Int>()
        val profiles = (1..8).map { index ->
            repository.createConfigProfile("方案 $index").also {
                storedSizes += prefs.getString(STATE_KEY, null)!!.length
            }
        }

        profiles.forEach { profile ->
            val snapshot = json.decodeFromString<GlobalMinusOneState>(profile.stateJson)
            assertTrue(snapshot.profiles.isEmpty())
            assertEquals("default", snapshot.activeProfileId)
            assertFalse(snapshot.editMode)
            assertNull(snapshot.selectedComponentId)
        }
        val sizes = profiles.map { it.stateJson.length }
        assertTrue("Snapshots must not embed previous profiles", sizes.max() <= sizes.min() + 128)

        assertTrue(
            "Stored state should grow at most linearly with profile count",
            storedSizes.last() <= storedSizes.first() * profiles.size + 1024
        )
    }

    @Test
    fun applyingSnapshot_restoresContentAndKeepsAllProfiles() {
        val repository = MinusOneRepository(MemoryPreferences())
        repository.updateNoteText("保存时的便签")
        repository.setCurrentPageIndex(1)
        val saved = repository.createConfigProfile("保存的方案")
        repository.updateNoteText("另一份便签")
        repository.createConfigProfile("另一方案")
        val allProfiles = repository.state.value.profiles

        assertTrue(repository.applyConfigProfile(saved.id))
        assertEquals("保存时的便签", repository.state.value.noteText)
        assertEquals(1, repository.state.value.currentPageIndex)
        assertEquals(allProfiles, repository.state.value.profiles)
        assertEquals(saved.id, repository.state.value.activeProfileId)
    }

    @Test
    fun invalidImports_leaveStateAndPreferencesUntouched() {
        val prefs = MemoryPreferences()
        val repository = MinusOneRepository(prefs)
        repository.updateNoteText("必须保留")
        val original = repository.state.value
        val stored = prefs.getString(STATE_KEY, null)
        val writes = prefs.writeCount
        val invalidStates = listOf(
            original.copy(advancedConfig = AdvancedConfig(gridColumns = 0)),
            original.copy(advancedConfig = AdvancedConfig(gridColumns = 65)),
            original.copy(pages = emptyList()),
            original.copy(currentPageIndex = -1),
            original.copy(currentPageIndex = original.pages.size)
        )

        (invalidStates.map { json.encodeToString(it) } + "invalid json").forEach { code ->
            assertFalse(repository.importConfigCode(code))
            assertEquals(original, repository.state.value)
            assertEquals(stored, prefs.getString(STATE_KEY, null))
            assertEquals(writes, prefs.writeCount)
        }
        assertTrue(repository.importConfigCodeResult("{\"advancedConfig\":{\"gridColumns\":0}}").isFailure)
        assertEquals(original, repository.state.value)
        assertEquals(writes, prefs.writeCount)
    }

    @Test
    fun validImport_persistsAndLoadsAgain() {
        val prefs = MemoryPreferences()
        val repository = MinusOneRepository(prefs)
        val expected = repository.state.value.copy(
            noteText = "有效导入",
            currentPageIndex = 1,
            advancedConfig = AdvancedConfig(gridColumns = 64)
        )

        assertTrue(repository.importConfigCode(json.encodeToString(expected)))
        assertEquals(expected, repository.state.value)
        assertEquals(expected, MinusOneRepository(prefs).state.value)
    }

    @Test
    fun invalidPersistedState_fallsBackWithoutOverwritingStoredValue() {
        val invalid = GlobalMinusOneState(advancedConfig = AdvancedConfig(gridColumns = 0))
        val raw = json.encodeToString(invalid)
        val prefs = MemoryPreferences(mapOf(STATE_KEY to raw))

        val loaded = MinusOneRepository(prefs).state.value

        assertEquals(32, loaded.advancedConfig.gridColumns)
        assertEquals(2, loaded.pages.size)
        assertEquals(raw, prefs.getString(STATE_KEY, null))
        assertEquals(0, prefs.writeCount)
    }

    @Test
    fun invalidProfileSnapshot_cannotChangeStateOrPreferences() {
        val invalidSnapshot = json.encodeToString(GlobalMinusOneState(pages = emptyList()))
        val initial = GlobalMinusOneState(
            profiles = listOf(ConfigProfile(id = "invalid", name = "无效方案", stateJson = invalidSnapshot))
        )
        val prefs = MemoryPreferences(mapOf(STATE_KEY to json.encodeToString(initial)))
        val repository = MinusOneRepository(prefs)

        assertFalse(repository.applyConfigProfile("invalid"))
        assertNull(repository.exportConfigProfileCode("invalid"))
        assertEquals(initial, repository.state.value)
        assertEquals(0, prefs.writeCount)
    }

    @Test
    fun exportingInactiveProfile_usesSelectedSnapshotAndDoesNotApplyIt() {
        val prefs = MemoryPreferences()
        val repository = MinusOneRepository(prefs)
        repository.updateNoteText("方案 A")
        val profileA = repository.createConfigProfile("A")
        repository.updateNoteText("方案 B")
        val profileB = repository.createConfigProfile("B")
        val original = repository.state.value
        val writes = prefs.writeCount

        val exported = repository.exportConfigProfileCode(profileA.id)!!
        val snapshot = json.decodeFromString<GlobalMinusOneState>(exported)
        assertEquals("方案 A", snapshot.noteText)
        assertEquals("方案 B", json.decodeFromString<GlobalMinusOneState>(repository.exportConfigCode()).noteText)
        assertEquals(profileB.id, repository.state.value.activeProfileId)
        assertEquals(original, repository.state.value)
        assertEquals(writes, prefs.writeCount)
        assertNull(repository.exportConfigProfileCode("missing"))
    }

    @Test
    fun defaultProfileWithoutSnapshot_keepsCurrentContentForExportAndApply() {
        val repository = MinusOneRepository(MemoryPreferences())
        repository.updateNoteText("当前自定义内容")
        repository.createConfigProfile("自定义")
        val before = repository.state.value

        assertEquals(repository.exportConfigCode(), repository.exportConfigProfileCode("default"))
        assertTrue(repository.applyConfigProfile("default"))
        assertEquals(before.copy(activeProfileId = "default"), repository.state.value)
    }

    @Test
    fun layoutLock_persistsExitsEditingAndRejectsStructuralMutations() {
        val prefs = MemoryPreferences()
        val repository = MinusOneRepository(prefs)
        val action = QuickActionItem(id = "locked-action", title = "测试", intentUri = "test:")
        val note = ComponentItem(id = "lock-note", type = ComponentType.NOTE_CARD, textContent = "原始内容", containerActions = listOf(action))
        repository.addComponentToPage(0, note)
        repository.addQuickAction(action)
        repository.setFavoriteApps(listOf("test"))
        repository.addBoundWidget(BoundWidgetInfo(99, "test", "Widget", "已有小部件"))
        repository.setEditMode(true)
        repository.selectComponent(note.id)
        repository.setLayoutLocked(true)
        val locked = repository.state.value
        val writes = prefs.writeCount
        assertFalse(locked.editMode)
        assertNull(locked.selectedComponentId)
        assertTrue(MinusOneRepository(prefs).state.value.advancedConfig.layoutLocked)

        repository.setEditMode(true)
        repository.selectComponent(note.id)
        assertEquals(-1, repository.addPage(PageLayoutType.FREE))
        assertFalse(repository.deletePage(0))
        assertFalse(repository.swapPageLeft(1))
        assertFalse(repository.swapPageRight(0))
        repository.updatePageConfig(0) { it.copy(name = "不应修改") }
        repository.togglePageLayoutType(0)
        repository.addComponentToPage(0, note.copy(id = "extra"))
        repository.updateComponent(0, note.id) { it.copy(x = 7, title = "不应修改") }
        repository.removeComponent(0, note.id)
        repository.moveComponentOrderInLinearPage(0, note.id, true)
        repository.resetComponentStyle(0, note.id)
        repository.addActionToContainer(0, note.id, action)
        repository.removeActionFromContainer(0, note.id, action.id)
        repository.clearContainerActions(0, note.id)
        repository.addBoundWidget(BoundWidgetInfo(100, "test", "Widget", "新增小部件"))
        repository.updateWidgetHeight(99, 300)
        repository.addQuickAction(action)
        repository.updateQuickAction(action.copy(title = "不应更新"))
        repository.removeQuickAction(action.id)
        repository.toggleFavoriteApp("test")
        repository.setFavoriteApps(listOf("other"))
        repository.toggleCardEnabled(locked.cards.first().id, false)
        repository.toggleCardCollapsed(locked.cards.first().id)
        repository.moveCard(locked.cards.last().id, true)
        repository.removeCard(locked.cards.first().id)
        repository.updateCustomImageCard("content://test")
        repository.updateAdvancedConfig { it.copy(gridColumns = 64, layoutLocked = false) }
        repository.resetToDefaults()

        assertEquals(locked, repository.state.value)
        assertEquals(writes, prefs.writeCount)
        repository.setLayoutLocked(false)
        repository.updateComponent(0, note.id) { it.copy(title = "可修改") }
        assertEquals("可修改", repository.state.value.pages[0].components.first { it.id == note.id }.title)
    }

    @Test
    fun layoutLock_allowsContentNavigationAndGlobalPreferences() {
        val repository = MinusOneRepository(MemoryPreferences())
        val note = ComponentItem(id = "note", type = ComponentType.NOTE_CARD)
        repository.addComponentToPage(0, note)
        repository.setLayoutLocked(true)
        repository.updateComponentText(0, note.id, "仍可输入")
        repository.updateNoteText("便签正文")
        repository.recordClipboardText("剪贴板内容")
        repository.addTodo("日常待办")
        repository.setCurrentPageIndex(1)
        repository.updateThemeConfig { it.copy(isDarkMode = false) }
        repository.updateAdvancedConfig { it.copy(autoCloseInLandscape = !it.autoCloseInLandscape) }
        repository.updateTriggerConfig { it.copy(minimumSwipeDistanceDp = 160) }
        repository.updateFileBrowserConfig { it.copy(showHidden = true, sortOrder = FileBrowserSortOrder.MODIFIED) }

        val state = repository.state.value
        assertEquals("仍可输入", state.pages[0].components.last().textContent)
        assertEquals("便签正文", state.noteText)
        assertTrue(state.clipboardHistory.any { it.text == "剪贴板内容" })
        assertEquals("日常待办", state.todos.first().content)
        assertEquals(1, state.currentPageIndex)
        assertFalse(state.themeConfig.isDarkMode)
        assertEquals(160, state.triggerConfig.minimumSwipeDistanceDp)
        assertTrue(state.fileBrowserConfig.showHidden)
        assertEquals(FileBrowserSortOrder.MODIFIED, state.fileBrowserConfig.sortOrder)
        assertTrue(state.advancedConfig.layoutLocked)
    }

    @Test
    fun layoutLock_rejectsImportAndProfileReplacementWithoutLosingData() {
        val prefs = MemoryPreferences()
        val repository = MinusOneRepository(prefs)
        repository.updateNoteText("已保存方案")
        val profile = repository.createConfigProfile("未锁定方案")
        val unlockedCode = repository.exportConfigCode()
        repository.updateNoteText("当前锁定内容")
        repository.setLayoutLocked(true)
        val locked = repository.state.value
        val writes = prefs.writeCount

        assertFalse(repository.applyConfigProfile(profile.id))
        assertFalse(repository.applyConfigProfile("default"))
        assertFalse(repository.importConfigCode(unlockedCode))
        assertTrue(repository.importConfigCodeResult(unlockedCode).isFailure)
        assertEquals(locked, repository.state.value)
        assertEquals(writes, prefs.writeCount)
        repository.setLayoutLocked(false)
        assertTrue(repository.applyConfigProfile(profile.id))
        assertEquals("已保存方案", repository.state.value.noteText)
    }

    @Test
    fun importingLockedState_normalizesTransientEditingAndRetainsLock() {
        val prefs = MemoryPreferences()
        val repository = MinusOneRepository(prefs)
        val incoming = repository.state.value.copy(
            editMode = true,
            selectedComponentId = "selection",
            advancedConfig = AdvancedConfig(layoutLocked = true)
        )
        assertTrue(repository.importConfigCode(json.encodeToString(incoming)))
        val expected = incoming.copy(editMode = false, selectedComponentId = null)
        assertEquals(expected, repository.state.value)
        assertEquals(expected, MinusOneRepository(prefs).state.value)
        repository.setEditMode(true)
        assertEquals(expected, repository.state.value)
    }

    @Test
    fun swipeDistance_validatesImportAndClampsPreferenceUpdates() {
        val repository = MinusOneRepository(MemoryPreferences())
        val original = repository.state.value
        for (distance in listOf(15, 161)) {
            val invalid = original.copy(triggerConfig = original.triggerConfig.copy(minimumSwipeDistanceDp = distance))
            assertFalse(repository.importConfigCode(json.encodeToString(invalid)))
            assertEquals(original, repository.state.value)
        }
        repository.updateTriggerConfig { it.copy(minimumSwipeDistanceDp = 0) }
        assertEquals(16, repository.state.value.triggerConfig.minimumSwipeDistanceDp)
        repository.updateTriggerConfig { it.copy(minimumSwipeDistanceDp = 999) }
        assertEquals(160, repository.state.value.triggerConfig.minimumSwipeDistanceDp)
    }

    @Test
    fun invalidFileDirectoryImports_leaveStoredContentUntouched() {
        val prefs = MemoryPreferences()
        val repository = MinusOneRepository(prefs)
        repository.updateNoteText("必须保留的内容")
        val original = repository.state.value
        val writes = prefs.writeCount
        val stored = prefs.getString(STATE_KEY, null)
        val invalidRoots = listOf(
            listOf(FileBrowserRoot("", "空目录")),
            listOf(FileBrowserRoot("  ", "空白目录")),
            listOf(FileBrowserRoot("content://tree/one", "目录一"), FileBrowserRoot("content://tree/one", "重复目录"))
        )
        invalidRoots.forEach { roots ->
            val invalid = original.copy(fileBrowserConfig = FileBrowserConfig(roots = roots))
            assertFalse(repository.importConfigCode(json.encodeToString(invalid)))
            assertEquals(original, repository.state.value)
            assertEquals(stored, prefs.getString(STATE_KEY, null))
            assertEquals(writes, prefs.writeCount)
        }
    }

    @Test
    fun pickedCards_recheckDestinationAndKeepPersistentReadUris() {
        val prefs = MemoryPreferences()
        val repository = MinusOneRepository(prefs)
        val pageId = repository.state.value.pages.first().id
        assertTrue(repository.saveFileShortcut(pageId, null, "合同.pdf", "content://documents/contract"))
        val card = repository.state.value.pages.first().components.last()
        assertEquals(ComponentType.FILE_SHORTCUT, card.type)
        assertEquals("content://documents/contract", card.intentUri)
        assertTrue(repository.saveFileShortcut(pageId, card.id, "新版.pdf", "content://documents/revised"))
        assertEquals(card.id, repository.state.value.pages.first().components.last().id)
        assertEquals("content://documents/revised", MinusOneRepository(prefs).state.value.pages.first().components.last().intentUri)
        val previous = repository.state.value
        assertFalse(repository.saveFileShortcut("deleted-page", null, "丢失.pdf", "content://documents/missing"))
        assertFalse(repository.saveFileShortcut(pageId, "deleted-card", "丢失.pdf", "content://documents/missing"))
        assertFalse(repository.saveImageCard(pageId, card.id, "类型不符", "content://images/a"))
        assertFalse(repository.saveFileShortcut(pageId, null, "错误协议", "file:///sdcard/private"))
        assertEquals(previous, repository.state.value)
        repository.setLayoutLocked(true)
        assertFalse(repository.saveFileShortcut(pageId, card.id, "锁定期间", "content://documents/locked"))
        assertFalse(repository.saveImageCard(pageId, null, "图片", "content://images/a"))
    }

    @Test
    fun imageSelectionAndSettingsTheme_persistIndependentlyFromPanelTheme() {
        val prefs = MemoryPreferences()
        val repository = MinusOneRepository(prefs)
        val panelTheme = repository.state.value.themeConfig
        val pageId = repository.state.value.pages.first().id
        assertTrue(repository.saveImageCard(pageId, null, "合成图片", "content://images/selected"))
        repository.updateAdvancedConfig { it.copy(settingsDarkMode = true) }
        val reloaded = MinusOneRepository(prefs).state.value
        assertTrue(reloaded.advancedConfig.settingsDarkMode)
        assertEquals(panelTheme, reloaded.themeConfig)
        assertEquals("content://images/selected", reloaded.pages.first().components.last().imageUri)
    }

    @Test
    fun panelAndFileWindowGeometry_persistAndClampWithoutChangingCards() {
        val prefs = MemoryPreferences()
        val repository = MinusOneRepository(prefs)
        val pages = repository.state.value.pages
        repository.updateThemeConfig { it.copy(panelHeightPercent = 60, panelWidthPercent = 200,
            panelVerticalPositionPercent = 25, panelHorizontalOffsetPercent = -10) }
        repository.updateFileBrowserConfig { it.copy(windowWidthFraction = .8f, windowHeightFraction = .5f,
            windowXFraction = .1f, windowYFraction = .2f) }
        val restored = MinusOneRepository(prefs).state.value
        assertEquals(100, restored.themeConfig.panelWidthPercent)
        assertEquals(0, restored.themeConfig.panelHorizontalOffsetPercent)
        assertEquals(60, restored.themeConfig.panelHeightPercent)
        assertEquals(25, restored.themeConfig.panelVerticalPositionPercent)
        assertEquals(.8f, restored.fileBrowserConfig.windowWidthFraction)
        assertEquals(.2f, restored.fileBrowserConfig.windowYFraction)
        assertEquals(pages, restored.pages)
    }

    @Test
    fun nestedCards_addEditDeleteAndReloadStayInsideExactContainer() {
        val prefs = MemoryPreferences()
        val repository = MinusOneRepository(prefs)
        repository.updatePageConfig(0) { it.copy(components = listOf(ComponentItem(id = "outer", type = ComponentType.CONTAINER_BOX))) }
        assertTrue(repository.addComponentToContainer(0, "outer", ComponentItem(id = "inner", type = ComponentType.CONTAINER_BOX)))
        assertTrue(repository.addComponentToContainer(0, "inner", ComponentItem(id = "note", type = ComponentType.NOTE_CARD, w = 12, h = 8)))
        assertTrue(repository.addComponentToContainer(0, "inner", ComponentItem(id = "battery", type = ComponentType.STAT_BATTERY_BAR)))
        repository.updateComponent(0, "note") { it.copy(title = "子便签", x = 3, y = 4, w = 14, h = 10) }
        repository.updateComponentText(0, "note", "持久内容")
        val restored = MinusOneRepository(prefs).state.value
        assertEquals(1, restored.pages[0].components.size)
        assertEquals(listOf("outer", "inner", "note"), restored.pages[0].components.componentPath("note"))
        val child = restored.pages[0].components.findComponent("note")!!
        assertEquals("持久内容", child.textContent)
        assertEquals(listOf(3, 4, 14, 10), listOf(child.x, child.y, child.w, child.h))
        assertTrue(restored.pages[0].components.findComponent("battery")!!.y > 0)
        repository.removeComponent(0, "note")
        assertNull(repository.state.value.pages[0].components.findComponent("note"))
        assertNotNull(repository.state.value.pages[0].components.findComponent("battery"))
    }

    @Test
    fun nestedMutationsRejectDuplicateCyclesDeletedTargetsAndLocksWithoutWrites() {
        val prefs = MemoryPreferences()
        val repository = MinusOneRepository(prefs)
        repository.updatePageConfig(0) { it.copy(components = listOf(ComponentItem(id = "outer", type = ComponentType.CONTAINER_BOX))) }
        val stable = repository.exportConfigCode()
        assertFalse(repository.addComponentToContainer(0, "missing", ComponentItem(type = ComponentType.NOTE_CARD)))
        assertFalse(repository.addComponentToContainer(0, "outer", ComponentItem(id = "outer", type = ComponentType.NOTE_CARD)))
        val children = mutableListOf<ComponentItem>()
        val cyclic = ComponentItem(id = "cycle", type = ComponentType.CONTAINER_BOX, containerComponents = children)
        children += cyclic
        assertFalse(repository.addComponentToContainer(0, "outer", cyclic))
        assertEquals(stable, repository.exportConfigCode())
        repository.setLayoutLocked(true)
        val locked = repository.exportConfigCode()
        assertFalse(repository.addComponentToContainer(0, "outer", ComponentItem(type = ComponentType.NOTE_CARD)))
        assertEquals(locked, repository.exportConfigCode())
    }

    @Test
    fun nestedWidgetRemovalReleasesOnlyIdsWithNoRemainingTreeReference() {
        val repository = MinusOneRepository(MemoryPreferences())
        repository.updatePageConfig(0) { it.copy(components = listOf(ComponentItem(id = "outer", type = ComponentType.CONTAINER_BOX))) }
        val pageId = repository.state.value.pages[0].id
        assertTrue(repository.addBoundWidget(BoundWidgetInfo(301, "test.provider", "Widget", "子小部件"), pageId, "outer"))
        assertTrue(301 in repository.state.value.savedWidgetIds())
        repository.addComponentToPage(0, ComponentItem(id = "survivor", type = ComponentType.APP_WIDGET, widgetId = 301))
        repository.removeComponent(0, "outer")
        assertTrue(repository.state.value.boundWidgets.any { it.appWidgetId == 301 })
        assertTrue(301 in repository.state.value.savedWidgetIds())
        repository.removeComponent(0, "survivor")
        assertFalse(301 in repository.state.value.savedWidgetIds())
        assertFalse(repository.state.value.boundWidgets.any { it.appWidgetId == 301 })
    }

    @Test
    fun asyncNestedFileAndImageResultsNeverFallBackToTopLevel() {
        val repository = MinusOneRepository(MemoryPreferences())
        repository.updatePageConfig(0) { it.copy(components = listOf(ComponentItem(id = "outer", type = ComponentType.CONTAINER_BOX))) }
        val pageId = repository.state.value.pages[0].id
        repository.setCurrentPageIndex(1)
        assertTrue(repository.saveImageCard(pageId, null, "子图片", "content://images/picked", "outer"))
        val page = repository.state.value.pages[0]
        assertEquals(1, page.components.size)
        assertEquals(ComponentType.SINGLE_IMAGE, page.components.single().containerComponents.single().type)
        repository.removeComponent(0, "outer")
        val stable = repository.exportConfigCode()
        assertFalse(repository.saveFileShortcut(pageId, null, "子文件", "content://documents/picked", "outer"))
        assertFalse(repository.addBoundWidget(BoundWidgetInfo(302, "test.provider", "Widget", "失效目标"), pageId, "outer"))
        assertEquals(stable, repository.exportConfigCode())
    }

    @Test
    fun legacyContainerActionsRemainReadableAndTooDeepImportsAreAtomic() {
        val repository = MinusOneRepository(MemoryPreferences())
        // Simulate an old document without the newly optional field using the serializer's default omission.
        val oldJson = Json { encodeDefaults = false }.encodeToString(repository.state.value)
        val originalContainer = repository.state.value.pages[0].components.first { it.containerActions.isNotEmpty() }
        assertTrue(repository.importConfigCode(oldJson))
        assertEquals(originalContainer.containerActions, repository.state.value.pages[0].components.findComponent(originalContainer.id)!!.containerActions)
        var deep = ComponentItem(type = ComponentType.NOTE_CARD)
        repeat(MAX_COMPONENT_TREE_DEPTH + 1) { deep = ComponentItem(type = ComponentType.CONTAINER_BOX, containerComponents = listOf(deep)) }
        val tooDeep = repository.state.value.copy(pages = listOf(PageConfig(name = "过深", components = listOf(deep))))
        val stable = repository.exportConfigCode()
        assertFalse(repository.importConfigCode(json.encodeToString(tooDeep)))
        assertEquals(stable, repository.exportConfigCode())
    }

    @Test fun iconAppearanceDefaultsRemainCompatibleAndDoNotChangeLaunchIdentity() {
        val repository = MinusOneRepository(MemoryPreferences())
        val item = ComponentItem(id="icon",type=ComponentType.APP_ICON,packageName="test.app",intentUri="keep://intent")
        repository.updatePageConfig(0) { it.copy(components=listOf(item)) }
        val pageId=repository.state.value.pages[0].id
        assertTrue(repository.saveComponentIcon(pageId,"icon","content://images/icon"))
        assertTrue(repository.updateAppAppearance("test.app",displayName="别名"))
        assertTrue(repository.saveAppIcon("test.app","content://images/app-icon"))
        val saved=repository.state.value.pages[0].components.single()
        assertEquals("test.app",saved.packageName);assertEquals("keep://intent",saved.intentUri)
        assertEquals("content://images/icon",saved.iconUri)
        assertEquals("别名",repository.state.value.appAppearanceOverrides["test.app"]!!.displayName)
        val old = Json { encodeDefaults=false }.encodeToString(GlobalMinusOneState(pages=listOf(PageConfig(name="Old",components=listOf(item)))))
        assertFalse(old.contains("iconUri")); assertFalse(old.contains("appAppearanceOverrides"))
        assertTrue(repository.importConfigCode(old));assertTrue(repository.state.value.appAppearanceOverrides.isEmpty())
        assertEquals("",repository.state.value.pages[0].components.single().iconUri)
    }
    @Test fun iconPickerReturnRejectsDeletedTargetsLockedStateAndNonContentUris() {
        val repository=MinusOneRepository(MemoryPreferences())
        val inner=ComponentItem(id="inner",type=ComponentType.APP_ICON)
        repository.updatePageConfig(0) { it.copy(components=listOf(ComponentItem(id="box",type=ComponentType.CONTAINER_BOX,containerComponents=listOf(inner)))) }
        val pageId=repository.state.value.pages[0].id
        assertTrue(repository.saveComponentIcon(pageId,"inner","content://images/one"))
        repository.removeComponent(0,"inner")
        val stable=repository.exportConfigCode()
        assertFalse(repository.saveComponentIcon(pageId,"inner","content://images/two"))
        assertFalse(repository.saveComponentIcon("deleted-page","box","content://images/two"))
        assertFalse(repository.saveAppIcon("test.app","file:///private/icon"))
        assertEquals(stable,repository.exportConfigCode())
        repository.setLayoutLocked(true)
        assertFalse(repository.saveAppIcon("test.app","content://images/two"))
        assertFalse(repository.saveComponentIcon(pageId,"box","content://images/two"))
    }
    @Test fun recursiveCopyAssignsNewIdsAndMoveKeepsOtherGeometryUntouched() {
        val repository=MinusOneRepository(MemoryPreferences())
        val child=ComponentItem(id="source-app",type=ComponentType.APP_ICON,packageName="test.app",x=2,y=4,w=8,h=8)
        val container=ComponentItem(id="source",type=ComponentType.CONTAINER_BOX,containerComponents=listOf(child))
        val anchor=ComponentItem(id="anchor",type=ComponentType.NOTE_CARD,x=7,y=21,w=11,h=9)
        repository.updatePageConfig(0) { it.copy(components=listOf(container,anchor)) }
        val target=ComponentItem(id="target",type=ComponentType.CONTAINER_BOX)
        repository.updatePageConfig(1) { it.copy(components=listOf(target)) }
        val a=repository.state.value.pages[0].id;val b=repository.state.value.pages[1].id
        assertTrue(repository.transferComponent(a,"source",b,"target",copy=true))
        val copy=repository.state.value.pages[1].components.single().containerComponents.single()
        assertFalse(copy.id==container.id);assertFalse(copy.containerComponents.single().id==child.id)
        assertEquals(child.packageName,copy.containerComponents.single().packageName)
        assertTrue(repository.transferComponent(a,"source-app",b,"target"))
        assertNull(repository.state.value.pages[0].components.findComponent("source-app"))
        assertNotNull(repository.state.value.pages[1].components.findComponent("source-app"))
        assertEquals(anchor,repository.state.value.pages[0].components.findComponent("anchor"))
        validateComponentTrees(repository.state.value.pages)
    }
    @Test fun invalidMoveAndWidgetCopyAreAtomicAndWidgetMovePreservesBinding() {
        val repository=MinusOneRepository(MemoryPreferences())
        val widget=ComponentItem(id="widget",type=ComponentType.APP_WIDGET,widgetId=501)
        val parent=ComponentItem(id="parent",type=ComponentType.CONTAINER_BOX,containerComponents=listOf(widget))
        repository.updatePageConfig(0) { it.copy(components=listOf(parent)) }
        repository.updatePageConfig(1) { it.copy(components=listOf(ComponentItem(id="target",type=ComponentType.CONTAINER_BOX))) }
        val a=repository.state.value.pages[0].id;val b=repository.state.value.pages[1].id
        val stable=repository.exportConfigCode()
        assertFalse(repository.transferComponent(a,"parent",a,"widget"))
        assertFalse(repository.transferComponent(a,"widget",b,"deleted"))
        assertFalse(repository.transferComponent(a,"parent",b,"target",copy=true))
        assertEquals(stable,repository.exportConfigCode())
        assertTrue(repository.transferComponent(a,"widget",b,"target"))
        assertTrue(501 in repository.state.value.savedWidgetIds())
        repository.setLayoutLocked(true)
        assertFalse(repository.transferComponent(b,"widget",a))
        assertFalse(repository.placeMenuComponent(a,null,ComponentItem(type=ComponentType.APP_ICON)))
    }


    @Test fun newAndTransferredChildrenStartBelowMeasuredAutomaticAppsWithoutMutatingSiblings() {
        val repository = MinusOneRepository(MemoryPreferences())
        repository.updatePanelLayoutMetrics(160f, 1.6f)
        val box = ComponentItem(id = "box", type = ComponentType.CONTAINER_BOX, w = 32)
        repository.updatePageConfig(0) { it.copy(components = listOf(box)) }
        val app = ComponentItem(id = "auto-app", type = ComponentType.APP_ICON, w = 8, h = 7)
        assertTrue(repository.addComponentToContainer(0, "box", app))
        val before = repository.state.value
        val next = nextComponentPlacementY(before.pages[0], "box", 32, 160f, fontScale = 1.6f)
        assertTrue(repository.addComponentToContainer(0, "box", ComponentItem(id = "note", type = ComponentType.NOTE_CARD)))
        var children = repository.state.value.pages[0].components.single().containerComponents
        assertEquals(app, children.first())
        assertEquals(next, children[1].y)
        val source = ComponentItem(id = "transfer", type = ComponentType.APP_ICON, w = 8, h = 7)
        repository.updatePageConfig(1) { it.copy(components = listOf(source)) }
        val current = repository.state.value
        val transferredY = nextComponentPlacementY(current.pages[0], "box", 32, 160f, fontScale = 1.6f)
        assertTrue(repository.transferComponent(current.pages[1].id, source.id, current.pages[0].id, "box"))
        children = repository.state.value.pages[0].components.single().containerComponents
        assertEquals(app, children.first())
        assertEquals(transferredY, children.last().y)
        assertEquals(source.h, children.last().h)
    }

    private companion object {
        const val STATE_KEY = "state_json_v5"
    }
}

/** Small in-memory implementation for exercising the repository without Android runtime calls. */
internal class MemoryPreferences(initial: Map<String, Any?> = emptyMap()) : SharedPreferences {
    private val values = initial.toMutableMap()
    var writeCount = 0
        private set

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        @Suppress("UNCHECKED_CAST")
        return (values[key] as? Set<String>)?.toMutableSet() ?: defValues
    }
    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = put(key, value)
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = put(key, values?.toSet())
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = put(key, value)
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = put(key, value)
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = put(key, value)
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = put(key, value)
        override fun remove(key: String?): SharedPreferences.Editor = put(key, null)
        override fun clear(): SharedPreferences.Editor = apply { clearRequested = true }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            if (clearRequested) values.clear()
            changes.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
            }
            writeCount++
        }

        private fun put(key: String?, value: Any?): SharedPreferences.Editor = apply {
            changes[requireNotNull(key)] = value
        }
    }
}
