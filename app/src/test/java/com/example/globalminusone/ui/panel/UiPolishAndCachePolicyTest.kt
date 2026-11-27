package com.example.globalminusone.ui.panel

import androidx.compose.ui.graphics.CompositingStrategy
import com.example.globalminusone.MemoryPreferences
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.ContainerLayoutType
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.MAX_GRID_GAP_DP
import com.example.globalminusone.data.MIN_CARD_SPACING_DP
import com.example.globalminusone.data.MIN_GRID_GAP_DP
import com.example.globalminusone.data.MIN_ICON_LABEL_GAP_DP
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.data.PageTransition
import com.example.globalminusone.data.QuickActionItem
import com.example.globalminusone.data.ROW_GAP_INHERIT
import com.example.globalminusone.data.ThemeConfig
import com.example.globalminusone.data.policy.arrangePageGridComponents
import com.example.globalminusone.data.backup.NativeBackupCodec
import com.example.globalminusone.data.policy.componentDisplayHeightDp
import com.example.globalminusone.data.policy.containerGridChildBounds
import com.example.globalminusone.data.policy.containerPopupColumns
import com.example.globalminusone.data.policy.derivePageGridMetrics
import com.example.globalminusone.data.policy.normalizedPanelGeometry
import com.example.globalminusone.data.policy.resolveContainerGridDropPlacements
import com.example.globalminusone.data.policy.resolvePageGridDropPlacements
import com.example.globalminusone.system.AppAndIntentLauncher
import com.example.globalminusone.system.BoundedMemoryLruCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.globalminusone.ui.panel.cards.UserIconCache
import com.example.globalminusone.ui.panel.container.collectComponentTreeIcons
import com.example.globalminusone.ui.panel.layout.computePageTransitionSpec
import com.example.globalminusone.ui.panel.editor.previousPropertySection
import com.example.globalminusone.ui.panel.layout.resolveTransitionCompositingStrategy

class UiPolishAndCachePolicyTest {

    @Test
    fun boundedMemoryLruCacheEnforcesCapacityAndAccessOrder() {
        val cache = BoundedMemoryLruCache<String, String>(2)
        cache.put("a", "1")
        cache.put("b", "2")
        // Touch "a" so "b" becomes least recently used.
        assertEquals("1", cache.get("a"))
        cache.put("c", "3")

        assertNull(cache.get("b"))
        assertEquals("1", cache.get("a"))
        assertEquals("3", cache.get("c"))

        assertEquals("1", cache.remove("a"))
        assertNull(cache.get("a"))
        cache.evictAll()
        assertNull(cache.get("c"))
    }

    @Test
    fun peekCachesReturnNullSafelyWithoutAndroidFramework() {
        assertNull(AppAndIntentLauncher.peekAppInfoCached(""))
        assertNull(AppAndIntentLauncher.peekAppInfoCached("com.example.missing"))
        AppAndIntentLauncher.evictAppCache("com.example.missing")

        assertNull(UserIconCache.peek("", 1L))
        assertNull(UserIconCache.peek("content://example/icon.png", 1L))
    }

    @Test
    fun stackAndPageTransitionsAnchorLeftwardPageAndFadeOffscreen() {
        val width = 1000f
        val height = 1800f
        val density = 3f

        listOf(PageTransition.STACK, PageTransition.STACK_SCALE, PageTransition.PAGE).forEach { mode ->
            val leaving = computePageTransitionSpec(mode, pageOffset = 0.4f, width = width, height = height, density = density)
            assertEquals("Mode $mode should counteract HorizontalPager leftward shift", 400f, leaving.translationX, 0.01f)
            assertTrue("Mode $mode should remain visible mid-swipe", leaving.alpha > 0f && leaving.alpha < 1f)

            val exited = computePageTransitionSpec(mode, pageOffset = 1.0f, width = width, height = height, density = density)
            assertEquals("Mode $mode must fade out at offset >= 1 to avoid bleeding behind transparent panels", 0f, exited.alpha, 0.001f)

            val entering = computePageTransitionSpec(mode, pageOffset = -0.4f, width = width, height = height, density = density)
            assertEquals(0f, entering.translationX, 0.01f)
            assertEquals(1f, entering.alpha, 0.01f)
        }

        val pageMid = computePageTransitionSpec(PageTransition.PAGE, 0.5f, width, height, density)
        assertTrue(pageMid.rotationY < 0f)
        assertEquals(0f, pageMid.pivotFractionX, 0.001f)
        assertEquals(16f * density, pageMid.cameraDistance, 0.01f)
    }

