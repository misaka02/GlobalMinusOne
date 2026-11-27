package com.example.globalminusone.ui.panel

import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.PageConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.ui.panel.cards.AppMenuReadState
import com.example.globalminusone.ui.panel.cards.CardLayoutSnapshot
import com.example.globalminusone.ui.panel.cards.CardStateStreams
import com.example.globalminusone.ui.panel.cards.hasSameLayoutInputs

@OptIn(ExperimentalCoroutinesApi::class)
class CardStateStreamsTest {
    private fun state() = GlobalMinusOneState(pages = listOf(PageConfig(id = "page", name = "Page")))

    @Test fun unrelatedUpdatesDoNotEmitAndBothMenuInputsRemainLive() = runTest {
        val source = MutableStateFlow(state())
        val streams = CardStateStreams(source)
        val menu = mutableListOf<AppMenuReadState>()
        val layout = mutableListOf<CardLayoutSnapshot>()
        val menuJob = backgroundScope.launch { streams.menu.collect { menu.add(it) } }
        val layoutJob = backgroundScope.launch { streams.layout.collect { layout.add(it) } }
        runCurrent()
        assertEquals(1, menu.size)
        assertEquals(1, layout.size)
        source.value = source.value.copy(noteText = "Changed note")
        runCurrent()
        source.value = source.value.copy(clipboardHistory = emptyList())
        runCurrent()
        assertEquals(1, menu.size)
        assertEquals(1, layout.size)

        source.value = source.value.copy(advancedConfig = source.value.advancedConfig.copy(
            autoCloseOnWidgetClick = !source.value.advancedConfig.autoCloseOnWidgetClick
        ))
        runCurrent()
        assertEquals(2, menu.size)
        assertEquals(1, layout.size)
        source.value = source.value.copy(advancedConfig = source.value.advancedConfig.copy(
            layoutLocked = !source.value.advancedConfig.layoutLocked
        ))
        runCurrent()
        assertEquals(3, menu.size)
        assertEquals(2, layout.size)
        assertEquals(source.value.advancedConfig.layoutLocked, menu.last().layoutLocked)
        assertEquals(source.value.advancedConfig.autoCloseOnWidgetClick, menu.last().autoCloseOnWidgetClick)
        menuJob.cancelAndJoin()
        layoutJob.cancelAndJoin()
        assertEquals(0, source.subscriptionCount.value)
    }

    @Test fun everyLayoutInputEmitsWhileUnchangedProjectionIsSuppressed() = runTest {
        val source = MutableStateFlow(state())
        val streams = CardStateStreams(source)
        val seen = mutableListOf<CardLayoutSnapshot>()
        backgroundScope.launch { streams.layout.collect { seen.add(it) } }
        runCurrent()
        val changes: List<(GlobalMinusOneState) -> GlobalMinusOneState> = listOf(
            { it.copy(editMode = !it.editMode) },
            { it.copy(advancedConfig = it.advancedConfig.copy(layoutLocked = !it.advancedConfig.layoutLocked)) },
            { it.copy(selectedComponentId = "selected") },
            { it.copy(advancedConfig = it.advancedConfig.copy(gridColumns = it.advancedConfig.gridColumns + 1)) },
            { it.copy(advancedConfig = it.advancedConfig.copy(heightUnitDp = it.advancedConfig.heightUnitDp + 1)) },
            { it.copy(themeConfig = it.themeConfig.copy(animationDurationMs = it.themeConfig.animationDurationMs + 1)) },
            { it.copy(pages = listOf(it.pages.single().copy(name = "Renamed"))) }
        )
        changes.forEachIndexed { index, change ->
            source.value = change(source.value)
            runCurrent()
            assertEquals(index + 2, seen.size)
            val actual = seen.last()
            val current = source.value
            assertSame(current.pages, actual.pages)
            assertEquals(current.editMode, actual.editMode)
            assertEquals(current.advancedConfig.layoutLocked, actual.layoutLocked)
            assertEquals(current.selectedComponentId, actual.selectedComponentId)
            assertEquals(current.advancedConfig.gridColumns, actual.gridColumns)
            assertEquals(current.advancedConfig.heightUnitDp, actual.heightUnitDp)
            assertEquals(current.themeConfig.animationDurationMs, actual.animationDurationMs)
        }
    }

