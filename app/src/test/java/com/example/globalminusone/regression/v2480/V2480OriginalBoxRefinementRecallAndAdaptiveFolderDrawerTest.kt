package com.example.globalminusone.regression.v2480

import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.ContainerLayoutType
import com.example.globalminusone.data.policy.containerGridChildBounds
import com.example.globalminusone.data.policy.containerPopupAdaptiveReferenceInnerWidthDp
import com.example.globalminusone.data.policy.containerPopupColumns
import com.example.globalminusone.data.policy.containerPopupWidthDp
import com.example.globalminusone.system.privilege.RootExecutor
import com.example.globalminusone.system.privilege.RootProcessOutcome
import com.example.globalminusone.system.privilege.UserRootSession
import com.example.globalminusone.ui.panel.gesture.commitContainerGridChildResize
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V2480OriginalBoxRefinementRecallAndAdaptiveFolderDrawerTest {

    @Test
    fun singleShrunkAppDrawerFolderAdaptivelyShrinksPopupWithoutDoubleShrinkingChild() {
        val defaultDrawer = ComponentItem(id = "drawer_1", type = ComponentType.APP_DRAWER_CARD, w = 32, h = 40)
        val baseFolder = ComponentItem(
            id = "folder_drawer",
            type = ComponentType.FOLDER,
            containerLayout = ContainerLayoutType.GRID,
            containerColumns = 4,
            containerItemGapDp = 8,
            containerPopupWidthDp = 0,
            containerComponents = listOf(defaultDrawer)
        )
        assertEquals(4, containerPopupColumns(baseFolder))
        assertEquals(360f, containerPopupWidthDp(baseFolder, 500f), 0.01f)
        assertEquals(0f, containerPopupAdaptiveReferenceInnerWidthDp(baseFolder, 500f), 0.01f)

        val shrunkByDrag = commitContainerGridChildResize(defaultDrawer, w = 16, h = 18, resizeWidth = true, resizeHeight = true)
        val dragFolder = baseFolder.copy(containerComponents = listOf(shrunkByDrag))
        assertEquals(2, containerPopupColumns(dragFolder))
        assertEquals(192f, containerPopupWidthDp(dragFolder, 500f), 0.01f)
        assertEquals(336f, containerPopupAdaptiveReferenceInnerWidthDp(dragFolder, 500f), 0.01f)

        val innerWidth = containerPopupWidthDp(dragFolder, 500f) - 24f
        val adaptiveRefWidth = containerPopupAdaptiveReferenceInnerWidthDp(dragFolder, 500f)
        val bounds = containerGridChildBounds(
            children = listOf(shrunkByDrag),
            columns = 32,
            widthDp = innerWidth,
            stepYDp = 10f,
            configuredHeightUnitDp = 10,
            iconColumns = containerPopupColumns(dragFolder),
            itemGapDp = dragFolder.containerItemGapDp,
            wReferenceWidthDp = adaptiveRefWidth
        ).single()
        val unreferenced = containerGridChildBounds(
            children = listOf(shrunkByDrag),
            columns = 32,
            widthDp = innerWidth,
            stepYDp = 10f,
            configuredHeightUnitDp = 10,
            iconColumns = containerPopupColumns(dragFolder),
            itemGapDp = dragFolder.containerItemGapDp
        ).single()
        assertEquals(2, bounds.spanCols)
        assertTrue("Shrunk child must fit inside 2 cols without double-shrinking: width=${bounds.width}", bounds.width in 150f..170f)
        assertTrue("Adaptive reference width must prevent double-shrinking (got ${bounds.width} vs ${unreferenced.width})", bounds.width > unreferenced.width * 1.5f)
        assertTrue("Explicit height 18 units at 10dp must preserve >=180dp: height=${bounds.height}", bounds.height >= 180f)
    }
}