    @Test
    fun cubeTransitionsHingeSymmetricallyAcrossSeamAndAllTransitionsStayFinite() {
        val width = 900f
        val height = 1600f
        val density = 2.5f

        val outLeft = computePageTransitionSpec(PageTransition.OUT_CUBE, 0.5f, width, height, density)
        val outRight = computePageTransitionSpec(PageTransition.OUT_CUBE, -0.5f, width, height, density)
        assertEquals(1f, outLeft.pivotFractionX, 0.001f)
        assertEquals(0f, outRight.pivotFractionX, 0.001f)
        assertEquals(-45f, outLeft.rotationY, 0.01f)
        assertEquals(45f, outRight.rotationY, 0.01f)

        val inLeft = computePageTransitionSpec(PageTransition.IN_CUBE, 0.5f, width, height, density)
        val inRight = computePageTransitionSpec(PageTransition.IN_CUBE, -0.5f, width, height, density)
        assertEquals(1f, inLeft.pivotFractionX, 0.001f)
        assertEquals(0f, inRight.pivotFractionX, 0.001f)
        assertEquals(45f, inLeft.rotationY, 0.01f)
        assertEquals(-45f, inRight.rotationY, 0.01f)

        for (mode in PageTransition.entries) {
            for (offset in listOf(-1f, -0.5f, 0f, 0.5f, 1f)) {
                val spec = computePageTransitionSpec(mode, offset, width, height, density)
                assertFalse(spec.translationX.isNaN())
                assertFalse(spec.translationY.isNaN())
                assertFalse(spec.scaleX.isNaN())
                assertFalse(spec.scaleY.isNaN())
                assertFalse(spec.alpha.isNaN())
                assertFalse(spec.rotationY.isNaN())
                assertFalse(spec.rotationZ.isNaN())
                assertTrue(spec.cameraDistance >= 8f * density)
            }
        }
    }

    @Test
    fun previousPropertySectionCompatibilityPreserved() {
        assertNull(previousPropertySection("", ""))
        assertEquals("", previousPropertySection("内容", ""))
        assertEquals("容器与行为", previousPropertySection("内部排列", ""))
        assertEquals("容器与行为", previousPropertySection("图标与文字留白", ""))
        assertNull(previousPropertySection("文件夹窗口", "文件夹窗口"))
    }

    @Test
    fun resolveEffectivePackageNameAndMissingCachesSafeInJvm() {
        assertEquals(
            "com.example.direct",
            AppAndIntentLauncher.resolveEffectivePackageName("com.example.direct", "")
        )
        assertEquals(
            "com.example.comp",
            AppAndIntentLauncher.resolveEffectivePackageName(
                "",
                "intent:#Intent;action=android.intent.action.MAIN;component=com.example.comp/.MainActivity;end"
            )
        )
        assertEquals(
            "com.example.pkg",
            AppAndIntentLauncher.resolveEffectivePackageName(
                "",
                "intent:#Intent;package=com.example.pkg;end"
            )
        )
        assertEquals(
            "",
            AppAndIntentLauncher.resolveEffectivePackageName("", "https://example.com")
        )
        assertFalse(AppAndIntentLauncher.isKnownMissingPackage(""))
        assertFalse(AppAndIntentLauncher.isKnownMissingPackage("com.example.unknown"))
        assertFalse(UserIconCache.isKnownMissing("", 1L))
        assertFalse(UserIconCache.isKnownMissing("content://example/icon.png", 1L))
    }

