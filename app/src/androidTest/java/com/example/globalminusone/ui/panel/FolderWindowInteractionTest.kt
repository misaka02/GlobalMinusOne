package com.example.globalminusone.ui.panel

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.globalminusone.data.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual Compose measurement and pointer-chain regressions. Requires device execution;
 * JVM policy tests cannot prove either window visibility or hold continuity. */
class FolderWindowInteractionTest {
    @get:Rule val compose = createComposeRule()

    private fun mount(): Pair<MinusOneRepository, String> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val child = ComponentItem(type = ComponentType.APP_ICON, title = "内部测试应用", autoHeight = true)
        val folder = ComponentItem(type = ComponentType.FOLDER, title = "测试文件夹",
            containerLayout = ContainerLayoutType.FREE, containerComponents = listOf(child))
        val prefs = context.getSharedPreferences("folder-window-regression", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("state_json_v5", Json.encodeToString(
            GlobalMinusOneState(pages = listOf(PageConfig(name = "测试", components = listOf(folder)))))).commit()
        val repo = MinusOneRepository(prefs)
        compose.setContent {
            val state by repo.state.collectAsState()
            val current = state.pages[0].components.single()
            val palette = PanelPalette(Brush.verticalGradient(listOf(Color.White, Color.White)), Color.White,
                Color.Gray, Color.Black, Color.DarkGray, Color.Blue, false)
            MaterialTheme {
                CompositionLocalProvider(LocalPanelPalette provides palette) {
                    // Reproduce the real anchor constraint, rather than mounting a full-screen modal directly.
                    Box(Modifier.size(72.dp, 84.dp)) {
                        FolderContainerCard(current, 0, state, repo, null, false, {}, {}) {
                            NestedContainerComponents(current, 0, state, repo, {}) { item ->
                                AppContextMenuTarget("", item.title, true, {}, Modifier.fillMaxSize().testTag("folder-child")) {
                                    Text(item.title)
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("测试文件夹").performClick()
        return repo to child.id
    }

    @Test fun propertiesFromSmallAnchorShowBothLayoutChoicesAndPersistSelection() {
        val (repo, _) = mount()
        compose.onNodeWithText("文件夹属性").performClick()
        compose.onNodeWithText("容器与行为").performScrollTo().performClick()
        compose.onNodeWithText("内部排列").performClick()
        compose.onNodeWithText("自由摆放").assertIsDisplayed()
        compose.onNodeWithText("自动排列").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(ContainerLayoutType.GRID, repo.state.value.pages[0].components.single().containerLayout) }
    }

    @Test fun holdOwnershipTransitionKeepsReleaseMenuAndMovingDragAlive() {
        val (repo, childId) = mount()
        compose.onNodeWithTag("folder-child").performTouchInput { down(center); advanceEventTime(700); up() }
        compose.onNodeWithText("正常打开").assertIsDisplayed().performClick()
        compose.onNodeWithTag("folder-child").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        // The same pointer is still down: preview must be visible without stealing
        // its window focus, so that subsequent movement can become a card drag.
        compose.onNodeWithText("正常打开").assertIsDisplayed()
        compose.onNodeWithTag("folder-child").performTouchInput { moveBy(Offset(32f, 30f), 40); up() }
        compose.onNodeWithText("正常打开").assertDoesNotExist()
        compose.runOnIdle {
            val child = repo.state.value.pages[0].components.single().containerComponents.single { it.id == childId }
            assertTrue("Confirmed hold must survive ownership acquisition and commit movement", child.x > 0 || child.y > 0)
            assertFalse(repo.state.value.editMode)
        }
    }
}
