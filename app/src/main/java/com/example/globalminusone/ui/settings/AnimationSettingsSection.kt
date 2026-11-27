package com.example.globalminusone.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.CardReorderMotionStyle
import com.example.globalminusone.data.FOLLOW_GLOBAL_ANIMATION_MS
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.ModuleAnimationConfig
import com.example.globalminusone.data.PageTransition
import com.example.globalminusone.data.SubPanelTransitionStyle
import com.example.globalminusone.data.ThemeConfig
import com.example.globalminusone.data.formatModuleAnimationSummary
import kotlin.math.roundToInt

@Composable
internal fun AnimationSettingsSection(
    themeConfig: ThemeConfig,
    repository: MinusOneRepository
) {
    var showPageTransitionDialog by remember { mutableStateOf(false) }
    val modules = themeConfig.moduleAnimations
    val updateModules: ((ModuleAnimationConfig) -> ModuleAnimationConfig) -> Unit = { transform ->
        repository.updateThemeConfig { cfg -> cfg.copy(moduleAnimations = transform(cfg.moduleAnimations)) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "全局默认动画时长",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "未单独设定的模块均实时跟随此默认时长（当前 ${if (themeConfig.animationDurationMs <= 0) "0ms 关闭" else "${themeConfig.animationDurationMs}ms"}）；各模块面板内的动画调整也会与此处同步。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("0ms" to 0, "120ms" to 120, "260ms" to 260, "400ms" to 400).forEach { (label, ms) ->
                val selected = themeConfig.animationDurationMs == ms
                FilterChip(
                    selected = selected,
                    onClick = { repository.updateThemeConfig { it.copy(animationDurationMs = ms) } },
                    label = { Text(label, fontSize = 12.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Slider(
            value = themeConfig.animationDurationMs.coerceIn(0, 800).toFloat(),
            onValueChange = { v -> repository.updateThemeConfig { it.copy(animationDurationMs = v.roundToInt()) } },
            valueRange = 0f..800f
        )

        if (modules.hasCustomOverrides()) {
            OutlinedButton(
                onClick = { updateModules { ModuleAnimationConfig() } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("全部模块恢复跟随全局默认", fontSize = 13.sp)
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)

        SettingsModuleDurationBlock(
            title = "1. 面板呼出与收起",
            subtitle = "边缘滑动与悬浮球拉出/收起负一屏的弹簧速度",
            overrideMs = modules.panelOpenCloseDurationMs,
            globalMs = themeConfig.animationDurationMs,
            onChange = { ms -> updateModules { it.copy(panelOpenCloseDurationMs = ms) } }
        )

        SettingsModuleDurationBlock(
            title = "2. 页面切换与翻页转场",
            subtitle = "转场效果：${themeConfig.pageTransition.label}",
            overrideMs = modules.pageSwitchDurationMs,
            globalMs = themeConfig.animationDurationMs,
            onChange = { ms -> updateModules { it.copy(pageSwitchDurationMs = ms) } },
            extraHeaderAction = {
                TextButton(onClick = { showPageTransitionDialog = true }) {
                    Text("选择转场")
                }
            }
        )

        SettingsModuleDurationBlock(
            title = "3. 子面板切换",
            subtitle = "进入及退出子面板时的动画风格与速度（与子面板设置同步）",
            overrideMs = modules.subPanelDurationMs,
            globalMs = themeConfig.animationDurationMs,
            onChange = { ms -> updateModules { it.copy(subPanelDurationMs = ms) } }
        ) {
            SettingsEnumChipRow(
                options = SubPanelTransitionStyle.values().toList(),
                selected = modules.subPanelTransitionStyle,
                labelOf = { it.label },
                onSelect = { style -> updateModules { it.copy(subPanelTransitionStyle = style) } }
            )
        }

        SettingsModuleDurationBlock(
            title = "4. 卡片移动与换位",
            subtitle = "网格/自由/线性页面拖拽拾起、让位与归位手感（与网格设置同步）",
            overrideMs = modules.cardReorderDurationMs,
            globalMs = themeConfig.animationDurationMs,
            onChange = { ms -> updateModules { it.copy(cardReorderDurationMs = ms) } }
        ) {
            SettingsEnumChipRow(
                options = CardReorderMotionStyle.values().toList(),
                selected = modules.cardReorderStyle,
                labelOf = { it.label },
                onSelect = { style -> updateModules { it.copy(cardReorderStyle = style) } }
            )
        }

        SettingsModuleDurationBlock(
            title = "5. 文件夹、容器与浮窗",
            subtitle = "文件夹展开/收起、气泡弹窗、键盘浮窗与内置浏览器窗（与文件夹工具栏同步）",
            overrideMs = modules.containerPopupDurationMs,
            globalMs = themeConfig.animationDurationMs,
            onChange = { ms -> updateModules { it.copy(containerPopupDurationMs = ms) } }
        )

        SettingsModuleDurationBlock(
            title = "6. 弹窗、菜单与设置页切换",
            subtitle = "九键菜单、添加/属性弹窗、提示框与设置页导航转场",
            overrideMs = modules.modalMenuDurationMs,
            globalMs = themeConfig.animationDurationMs,
            onChange = { ms -> updateModules { it.copy(modalMenuDurationMs = ms) } }
        )
    }

    if (showPageTransitionDialog) {
        val transitions = PageTransition.values()
        SingleChoiceDialog(
            title = "页面切换转场",
            options = transitions.map { it.label },
            selectedIndex = transitions.indexOf(themeConfig.pageTransition),
            onSelect = { idx ->
                repository.updateThemeConfig { it.copy(pageTransition = transitions[idx]) }
                showPageTransitionDialog = false
            },
            onDismiss = { showPageTransitionDialog = false }
        )
    }
}

@Composable
private fun SettingsModuleDurationBlock(
    title: String,
    subtitle: String,
    overrideMs: Int,
    globalMs: Int,
    onChange: (Int) -> Unit,
    extraHeaderAction: (@Composable () -> Unit)? = null,
    extraContent: (@Composable () -> Unit)? = null
) {
    val followGlobal = overrideMs < 0
    val effectiveMs = if (followGlobal) globalMs.coerceIn(0, 800) else overrideMs.coerceIn(0, 800)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = formatModuleAnimationSummary(overrideMs, globalMs),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (extraHeaderAction != null) {
                    extraHeaderAction()
                }
            }
            extraContent?.invoke()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("跟随全局默认时长", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                Switch(
                    checked = followGlobal,
                    onCheckedChange = { follow ->
                        onChange(if (follow) FOLLOW_GLOBAL_ANIMATION_MS else globalMs.coerceIn(0, 800))
                    }
                )
            }
            if (!followGlobal) {
                Slider(
                    value = effectiveMs.toFloat(),
                    onValueChange = { onChange(it.roundToInt().coerceIn(0, 800)) },
                    valueRange = 0f..800f
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> SettingsEnumChipRow(
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(labelOf(option), fontSize = 11.sp) }
            )
        }
    }
}