    @Test
    fun collectComponentTreeIconsExtractsNestedFoldersAndQuickActions() {
        val leafApp = ComponentItem(
            type = ComponentType.APP_ICON,
            title = "Leaf",
            packageName = "com.example.leaf",
            iconUri = "content://icons/leaf.png"
        )
        val shortcut = ComponentItem(
            type = ComponentType.CUSTOM_SHORTCUT,
            title = "Shortcut",
            intentUri = "intent:#Intent;component=com.example.shortcut/.Entry;end"
        )
        val innerFolder = ComponentItem(
            type = ComponentType.FOLDER,
            title = "Inner",
            containerComponents = listOf(leafApp),
            containerActions = listOf(
                QuickActionItem(
                    title = "Legacy",
                    intentUri = "intent:#Intent;package=com.example.legacy;end"
                )
            )
        )
        val outerFolder = ComponentItem(
            type = ComponentType.FOLDER,
            title = "Outer",
            containerComponents = listOf(shortcut, innerFolder)
        )

        val packages = linkedSetOf<String>()
        val uris = linkedSetOf<String>()
        collectComponentTreeIcons(listOf(outerFolder), packages, uris)

        assertEquals(
            setOf("com.example.shortcut", "com.example.leaf", "com.example.legacy"),
            packages
        )
        assertEquals(setOf("content://icons/leaf.png"), uris)
    }

    @Test
    fun pageGridLayoutPolicy_supportsFree2DGridPlacementAndCellSwap() {
        val a = ComponentItem(
            id = "a",
            type = ComponentType.APP_ICON,
            title = "A",
            packageName = "com.example.a",
            gridCol = 0,
            gridRow = 0
        )
        val b = ComponentItem(
            id = "b",
            type = ComponentType.APP_ICON,
            title = "B",
            packageName = "com.example.b",
            gridCol = 2,
            gridRow = 3
        )

        val arranged = arrangePageGridComponents(
            components = listOf(a, b),
            availableWidthDp = 320f,
            columnCount = 4,
            iconSizeDp = 44
        )
        val placementA = arranged.placements.first { it.id == "a" }
        val placementB = arranged.placements.first { it.id == "b" }
        assertEquals(0, placementA.col)
        assertEquals(0, placementA.row)
        assertEquals(2, placementB.col)
        assertEquals(3, placementB.row)
        assertEquals(4, arranged.totalRows)

        val droppedEmpty = resolvePageGridDropPlacements(
            components = listOf(a, b),
            availableWidthDp = 320f,
            columnCount = 4,
            iconSizeDp = 44,
            movedId = "a",
            targetCol = 3,
            targetRow = 1
        )
        val aAfterEmpty = droppedEmpty.first { it.id == "a" }
        val bAfterEmpty = droppedEmpty.first { it.id == "b" }
        assertEquals(3, aAfterEmpty.gridCol)
        assertEquals(1, aAfterEmpty.gridRow)
        assertEquals(2, bAfterEmpty.gridCol)
        assertEquals(3, bAfterEmpty.gridRow)

        val swapped = resolvePageGridDropPlacements(
            components = droppedEmpty,
            availableWidthDp = 320f,
            columnCount = 4,
            iconSizeDp = 44,
            movedId = "a",
            targetCol = 2,
            targetRow = 3
        )
        val aAfterSwap = swapped.first { it.id == "a" }
        val bAfterSwap = swapped.first { it.id == "b" }
        assertEquals(2, aAfterSwap.gridCol)
        assertEquals(3, aAfterSwap.gridRow)
        assertEquals(3, bAfterSwap.gridCol)
        assertEquals(1, bAfterSwap.gridRow)
    }

    @Test
    fun pageGridLayoutPolicy_explicitIconSizeUsesCompactRowHeight() {
        val metrics = derivePageGridMetrics(
            availableWidthDp = 320f,
            itemSizeDp = 128,
            itemGapDp = 8,
            fontScale = 1f,
            columnCount = 4,
            iconSizeDp = 48,
            rowGapDp = 0
        )
        assertEquals(0, metrics.rowGapDp)
        assertEquals(48, metrics.iconRenderSizeDp)
        assertTrue(
            "Explicit iconSizeDp=48 should keep compact row height (< 100dp) instead of legacy itemSizeDp=128",
            metrics.cellHeightDp < 100f
        )
    }

