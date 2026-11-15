package com.example.globalminusone.ui.panel

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.globalminusone.data.*
import com.example.globalminusone.theme.SettingsTheme
import com.example.globalminusone.ui.settings.ThemeSettingsScreen
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual production surfaces, with bounded size only. These tests require a device;
 * adding/compiling them does not establish that the R10 freeze was fixed on hardware. */
class ContinuousNativeScrollInteractionTest {
    @get:Rule val compose = createComposeRule()

    private fun repository(name: String, components: List<ComponentItem> = emptyList()): MinusOneRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().clear().putString("state_json_v5", Json.encodeToString(
            GlobalMinusOneState(pages = listOf(PageConfig(name = "回归", components = components))))).commit()
        return MinusOneRepository(prefs)
    }

    private fun assertRepeatedScrolling() {
        val scroll = compose.onNode(hasScrollAction())
        fun position() = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        repeat(8) { index ->
            val before = position()
            scroll.performTouchInput {
                val x = width * .9f
                val upper = height * .35f
                val lower = height * .72f
                if (index % 2 == 0) swipe(Offset(x, lower), Offset(x, upper), 450)
                else swipe(Offset(x, upper), Offset(x, lower), 450)
            }
            compose.waitForIdle()
            val after = position()
            if (index % 2 == 0) assertTrue("Gesture ${index + 1} must move forward without data changes", after > before)
            else assertTrue("Gesture ${index + 1} must move backward without data changes", after < before)
        }
        scroll.performTouchInput {
            down(Offset(width * .9f, height * .72f))
            moveBy(Offset(0f, -height * .2f), 100)
            cancel()
        }
        compose.waitForIdle()
        val cancelledAt = position()
        scroll.performTouchInput { swipeDown(durationMillis = 450) }
        compose.waitForIdle()
        assertTrue("Cancellation must leave the next native drag usable", position() < cancelledAt)
    }

    @Test fun themeListAcceptsEightAlternatingDragsAndANewDragAfterCancellation() {
        val repo = repository("continuous-theme-scroll")
        val before = repo.state.value
        compose.setContent {
            SettingsTheme(false) {
                Box(Modifier.size(320.dp, 420.dp)) { ThemeSettingsScreen(repo, {}) }
            }
        }
        assertRepeatedScrolling()
        compose.runOnIdle { assertEquals("Scroll alone must not mutate settings", before, repo.state.value) }
    }

    @Test fun addPickerAcceptsEightDragsThenSearchAddsBatteryDetailsToOriginalContainer() {
        val folder = ComponentItem(type = ComponentType.FOLDER, title = "原目标文件夹")
        val repo = repository("continuous-add-scroll", listOf(folder))
        var dismissed = false
        val palette = PanelPalette(Brush.verticalGradient(listOf(Color.White, Color.White)), Color.White,
            Color.Gray, Color.Black, Color.DarkGray, Color.Blue, false)
        compose.setContent {
            SettingsTheme(false) {
                CompositionLocalProvider(LocalPanelPalette provides palette) {
                    Box(Modifier.size(320.dp, 560.dp)) {
                        AddComponentModal(0, repo, {}, { dismissed = true }, targetContainerId = folder.id)
                    }
                }
            }
        }
        assertRepeatedScrolling()
        compose.onNode(hasSetTextAction()).performTextInput("电量详情")
        compose.onNode(hasText("电量详情") and !hasSetTextAction()).performClick()
        compose.runOnIdle {
            assertTrue(dismissed)
            val top = repo.state.value.pages.single().components
            assertEquals(listOf(folder.id), top.map { it.id })
            assertEquals(ComponentType.STAT_BATTERY_DETAIL, top.single().containerComponents.single().type)
        }
    }

    @Test fun exclusiveClaimReleasesOnDisableCancellationAndDisposal() {
        val ownership = PanelGestureOwnership()
        val enabled = mutableStateOf(true)
        val mounted = mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalPanelGestureOwnership provides ownership) {
                if (mounted.value) Box(Modifier.size(180.dp).testTag("owner")
                    .ownPanelScrollGestures(enabled.value, lockFromDown = true))
            }
        }
        repeat(4) {
            compose.onNodeWithTag("owner").performTouchInput { down(center) }
            compose.runOnIdle { assertTrue(ownership.active); enabled.value = false }
            compose.runOnIdle { assertFalse(ownership.active); enabled.value = true }
            compose.onNodeWithTag("owner").performTouchInput { cancel() }
            compose.runOnIdle { assertFalse(ownership.active) }
        }
        compose.onNodeWithTag("owner").performTouchInput { down(center) }
        compose.runOnIdle { assertTrue(ownership.active); mounted.value = false }
        compose.runOnIdle { assertFalse(ownership.active) }
    }
}
