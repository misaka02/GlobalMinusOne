package com.example.globalminusone.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.MinusOneRepository

// ======================================================================================
// 矢量图标定义
// ======================================================================================

internal fun customIcon(name: String, pathData: String): ImageVector {
    return ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        fill = SolidColor(Color.Black)
    ).build()
}

internal val IconAccessibility: ImageVector by lazy {
    customIcon(
        "IconAccessibility",
        "M12 2c1.1 0 2 .9 2 2s-.9 2-2 2-2-.9-2-2 .9-2 2-2zm9 7h-6v13h-2v-6h-2v6H9V9H3V7h18v2z"
    )
}

internal val IconOverlayWindow: ImageVector by lazy {
    customIcon(
        "IconOverlayWindow",
        "M19 4H5c-1.11 0-2 .9-2 2v12c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 14H5V9h14v9zm0-11H5V6h14v1z"
    )
}

internal val IconFloppy: ImageVector by lazy {
    customIcon(
        "IconFloppy",
        "M17 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4zm-5 16c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm3-10H5V5h10v4z"
    )
}

internal val IconDoNotDisturb: ImageVector by lazy {
    customIcon(
        "IconDoNotDisturb",
        "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.64 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z"
    )
}

internal val IconBarChart: ImageVector by lazy {
    customIcon(
        "IconBarChart",
        "M4 9h4v11H4zm6-5h4v16h-4zm6 8h4v8h-4z"
    )
}

internal val IconPhoneAndroid: ImageVector by lazy {
    customIcon(
        "IconPhoneAndroid",
        "M17 1.01L7 1c-1.1 0-2 .9-2 2v18c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V3c0-1.1-.9-1.99-2-1.99zM17 19H7V5h10v14z"
    )
}

internal val IconProhibited: ImageVector by lazy {
    customIcon(
        "IconProhibited",
        "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.42 0-8-3.58-8-8 0-1.85.63-3.55 1.69-4.9L16.9 18.31C15.55 19.37 13.85 20 12 20zm6.31-3.1L7.1 5.69C8.45 4.63 10.15 4 12 4c4.42 0 8 3.58 8 8 0 1.85-.63 3.55-1.69 4.9z"
    )
}

internal val IconAndroid: ImageVector by lazy {
    customIcon(
        "IconAndroid",
        "M6 18c0 .55.45 1 1 1h1v3.5c0 .83.67 1.5 1.5 1.5s1.5-.67 1.5-1.5V19h4v3.5c0 .83.67 1.5 1.5 1.5s1.5-.67 1.5-1.5V19h1c.55 0 1-.45 1-1V8H6v10zM3.5 8C2.67 8 2 8.67 2 9.5v7c0 .83.67 1.5 1.5 1.5S5 17.33 5 16.5v-7C5 8.67 4.33 8 3.5 8zm17 0c-.83 0-1.5.67-1.5 1.5v7c0 .83.67 1.5 1.5 1.5s1.5-.67 1.5-1.5v-7c0-.83-.67-1.5-1.5-1.5zm-4.97-4.84l1.3-1.3c.2-.2.2-.51 0-.71-.2-.2-.51-.2-.71 0l-1.48 1.48C13.85 2.23 12.95 2 12 2c-.96 0-1.86.23-2.66.63L7.85 1.15c-.2-.2-.51-.2-.71 0-.2.2-.2.51 0 .71l1.31 1.31C6.97 4.26 6 6.01 6 8h12c0-1.99-.97-3.75-2.47-4.84zM10 5H9V4h1v1zm5 0h-1V4h1v1z"
    )
}

internal val IconPuzzle: ImageVector by lazy {
    customIcon(
        "IconPuzzle",
        "M20.5 11H19V7c0-1.1-.9-2-2-2h-4V3.5C13 2.12 11.88 1 10.5 1S8 2.12 8 3.5V5H4c-1.1 0-1.99.9-1.99 2v3.8H3.5c1.49 0 2.7 1.21 2.7 2.7s-1.21 2.7-2.7 2.7H2V20c0 1.1.9 2 2 2h3.8v-1.5c0-1.49 1.21-2.7 2.7-2.7 1.49 0 2.7 1.21 2.7 2.7V22H17c1.1 0 2-.9 2-2v-4h1.5c1.38 0 2.5-1.12 2.5-2.5s-1.12-2.5-2.5-2.5z"
    )
}