    @Test
    fun containerGridMetrics_supportsFree2DGridPlacementAndZeroPadding() {
        val c1 = ComponentItem(
            id = "c1",
            type = ComponentType.APP_ICON,
            title = "One",
            packageName = "com.example.one"
        )
        val c2 = ComponentItem(
            id = "c2",
            type = ComponentType.APP_ICON,
            title = "Two",
            packageName = "com.example.two",
            gridCol = 2,
            gridRow = 2
        )

        val bounds = containerGridChildBounds(
            children = listOf(c1, c2),
            columns = 32,
            widthDp = 280f,
            stepYDp = 32f,
            iconColumns = 4,
            iconSizeDp = 44,
            itemGapDp = 6,
            rowGapDp = 4
        )
        val b1 = bounds.first { it.item.id == "c1" }
        val b2 = bounds.first { it.item.id == "c2" }
        assertEquals(0, b1.col)
        assertEquals(0, b1.row)
        assertEquals(2, b2.col)
        assertEquals(2, b2.row)

        val swapped = resolveContainerGridDropPlacements(
            children = listOf(c1, c2),
            columns = 32,
            widthDp = 280f,
            stepYDp = 32f,
            iconColumns = 4,
            iconSizeDp = 44,
            itemGapDp = 6,
            rowGapDp = 4,
            movedId = "c1",
            targetCol = 2,
            targetRow = 2
        )
        val movedC1 = swapped.first { it.id == "c1" }
        val movedC2 = swapped.first { it.id == "c2" }
        assertEquals(2, movedC1.gridCol)
        assertEquals(2, movedC1.gridRow)
        assertEquals(0, movedC2.gridCol)
        assertEquals(0, movedC2.gridRow)

        val zeroPadLabel = ComponentItem(
            id = "lbl0",
            type = ComponentType.TEXT_LABEL,
            title = "Label",
            showTitle = false,
            textContent = "Hi",
            autoHeight = true,
            w = 8,
            h = 1,
            paddingTopDp = 0,
            paddingBottomDp = 0
        )
        val sixPadLabel = zeroPadLabel.copy(paddingTopDp = 6, paddingBottomDp = 6)
        val zeroHeight = componentDisplayHeightDp(zeroPadLabel, outerWidthDp = 240f, gridColumns = 8, configuredHeightUnitDp = 1)
        val sixHeight = componentDisplayHeightDp(sixPadLabel, outerWidthDp = 240f, gridColumns = 8, configuredHeightUnitDp = 1)
        assertEquals(12f, sixHeight - zeroHeight, 0.01f)
    }

    @Test
    fun negativeSpacingPoliciesAndLegacyRowGapMigrationWorkCleanly() {
        val bounds = containerGridChildBounds(
            children = listOf(
                ComponentItem(id = "a", type = ComponentType.APP_ICON),
                ComponentItem(id = "b", type = ComponentType.APP_ICON),
                ComponentItem(id = "c", type = ComponentType.APP_ICON)
            ),
            columns = 32,
            widthDp = 280f,
            stepYDp = 32f,
            iconColumns = 2,
            iconSizeDp = 44,
            itemGapDp = MIN_GRID_GAP_DP,
            rowGapDp = -12,
            iconLabelGapDp = MIN_ICON_LABEL_GAP_DP
        )
        assertEquals(3, bounds.size)
        assertTrue(bounds.all { it.width >= 16f && it.height >= 16f })
        assertTrue(bounds[2].y >= bounds[0].y + 16f)

        val normalized = ThemeConfig(cardSpacingDp = -50).normalizedPanelGeometry()
        assertEquals(MIN_CARD_SPACING_DP, normalized.cardSpacingDp)

        val validState = GlobalMinusOneState(
            pages = listOf(
                PageConfig(
                    name = "Neg",
                    layoutType = PageLayoutType.GRID,
                    gridItemGapDp = MIN_GRID_GAP_DP,
                    gridRowGapDp = -8,
                    components = listOf(
                        ComponentItem(
                            type = ComponentType.FOLDER,
                            containerLayout = ContainerLayoutType.GRID,
                            containerItemGapDp = -4,
                            containerRowGapDp = -10,
                            containerIconLabelGapDp = MIN_ICON_LABEL_GAP_DP
                        )
                    )
                )
            ),
            themeConfig = ThemeConfig(cardSpacingDp = MIN_CARD_SPACING_DP),
            profiles = emptyList()
        )
        NativeBackupCodec.validateState(validState)

        val invalidSpacingState = validState.copy(themeConfig = ThemeConfig(cardSpacingDp = MIN_CARD_SPACING_DP - 1))
        assertTrue(runCatching { NativeBackupCodec.validateState(invalidSpacingState) }.isFailure)

        val legacyJson = """{"pages":[{"name":"Legacy","gridRowGapDp":-1,"components":[{"type":"FOLDER","containerRowGapDp":-1}]}],"profiles":[]}"""
        val migrated = NativeBackupCodec.readCompatible(legacyJson.byteInputStream(), emptyMap(), emptyMap()).payload.state
        assertEquals(ROW_GAP_INHERIT, migrated.pages[0].gridRowGapDp)
        assertEquals(ROW_GAP_INHERIT, migrated.pages[0].components[0].containerRowGapDp)
    }

