package com.example.globalminusone.regression.v24131

import com.example.globalminusone.data.AdvancedConfig
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.data.TriggerSide
import com.example.globalminusone.data.operations.SubPanelConfig
import com.example.globalminusone.data.policy.AppKeyboardLauncherSpec
import com.example.globalminusone.data.policy.FloatingPodHorizontalAnchor
import com.example.globalminusone.data.policy.KeyboardCardLayout
import com.example.globalminusone.data.policy.PanelOpenPageMode
import com.example.globalminusone.data.policy.SideFlushPodCorners
import com.example.globalminusone.data.policy.buildPanelOpenPageChoices
import com.example.globalminusone.data.policy.computeCandidateGridColumns
import com.example.globalminusone.data.policy.computeDetachedCandidatePlacement
import com.example.globalminusone.data.policy.computeFloatingPodPlacement
import com.example.globalminusone.data.policy.computeFrecencyRankOrder
import com.example.globalminusone.data.policy.describePanelOpenPageSetting
import com.example.globalminusone.data.policy.findDirectQueryHighlightRange
import com.example.globalminusone.data.policy.resolveDetachedCandidateWidthDp
import com.example.globalminusone.data.policy.resolveInitialSubPanelStack
import com.example.globalminusone.data.policy.resolveKeyboardPodDimensions
import com.example.globalminusone.data.policy.resolvePanelOpenTarget
import com.example.globalminusone.data.policy.resolveSideFlushPodCorners
import com.example.globalminusone.data.policy.resolveSubPanelEntryPageMemory
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V24131KeyboardOverlayAndOpenPagePolicyTest {

    @Test
    fun keyboardPodIsEnlargedAndDocksFlushToActiveScreenEdge() {
        val dims = resolveKeyboardPodDimensions(
            layout = KeyboardCardLayout.QWERTY,
            hostWidthDp = 412f,
            cardWidthDp = 48f,
            cardHeightDp = 48f,
            insideContainer = false,
            showNumberRow = true
        )
        assertTrue("Expected >= 350dp wide keyboard pod, got ${dims.podWidthDp}", dims.podWidthDp >= 350f)
        assertTrue("Expected taller QWERTY keys >= 40dp, got ${dims.qwertyKeyHeightDp}", dims.qwertyKeyHeightDp >= 40f)
        assertTrue("Expected single-hand detached candidates in 248..296dp, got ${dims.candidateMaxStripWidthDp}", dims.candidateMaxStripWidthDp in 248f..296f)
        assertEquals(296f, resolveDetachedCandidateWidthDp(412f), 0.01f)

        val t9Dims = resolveKeyboardPodDimensions(
            layout = KeyboardCardLayout.T9,
            hostWidthDp = 412f,
            cardWidthDp = 48f,
            cardHeightDp = 48f,
            insideContainer = false
        )
        assertTrue("Expected original compact T9 pod width 268..304dp, got ${t9Dims.podWidthDp}", t9Dims.podWidthDp in 268f..304f)
        assertTrue("Expected original compact T9 key height 30..38dp, got ${t9Dims.t9KeyHeightDp}", t9Dims.t9KeyHeightDp in 30f..38f)

        val rightPos = computeFloatingPodPlacement(
            hostWidthPx = 1080,
            hostHeightPx = 2400,
            podWidthPx = 980,
            podHeightPx = 620,
            anchorLeftPx = 120,
            anchorTopPx = 1600,
            anchorRightPx = 260,
            anchorBottomPx = 1740,
            activeSide = TriggerSide.RIGHT,
            horizontalAnchor = FloatingPodHorizontalAnchor.DOCK_ACTIVE_SIDE,
            edgeMarginPx = 0,
            safeTopPx = 96,
            safeBottomPx = 96
        )
        assertEquals(1080 - 980, rightPos.x)
        assertEquals(SideFlushPodCorners(leftDp = 16, rightDp = 0), resolveSideFlushPodCorners(TriggerSide.RIGHT, 16))
        assertEquals(SideFlushPodCorners(leftDp = 0, rightDp = 16), resolveSideFlushPodCorners(TriggerSide.LEFT, 16))
    }

    @Test
    fun detachedCandidateGridScalesColumnsAndAvoidsKeyboardOverlap() {
        assertEquals(5, computeCandidateGridColumns(panelWidthDp = 396f, showLabels = true))
        assertEquals(4, computeCandidateGridColumns(panelWidthDp = 300f, showLabels = true))
        assertEquals(3, computeCandidateGridColumns(panelWidthDp = 220f, showLabels = true))
        assertEquals(6, computeCandidateGridColumns(panelWidthDp = 396f, showLabels = false))
        assertEquals(3, computeCandidateGridColumns(panelWidthDp = 204f, showLabels = false))

        // Bottom keyboard (RIGHT side) -> single-hand candidate panel opens cleanly ABOVE, docked flush right
        val aboveRight = computeDetachedCandidatePlacement(
            hostWidthPx = 1080,
            hostHeightPx = 2400,
            anchorTopPx = 1520,
            anchorBottomPx = 2160,
            safeTopPx = 96,
            safeBottomPx = 96,
            horizontalMarginPx = 0,
            gapPx = 24,
            maxPanelHeightPx = 760,
            minPreferredHeightPx = 420,
            measuredPanelHeightPx = 560,
            activeSide = TriggerSide.RIGHT,
            maxPanelWidthPx = 780,
            measuredPanelWidthPx = 740
        )
        assertTrue(aboveRight.placedAbove)
        assertEquals(780, aboveRight.widthPx)
        assertEquals(1080 - 740, aboveRight.x)
        assertEquals(1520 - 24 - 560, aboveRight.y)

        // Left-side trigger -> candidate panel flips BELOW when near top and docks flush left
        val belowLeft = computeDetachedCandidatePlacement(
            hostWidthPx = 1080,
            hostHeightPx = 2400,
            anchorTopPx = 110,
            anchorBottomPx = 750,
            safeTopPx = 96,
            safeBottomPx = 96,
            horizontalMarginPx = 0,
            gapPx = 24,
            maxPanelHeightPx = 760,
            minPreferredHeightPx = 420,
            measuredPanelHeightPx = 560,
            activeSide = TriggerSide.LEFT,
            maxPanelWidthPx = 780
        )
        assertTrue(!belowLeft.placedAbove)
        assertEquals(0, belowLeft.x)
        assertEquals(780, belowLeft.widthPx)
        assertEquals(750 + 24, belowLeft.y)

        // Short split-screen host: candidate maxHeight clamps strictly to free gap with zero keyboard overlap
        val tight = computeDetachedCandidatePlacement(
            hostWidthPx = 1080,
            hostHeightPx = 760,
            anchorTopPx = 136,
            anchorBottomPx = 700,
            safeTopPx = 80,
            safeBottomPx = 40,
            horizontalMarginPx = 24,
            gapPx = 24,
            maxPanelHeightPx = 760,
            minPreferredHeightPx = 420
        )
        assertTrue(tight.maxHeightPx <= 32)
        assertTrue(tight.y + tight.maxHeightPx <= 136 - 24)
        assertEquals(4, AppKeyboardLauncherSpec.fromIntentUri(AppKeyboardLauncherSpec(maxCandidates = 4).encodeToIntentUri()).maxCandidates)
    }

    @Test
    fun panelOpenPagePolicySupportsLastVisitedLockedRootLockedSubPanelAndSafeFallback() {
        val pages = listOf(
            PageConfig(id = "root_0", name = "主页", layoutType = PageLayoutType.GRID),
            PageConfig(id = "root_1", name = "工具", layoutType = PageLayoutType.LINEAR),
            PageConfig(id = "sub_p1", name = "工作区", layoutType = PageLayoutType.FREE, subPanelId = "sub_work")
        )
        val subPanels = listOf(SubPanelConfig(id = "sub_work", name = "工作面板"))

        // 1. Default LAST_VISITED preserves caller's current page & sub-panel stack
        val lastVisitedState = GlobalMinusOneState(
            pages = pages,
            subPanels = subPanels,
            currentPageIndex = 2
        )
        assertNull(resolvePanelOpenTarget(lastVisitedState))
        assertTrue(describePanelOpenPageSetting(lastVisitedState).contains("上次"))

        // 2. Locked root page
        val lockedRoot = lastVisitedState.copy(
            advancedConfig = AdvancedConfig(
                panelOpenPageMode = PanelOpenPageMode.LOCKED_PAGE,
                lockedOpenPageId = "root_1"
            )
        )
        val rootTarget = resolvePanelOpenTarget(lockedRoot)
        assertNotNull(rootTarget)
        assertEquals(1, rootTarget!!.pageIndex)
        assertEquals(0, rootTarget.subPanelStack.depth)

        // 3. Locked sub-panel page restores both sub-panel stack and page index
        val lockedSub = lastVisitedState.copy(
            advancedConfig = AdvancedConfig(
                panelOpenPageMode = PanelOpenPageMode.LOCKED_PAGE,
                lockedOpenPageId = "sub_p1"
            )
        )
        val subTarget = resolvePanelOpenTarget(lockedSub)
        assertNotNull(subTarget)
        assertEquals(2, subTarget!!.pageIndex)
        assertEquals("sub_work", subTarget.subPanelStack.currentId)

        // 4. Deleted target ID safely falls back to first root page
        val deletedTarget = lastVisitedState.copy(
            advancedConfig = AdvancedConfig(
                panelOpenPageMode = PanelOpenPageMode.LOCKED_PAGE,
                lockedOpenPageId = "missing_id"
            )
        )
        val fallback = resolvePanelOpenTarget(deletedTarget)
        assertNotNull(fallback)
        assertEquals(0, fallback!!.pageIndex)
        assertEquals(0, fallback.subPanelStack.depth)
        assertEquals(3, buildPanelOpenPageChoices(lastVisitedState).size)

        // 5. v5 JSON serialization preserves settings and deserializes legacy JSON cleanly
        val json = Json { ignoreUnknownKeys = true }
        val encoded = json.encodeToString(GlobalMinusOneState.serializer(), lockedSub)
        val restored = json.decodeFromString(GlobalMinusOneState.serializer(), encoded)
        assertEquals(PanelOpenPageMode.LOCKED_PAGE, restored.advancedConfig.panelOpenPageMode)
        assertEquals("sub_p1", restored.advancedConfig.lockedOpenPageId)
        val legacyRestored = json.decodeFromString(GlobalMinusOneState.serializer(), "{}")
        assertEquals(PanelOpenPageMode.LAST_VISITED, legacyRestored.advancedConfig.panelOpenPageMode)
        assertEquals("", legacyRestored.advancedConfig.lockedOpenPageId)
    }

    @Test
    fun nestedSubPanelRestoresAncestorChainInBothLockedAndRecreatedLastVisitedModes() {
        val pages = listOf(
            PageConfig(
                id = "root_0",
                name = "主页",
                components = listOf(
                    ComponentItem(
                        id = "folder_wrap",
                        type = ComponentType.FOLDER,
                        title = "工作文件夹",
                        containerComponents = listOf(
                            ComponentItem(
                                id = "e1",
                                type = ComponentType.SUB_PANEL_ENTRY,
                                title = "工作",
                                subPanelId = "sub_parent"
                            )
                        )
                    )
                )
            ),
            PageConfig(
                id = "p_parent_0",
                name = "父子面板第一页",
                subPanelId = "sub_parent"
            ),
            PageConfig(
                id = "p_parent_1",
                name = "父子面板第二页",
                subPanelId = "sub_parent",
                components = listOf(
                    ComponentItem(id = "e2", type = ComponentType.SUB_PANEL_ENTRY, title = "二级", subPanelId = "sub_child")
                )
            ),
            PageConfig(
                id = "p_child",
                name = "子子面板",
                subPanelId = "sub_child"
            )
        )
        val subPanels = listOf(
            SubPanelConfig(id = "sub_parent", name = "父子面板"),
            SubPanelConfig(id = "sub_child", name = "子子面板")
        )

        val lockedChild = GlobalMinusOneState(
            pages = pages,
            subPanels = subPanels,
            advancedConfig = AdvancedConfig(
                panelOpenPageMode = PanelOpenPageMode.LOCKED_PAGE,
                lockedOpenPageId = "p_child"
            )
        )
        val target = resolvePanelOpenTarget(lockedChild)
        assertNotNull(target)
        assertEquals(3, target!!.pageIndex)
        assertEquals(listOf("sub_parent", "sub_child"), target.subPanelStack.stack)
        val mem = resolveSubPanelEntryPageMemory(lockedChild, target.subPanelStack)
        assertEquals("root_0", mem[""])
        assertEquals("p_parent_1", mem["sub_parent"])

        val recreatedLastVisited = GlobalMinusOneState(
            pages = pages,
            subPanels = subPanels,
            currentPageIndex = 3,
            advancedConfig = AdvancedConfig(panelOpenPageMode = PanelOpenPageMode.LAST_VISITED)
        )
        assertEquals(listOf("sub_parent", "sub_child"), resolveInitialSubPanelStack(recreatedLastVisited).stack)
    }

    @Test
    fun allComponentTypesHaveUniqueCnums() {
        val entries = ComponentType.entries
        val uniqueCnums = entries.mapTo(HashSet()) { it.cnum }
        assertEquals("Duplicate cnum detected in ComponentType", entries.size, uniqueCnums.size)
        assertEquals(ComponentType.APP_KEYBOARD_LAUNCHER, ComponentType.fromCnum(68))
        assertEquals(ComponentType.SUB_PANEL_ENTRY, ComponentType.fromCnum(69))
    }

    @Test
    fun legacyMax8SpecMigratesTo24AndFrecencyAndQueryHighlightWorkCleanly() {
        val legacyUri = "gmo-kbd-v1:layout=QWERTY&nums=1&clear=1&auto=0&max=8&labels=1"
        val migrated = AppKeyboardLauncherSpec.fromIntentUri(legacyUri)
        assertEquals(AppKeyboardLauncherSpec.DEFAULT_MAX_CANDIDATES, migrated.maxCandidates)
        assertEquals(24, migrated.maxCandidates)

        val explicitUri = AppKeyboardLauncherSpec(maxCandidates = 8).encodeToIntentUri()
        assertTrue(explicitUri.contains("&mv=2"))
        assertEquals(8, AppKeyboardLauncherSpec.fromIntentUri(explicitUri).maxCandidates)

        assertEquals(2..5, findDirectQueryHighlightRange("MyChromeApp", "chro"))
        assertEquals(0..1, findDirectQueryHighlightRange("微信支付", "微信"))
        assertNull(findDirectQueryHighlightRange("微信", "wx"))
        assertNull(findDirectQueryHighlightRange("微信", ""))

        val frecency = computeFrecencyRankOrder(
            counts = mapOf("com.freq" to 12, "com.latest" to 1, "com.mid" to 3),
            recentOrder = listOf("com.latest", "com.mid", "com.freq")
        )
        assertEquals(listOf("com.freq", "com.mid", "com.latest"), frecency)
    }
}