internal val IconHelp: ImageVector by lazy {
    customIcon(
        "IconHelp",
        "M11 18h2v-2h-2v2zm1-16C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm0-14c-2.21 0-4 1.79-4 4h2c0-1.1.9-2 2-2s2 .9 2 2c0 2-3 1.75-3 5h2c0-2.25 3-2.5 3-5 0-2.21-1.79-4-4-4z"
    )
}

internal val IconInfo: ImageVector by lazy {
    customIcon(
        "IconInfo",
        "M11 17h2v-6h-2v6zm1-15C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm-1-13h2v2h-2v-2z"
    )
}

// ======================================================================================
// 关于页面 ("ABOUT") 与使用教程弹窗
// ======================================================================================

private const val PROJECT_DEVELOPER = "misaka02"
private const val PROJECT_DEVELOPER_URL = "https://github.com/misaka02"
private const val PROJECT_REPO_URL = "https://github.com/misaka02/GlobalMinusOne"
private const val PROJECT_ISSUES_URL = "https://github.com/misaka02/GlobalMinusOne/issues"

private fun openProjectWebUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, "未找到可用浏览器，请手动访问：$url", Toast.LENGTH_SHORT).show()
    }
}

@Composable
internal fun AboutSettingsScreen(
    repository: MinusOneRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val versionLabel = remember(context) {
        runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            "v${info.versionName ?: "2.4.43"}"
        }.getOrDefault("v2.4.43")
    }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SubPageTopBar(title = "关于", onBack = onBack)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 36.dp)
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "全局负一屏 · GlobalMinusOne $versionLabel",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "项目来源：本开源项目灵感与设计延续自已停更的原 Android 应用《全局负一屏》，由 $PROJECT_DEVELOPER 发起并主要由 AI 辅助与主导完成架构重构、代码实现与测试（AI-Developed / AI-Assisted），基于 Kotlin + Jetpack Compose 独立实现并遵循 Apache License 2.0 协议开源；与原闭源版本及原作者无隶属或官方延续关系。",
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "开发者：$PROJECT_DEVELOPER · 仓库：$PROJECT_REPO_URL",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            item { PreferenceCategoryTitle(title = "项目与开发者信息") }

            item {
                PreferenceItem(
                    title = "开发者",
                    summary = "$PROJECT_DEVELOPER（$PROJECT_DEVELOPER_URL）",
                    onClick = { openProjectWebUrl(context, PROJECT_DEVELOPER_URL) }
                )
            }

            item {
                PreferenceItem(
                    title = "开源项目地址",
                    summary = PROJECT_REPO_URL,
                    onClick = { openProjectWebUrl(context, PROJECT_REPO_URL) }
                )
            }

            item {
                PreferenceItem(
                    title = "问题反馈与交流",
                    summary = "$PROJECT_ISSUES_URL（点击前往 GitHub Issues 提交反馈）",
                    onClick = { openProjectWebUrl(context, PROJECT_ISSUES_URL) }
                )
            }

            item { PreferenceDivider() }

            item { PreferenceCategoryTitle(title = "合规与维护") }

            item {
                PreferenceItem(
                    title = "开源许可与第三方声明",
                    summary = "Apache-2.0 · 包含 AI 开发说明及 RE2/J、Shizuku API、libsu 归属",
                    onClick = { showLicensesDialog = true }
                )
            }

            item {
                PreferenceItem(
                    title = "隐私政策",
                    summary = "点击查看软件隐私合规与数据安全声明",
                    onClick = { showPrivacyDialog = true }
                )
            }

            item {
                PreferenceItem(
                    title = "恢复初始默认布局",
                    summary = "重置负一屏所有卡片与排版为初始状态",
                    onClick = {
                        if (repository.state.value.advancedConfig.layoutLocked) {
                            Toast.makeText(context, "请先解锁布局，再恢复默认", Toast.LENGTH_SHORT).show()
                        } else {
                            showResetConfirmDialog = true
                        }
                    }
                )
            }
        }
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = {
                com.example.globalminusone.ui.Android12WindowKeyGuard()
                Text("恢复初始默认布局", fontWeight = FontWeight.Bold)
            },
            text = { Text("此操作将清除所有自定义卡片与排版，恢复为默认的线性卡片流（应用、快捷方式、支付、网站与操作栏），是否确认重置？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (repository.state.value.advancedConfig.layoutLocked) {
                            Toast.makeText(context, "请先解锁布局，再恢复默认", Toast.LENGTH_SHORT).show()
                        } else {
                            repository.resetToDefaults()
                            Toast.makeText(context, "已恢复初始默认布局", Toast.LENGTH_SHORT).show()
                        }
                        showResetConfirmDialog = false
                    }
                ) {
                    Text("确认重置", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = {
                com.example.globalminusone.ui.Android12WindowKeyGuard()
                Text("隐私政策与合规声明", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "《全局负一屏》严格保护您的隐私数据：\n\n" +
                            "1. 本软件为纯本地离线效率工具，无需任何网络服务器，不收集、不出售任何个人隐私或设备信息；\n" +
                            "2. 悬浮窗与无障碍服务权限仅用于在屏幕边缘识别滑动或触摸以呼出负一屏面板，不监听任何按键或文本输入；\n" +
                            "3. 存储权限仅用于读取与备份用户自定义的配置文件；\n" +
                            "4. 所有应用列表、快捷方式与配置数据仅保存在您手机本地的私有存储空间中。"
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("同意并关闭", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = {
                com.example.globalminusone.ui.Android12WindowKeyGuard()
                Text("开源许可与第三方声明", fontWeight = FontWeight.Bold)
            },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text(
                            text = "本应用以 Apache License 2.0 协议开源，灵感与设计延续自已停更的原《全局负一屏》，由 AI 辅助与主导独立重构并由 $PROJECT_DEVELOPER 维护发布（项目地址：$PROJECT_REPO_URL）。感谢并声明所使用第三方开源组件：",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    item {
                        Text(
                            text = "• AndroidX / Jetpack Compose / Kotlin / Kotlinx Serialization (Apache-2.0)\n" +
                                "• Google RE2/J 1.8 (Go / BSD-3-Clause, Copyright 2009 The Go Authors)\n" +
                                "• RikkaApps Shizuku API 13.1.5 (MIT License, Copyright 2021 RikkaW)\n" +
                                "• topjohnwu libsu 6.0.0 (Apache-2.0，包含本地 root-session-retry 补丁；完整许可原文打包于应用 assets/licenses 及 assets/third_party/)",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) {
                    Text("关闭", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }
}

@Composable
internal fun TutorialDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            com.example.globalminusone.ui.Android12WindowKeyGuard()
            Text("全局负一屏 · 使用教程", fontWeight = FontWeight.Bold)
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text(
                        text = "1. 侧边滑出：\n在任意界面从屏幕预设边缘（默认左侧70%高度处）向内滑动，即可呼出沉浸式负一屏面板。",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                item {
                    Text(
                        text = "2. 底部呼出 9 键菜单：\n在负一屏面板非卡片区域，从屏幕底部向上轻滑超过50px，或点击卡片底部的【+】操作栏卡片，即可呼出底部的 9 键管理浮层工具栏！",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                item {
                    Text(
                        text = "3. 编辑与组件添加：\n在 9 键工具栏中点击【编辑模式】，卡片右上角会出现红色序号，长按卡片可修改尺寸、坐标与外观；点击【添加】可自由选用 22 类系统功能组件。",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                item {
                    Text(
                        text = "4. 自由布局网格：\n支持新建自由布局分页，支持在 32 等分网格坐标系中拖动移动、拉伸 4 个边缘缩放，随心所欲定制您的专属桌面！",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("我知道了", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}