    @Test
    fun removeComponentPreservesPinnedSibling2DCoordinatesInGridMode() {
        val prefs = MemoryPreferences()
        val repo = MinusOneRepository(prefs)
        repo.updateAdvancedConfig { it.copy(layoutLocked = false) }
        repo.setPageLayoutType(0, PageLayoutType.GRID)
        val first = ComponentItem(id = "card-a", type = ComponentType.APP_ICON, title = "A", gridCol = 0, gridRow = 0)
        val second = ComponentItem(id = "card-b", type = ComponentType.APP_ICON, title = "B", gridCol = 2, gridRow = 1)
        val third = ComponentItem(id = "card-c", type = ComponentType.APP_ICON, title = "C")
        repo.updatePageConfig(0) { it.copy(gridColumnCount = 4, components = listOf(first, third, second)) }

        assertTrue(repo.removeComponent(0, "card-a"))
        val remaining = repo.state.value.pages[0].components.associateBy { it.id }
        assertEquals(2, remaining.size)
        assertEquals(2, remaining.getValue("card-b").gridCol)
        assertEquals(1, remaining.getValue("card-b").gridRow)
        assertEquals(1, remaining.getValue("card-c").gridCol)
        assertEquals(0, remaining.getValue("card-c").gridRow)
    }

    @Test
    fun folderPopupColumnsExpandForPinnedItemsAndLegacyActionsCommit2DCoords() {
        val unpinnedShort = ComponentItem(
            type = ComponentType.FOLDER,
            containerColumns = 5,
            containerComponents = listOf(ComponentItem(id = "u1", type = ComponentType.APP_ICON))
        )
        assertEquals(2, containerPopupColumns(unpinnedShort))

        val pinnedWide = ComponentItem(
            type = ComponentType.FOLDER,
            containerColumns = 5,
            containerComponents = listOf(
                ComponentItem(id = "p1", type = ComponentType.APP_ICON, gridCol = 3, gridRow = 0)
            )
        )
        assertEquals(4, containerPopupColumns(pinnedWide))

        val repo = MinusOneRepository(MemoryPreferences())
        repo.updateAdvancedConfig { it.copy(layoutLocked = false) }
        val legacyFolder = ComponentItem(
            id = "legacy-folder",
            type = ComponentType.FOLDER,
            containerLayout = ContainerLayoutType.GRID,
            containerColumns = 4,
            containerActions = listOf(
                QuickActionItem(id = "act-1", title = "One", intentUri = "app://com.one", packageName = "com.one"),
                QuickActionItem(id = "act-2", title = "Two", intentUri = "app://com.two", packageName = "com.two")
            )
        )
        repo.updatePageConfig(0) { it.copy(components = listOf(legacyFolder)) }
        assertTrue(repo.placeActionInContainerGrid(0, "legacy-folder", "act-1", targetCol = 3, targetRow = 2, iconColumns = 4))
        val updatedFolder = repo.state.value.pages[0].components.single { it.id == "legacy-folder" }
        val movedAction = updatedFolder.containerActions.single { it.id == "act-1" }
        assertEquals(3, movedAction.gridCol)
        assertEquals(2, movedAction.gridRow)
    }

    @Test
    fun translationTransitionKeepsFullAlphaAndAvoidsOffscreenStrategy() {
        for (offset in listOf(-1f, -0.5f, 0f, 0.5f, 1f)) {
            val spec = computePageTransitionSpec(PageTransition.TRANSLATION, offset, 1080f, 1920f, 3f)
            assertEquals(1f, spec.alpha, 0.0001f)
        }
        assertEquals(CompositingStrategy.Auto, resolveTransitionCompositingStrategy(1f))
        assertEquals(CompositingStrategy.ModulateAlpha, resolveTransitionCompositingStrategy(0.5f))
    }
}
