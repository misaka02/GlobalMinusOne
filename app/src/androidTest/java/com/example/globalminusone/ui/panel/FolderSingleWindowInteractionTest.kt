package com.example.globalminusone.ui.panel

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.globalminusone.data.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Device regression: opening properties/confirmation must keep exactly the same
 * native Popup and preserve the underlying folder editing session. */
class FolderSingleWindowInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun propertiesAndConfirmationStayInFolderWindowAndKeepLiveLayout() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val child = ComponentItem(type = ComponentType.TEXT_LABEL, title = "保留子项")
        val folder = ComponentItem(type = ComponentType.FOLDER, title = "单窗口文件夹",
            containerLayout = ContainerLayoutType.FREE, containerComponents = listOf(child))
        val prefs = context.getSharedPreferences("folder-single-window-regression", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("state_json_v5", Json.encodeToString(
            GlobalMinusOneState(pages = listOf(PageConfig(name = "测试", components = listOf(folder)))))).commit()
        val repository = MinusOneRepository(prefs)
        compose.setContent {
            val state by repository.state.collectAsState()
            val current = state.pages.single().components.single()
            val palette = PanelPalette(Brush.verticalGradient(listOf(Color.White, Color.White)), Color.White,
                Color.Gray, Color.Black, Color.DarkGray, Color.Blue, false)
            MaterialTheme {
                CompositionLocalProvider(LocalPanelPalette provides palette) {
                    Box(Modifier.size(72.dp, 84.dp)) {
                        FolderContainerCard(current, 0, state, repository, null, false, {}, {}) {
                            Text("当前排列：${current.containerLayout}")
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("单窗口文件夹").performClick()
        compose.onNodeWithText("编辑内部布局").performClick()
        compose.onAllNodes(isPopup()).assertCountEquals(1)
        compose.onNodeWithText("文件夹属性").performClick()
        compose.onAllNodes(isPopup()).assertCountEquals(1)
        compose.onNodeWithText("容器与行为").performScrollTo().performClick()
        compose.onNodeWithText("内部排列").performClick()
        compose.onNodeWithText("自动排列").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(ContainerLayoutType.GRID, repository.state.value.pages.single().components.single().containerLayout)
            // A new repository reads the committed layout, rather than modal-local state.
            assertEquals(ContainerLayoutType.GRID, MinusOneRepository(prefs).state.value.pages.single().components.single().containerLayout)
        }
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("完成内部编辑").assertIsDisplayed()
        compose.onNodeWithText("当前排列：GRID").assertIsDisplayed()
        compose.onNodeWithText("文件夹属性").performClick()
        compose.onNodeWithText("重置与删除").performScrollTo().performClick()
        compose.onNodeWithText("删除卡片").performClick()
        compose.onNodeWithText("确认删除卡片？").assertIsDisplayed()
        compose.onAllNodes(isPopup()).assertCountEquals(1)
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("完成内部编辑").assertIsDisplayed()
        compose.runOnIdle { assertEquals(child.id, repository.state.value.pages.single().components.single().containerComponents.single().id) }
    }
}