    private class EqualsProbeList<T>(private val items: List<T>) : AbstractList<T>() {
        var equalsCalls = 0
        override val size: Int get() = items.size
        override fun get(index: Int): T = items[index]
        override fun equals(other: Any?): Boolean {
            equalsCalls++
            return super.equals(other)
        }
    }

    @Test fun equalContentNewPagesReferenceIsDeliveredWithoutProjectionTreeEquality() = runTest {
        var nested = ComponentItem(id = "leaf", type = ComponentType.APP_ICON)
        repeat(40) { depth ->
            nested = ComponentItem(id = "container$depth", type = ComponentType.CONTAINER_BOX,
                containerComponents = listOf(nested))
        }
        val firstPages = EqualsProbeList(listOf(PageConfig(id = "page", name = "Page", components = listOf(nested))))
        val nextPages = EqualsProbeList(firstPages.toList())
        val source = MutableStateFlow(state().copy(pages = firstPages))
        val streams = CardStateStreams(source)
        val seen = mutableListOf<CardLayoutSnapshot>()
        backgroundScope.launch { streams.layout.collect { seen.add(it) } }
        runCurrent()
        // StateFlow compares full states during collection too; flow assertions below verify
        // delivery, while comparator counters are isolated after collection has settled.
        source.value = source.value.copy(pages = nextPages, noteText = "force upstream emission")
        runCurrent()
        assertEquals(2, seen.size)
        assertSame(nextPages, seen.last().pages)
        assertSame(nested, seen.last().pages.single().components.single())
        firstPages.equalsCalls = 0
        nextPages.equalsCalls = 0
        assertFalse(seen.first().hasSameLayoutInputs(seen.last()))
        assertTrue(seen.last().hasSameLayoutInputs(seen.last().copy()))
        assertEquals(0, firstPages.equalsCalls)
        assertEquals(0, nextPages.equalsCalls)

        source.value = source.value.copy(noteText = "unrelated")
        runCurrent()
        assertEquals(2, seen.size)
    }

    @Test fun currentValuesAndColdResubscriptionUseLatestOwnerWithoutBackgroundCollection() = runTest {
        val first = MutableStateFlow(state())
        val second = MutableStateFlow(state().copy(editMode = true, selectedComponentId = "second"))
        val firstStreams = CardStateStreams(first)
        val secondStreams = CardStateStreams(second)
        assertEquals(0, first.subscriptionCount.value)
        assertEquals(0, second.subscriptionCount.value)
        assertTrue(secondStreams.currentLayout.editMode)
        assertEquals("second", secondStreams.currentLayout.selectedComponentId)
        val seen = mutableListOf<CardLayoutSnapshot>()
        val oldJob = backgroundScope.launch { firstStreams.layout.collect { seen.add(it) } }
        runCurrent()
        oldJob.cancelAndJoin()
        first.value = first.value.copy(selectedComponentId = "old owner changed")
        second.value = second.value.copy(selectedComponentId = "latest second")
        assertEquals("latest second", secondStreams.currentLayout.selectedComponentId)
        second.value = second.value.copy(advancedConfig = second.value.advancedConfig.copy(
            layoutLocked = !second.value.advancedConfig.layoutLocked,
            autoCloseOnWidgetClick = !second.value.advancedConfig.autoCloseOnWidgetClick
        ))
        assertEquals(second.value.advancedConfig.layoutLocked, secondStreams.currentMenu.layoutLocked)
        assertEquals(second.value.advancedConfig.autoCloseOnWidgetClick, secondStreams.currentMenu.autoCloseOnWidgetClick)
        val nextJob = backgroundScope.launch { secondStreams.layout.collect { seen.add(it) } }
        runCurrent()
        assertEquals(2, seen.size)
        assertEquals("latest second", seen.last().selectedComponentId)
        assertEquals(0, first.subscriptionCount.value)
        assertEquals(1, second.subscriptionCount.value)
        nextJob.cancelAndJoin()
        assertEquals(0, second.subscriptionCount.value)
    }
}
