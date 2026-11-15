package com.example.globalminusone

import com.example.globalminusone.data.*
import com.example.globalminusone.ui.panel.mutableContainerTarget
import org.junit.Assert.*
import org.junit.Test

class ContainerTargetPolicyTest {
    @Test fun resolvesOnlyTheRequestedContainerOnTheRequestedPage() {
        val container = ComponentItem(type = ComponentType.CONTAINER_BOX, id = "target")
        val other = ComponentItem(type = ComponentType.CONTAINER_BOX, id = "other")
        val state = GlobalMinusOneState(pages = listOf(PageConfig(name = "测试", components = listOf(other)), PageConfig(name = "测试", components = listOf(container))))
        assertEquals(container, mutableContainerTarget(state, 1, "target"))
        assertNull(mutableContainerTarget(state, 0, "target"))
        assertNull(mutableContainerTarget(state, 2, "target"))
        assertNull(mutableContainerTarget(state, 1, "deleted"))
    }
    @Test fun lockedAndWrongTypeTargetsCannotReceiveContainerWrites() {
        val container = ComponentItem(type = ComponentType.CONTAINER_BOX, id = "target")
        val state = GlobalMinusOneState(pages = listOf(PageConfig(name = "测试", components = listOf(container))))
        assertNull(mutableContainerTarget(state.copy(advancedConfig = state.advancedConfig.copy(layoutLocked = true)), 0, "target"))
        val wrongType = state.copy(pages = listOf(PageConfig(name = "测试", components = listOf(container.copy(type = ComponentType.APP_ICON)))))
        assertNull(mutableContainerTarget(wrongType, 0, "target"))
    }
}
