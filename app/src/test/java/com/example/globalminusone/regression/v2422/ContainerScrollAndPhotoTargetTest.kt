package com.example.globalminusone.regression.v2422

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollableState
import com.example.globalminusone.MemoryPreferences
import com.example.globalminusone.data.*
import com.example.globalminusone.ui.panel.PanelScrollTarget
import com.example.globalminusone.ui.panel.nativeContainerScrollRoute
import androidx.compose.foundation.ScrollState
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ContainerScrollAndPhotoTargetTest {
    @Test fun oneNativeNodeConsumesLocallyWithoutManuallyMovingItsAncestor() {
        val parent = ScrollState(40)
        val local = ScrollState(20)
        val route = nativeContainerScrollRoute(local, PanelScrollTarget(parent))
        assertSame(local, route.target.state)
        assertEquals(16f, route.target.state.dispatchRawDelta(16f), 0f)
        assertEquals(36, local.value)
        assertEquals(40, parent.value)
        assertEquals(-12f, route.target.state.dispatchRawDelta(-12f), 0f)
        assertEquals(24, local.value)
        assertEquals(40, parent.value)
    }

    @Test fun touchingNestedContainerStopsAllPreviousAncestorInertia() = runBlocking {
        val page = ScrollableState { it }
        val container = ScrollableState { it }
        val target = PanelScrollTarget(container, PanelScrollTarget(page))
        val pageFling = launch { page.scroll(MutatePriority.Default) { awaitCancellation() } }
        val containerFling = launch { container.scroll(MutatePriority.Default) { awaitCancellation() } }
        yield()
        assertTrue(page.isScrollInProgress)
        assertTrue(container.isScrollInProgress)
        target.stop()
        pageFling.join(); containerFling.join()
        assertFalse(page.isScrollInProgress)
        assertFalse(container.isScrollInProgress)
    }

    @Test fun nativeRouteReportsItsOwnBoundaryRatherThanBorrowingTheParentRange() {
        val local = ScrollState(0)
        val parent = ScrollState(40)
        val route = nativeContainerScrollRoute(local, PanelScrollTarget(parent))
        assertFalse(route.target.state.canScrollBackward)
        assertTrue(parent.canScrollBackward)
        assertEquals(0f, route.target.state.dispatchRawDelta(-20f), 0f)
        assertEquals(40, parent.value)
    }

    @Test fun photoPickerPreservesExactNestedTargetAndRejectsInvalidOrDeletedDestination() {
        val repo = MinusOneRepository(MemoryPreferences())
        val page = repo.state.value.pages.first()
        val container = ComponentItem(type = ComponentType.CONTAINER_BOX, title = "Album")
        repo.addComponentToPage(0, container)
        val images = listOf("content://test.images/one", "content://test.images/two")
        assertTrue(repo.savePhotoCube(page.id, null, images, container.id))
        val nested = repo.state.value.pages.first().components.findComponent(container.id)!!.containerComponents.single()
        assertEquals(ComponentType.PHOTO_CUBE, nested.type)
        assertEquals(images, nested.photoCubeUris)
        repo.updateComponent(0, nested.id) { it.copy(title = "Keep name") }
        assertTrue(repo.savePhotoCube(page.id, nested.id, images.reversed()))
        assertEquals("Keep name", repo.state.value.pages.first().components.findComponent(nested.id)!!.title)
        assertFalse(repo.savePhotoCube(page.id, nested.id, images + "file:///private"))
        repo.removeComponent(0, container.id)
        val before = repo.state.value.pages
        assertFalse(repo.savePhotoCube(page.id, null, images, container.id))
        assertEquals(before, repo.state.value.pages)
        assertTrue(repo.state.value.pages.first().components.none { it.type == ComponentType.PHOTO_CUBE })
    }
}
