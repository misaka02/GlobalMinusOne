package com.example.globalminusone.ui.panel

import com.example.globalminusone.data.isContainerType

import android.content.Intent
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import com.example.globalminusone.data.*
import com.example.globalminusone.system.PanelFileBrowser
import com.example.globalminusone.browser.EmbeddedAppTargets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val LocalAppEditAction = staticCompositionLocalOf<(() -> Unit)?> { null }
internal val LocalAppEditLabel = staticCompositionLocalOf { "编辑应用卡片" }
private data class AppMenuTargets(val launch: ComponentName? = null, val embedded: ComponentName? = null,
    val info: ApplicationInfo? = null)

internal fun isAppContextMenuCardType(type: ComponentType): Boolean = type in setOf(
    ComponentType.APP_ICON,
    ComponentType.CUSTOM_SHORTCUT,
    ComponentType.STATIC_SHORTCUT,
    ComponentType.SHELL_COMMAND,
    ComponentType.WEBSITE_LINK,
    ComponentType.FILE_SHORTCUT
)

@Composable
internal fun AppContextMenuTarget(
    packageName: String, label: String, enabled: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = LocalAppEditAction.current,
    editLabel: String = LocalAppEditLabel.current,
    legacyAction: QuickActionItem? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val folderInteraction = LocalFolderItemInteraction.current
    val folderEdit = LocalFolderEditAction.current
    val folderReturn = LocalFolderReturnState.current
    val effectiveEnabled = enabled || folderInteraction != null
    val env = LocalAppMenuEnvironment.current
    val repository = env?.repository
    val liveState = repository?.state?.collectAsState()?.value
    val locked = liveState?.advancedConfig?.layoutLocked ?: LocalLayoutLocked.current
    val card = env?.item?.takeIf { legacyAction == null && isAppContextMenuCardType(it.type) }
    val editable = repository != null && !locked
    var expanded by remember(packageName, label) { mutableStateOf(false) }
    var heldMenu by remember(packageName, label) { mutableStateOf(false) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var openModes by remember(packageName) { mutableStateOf(false) }
    val latestClick by rememberUpdatedState(onClick)
    DisposableEffect(folderInteraction, packageName, label) {
        val menu: () -> Unit = {
            heldMenu = true; expanded = true
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        }
        val finish: () -> Unit = { heldMenu = false; expanded = true }
        val dismiss: () -> Unit = { heldMenu = false; expanded = false; openModes = false }
        val tap: () -> Unit = { latestClick() }
        folderInteraction?.openMenu = menu
        folderInteraction?.finishHoldMenu = finish
        folderInteraction?.dismissHeldMenu = dismiss
        folderInteraction?.tap = tap
        onDispose {
            folderInteraction?.let {
                if (it.openMenu === menu) it.openMenu = null
                if (it.finishHoldMenu === finish) it.finishHoldMenu = null
                if (it.dismissHeldMenu === dismiss) it.dismissHeldMenu = null
                if (it.tap === tap) it.tap = null
            }
        }
    }
    var dialog by remember { mutableStateOf("") }
    val openEmbedded = LocalOpenEmbeddedApplication.current
    val closePanel = LocalClosePanelForExternalApp.current
    val targets by produceState(AppMenuTargets(), context, packageName, expanded, dialog == "uninstall", openEmbedded != null) {
        value = AppMenuTargets()
        if ((expanded || dialog == "uninstall") && packageName.isNotBlank()) value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val launch = runCatching { pm.getLaunchIntentForPackage(packageName)?.component }.getOrNull()
            val info = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull()
            val embedded = if (expanded && openEmbedded != null && launch != null)
                EmbeddedAppTargets.resolve(context, launch) else null
            AppMenuTargets(launch, embedded, info)
        }
    }
    val embeddedComponent = targets.embedded
    val applicationInfo = targets.info
    var rename by remember(label) { mutableStateOf(label) }
    fun failed() { Toast.makeText(context, "布局已锁定或目标已失效", Toast.LENGTH_SHORT).show() }
    fun mutateCard(transform: (ComponentItem) -> ComponentItem): Boolean {
        val repo = repository ?: return false
        val state = repo.state.value
        val pageIndex = state.pages.indexOfFirst { it.id == env?.pageId }
        if (state.advancedConfig.layoutLocked || pageIndex < 0 || card == null || state.pages[pageIndex].components.findComponent(card.id) == null) return false
        repo.updateComponent(pageIndex, card.id, transform)
        return true
    }
    fun mutateLegacy(transform: (QuickActionItem) -> QuickActionItem): Boolean {
        val repo = repository ?: return false
        val state = repo.state.value
        val pageIndex = state.pages.indexOfFirst { it.id == env?.pageId }
        val parent = state.pages.getOrNull(pageIndex)?.components?.findComponent(env?.item?.id.orEmpty()) ?: return false
        if (state.advancedConfig.layoutLocked || legacyAction == null || parent.containerActions.none { it.id == legacyAction.id } != false) return false
        repo.updateComponent(pageIndex, parent.id) { it.copy(containerActions = it.containerActions.map { action ->
            if (action.id == legacyAction.id) transform(action) else action
        }) }
        return true
    }
    Box(modifier.semantics(mergeDescendants = true) {
        contentDescription = label
        if (effectiveEnabled) onLongClick(label = "打开应用或快捷方式菜单") { expanded = true; true }
    }.cardLongPressToEdit(packageName to label, effectiveEnabled && (folderInteraction == null || locked)) { expanded = true }
        .clickable(enabled = effectiveEnabled, onClick = onClick)) {
        content()
        DropdownMenu(expanded = expanded && effectiveEnabled,
            properties = PopupProperties(focusable = !heldMenu),
            onDismissRequest = { expanded = false; heldMenu = false; openModes = false }) {
            com.example.globalminusone.service.Android12WindowKeyGuard()

            if (openModes) {
                DropdownMenuItem(text = { Text("‹ 应用菜单") }, onClick = { openModes = false })
                DropdownMenuItem(text = { Text("正常打开") }, onClick = {
                    expanded = false; openModes = false
                    if (card?.type == ComponentType.APP_ICON && card.appLaunchMode == AppLaunchMode.FLOATING) {
                        com.example.globalminusone.system.AppAndIntentLauncher.launchApp(context, packageName)
                        if (liveState?.advancedConfig?.autoCloseOnWidgetClick == true) closePanel?.invoke()
                    } else onClick()
                })
                if (embeddedComponent != null && openEmbedded != null)
                    DropdownMenuItem(text = { Text("面板小窗") }, onClick = {
                        expanded = false; openModes = false
                        openEmbedded(EmbeddedApplicationTarget(embeddedComponent, label))
                    })
            } else {
            DropdownMenuItem(text = { Text(if (packageName.isNotBlank()) "打开方式 ›" else "正常打开") }, onClick = {
                if (packageName.isNotBlank()) openModes = true else { expanded = false; onClick() }
            })
            if (embeddedComponent != null && openEmbedded != null) {
                DropdownMenuItem(text = { Text("面板小窗") }, onClick = {
                    expanded = false
                    openEmbedded(EmbeddedApplicationTarget(embeddedComponent, label))
                })
            }
            if (repository != null) {
                HorizontalDivider()
                DropdownMenuItem(text = { Text("编辑布局") }, enabled = editable,
                    onClick = {
                        expanded = false
                        val current = repository.state.value
                        val target = current.pages.firstOrNull { it.id == env?.pageId }?.components?.findComponent(env?.item?.id.orEmpty())
                        if (target != null && !current.advancedConfig.layoutLocked) {
                            if (folderEdit != null) folderEdit() else repository.setEditMode(true)
                            repository.selectComponent(target.id)
                        } else failed()
                    })
                if (onEdit != null) DropdownMenuItem(text = { Text(if (legacyAction != null) "编辑启动动作" else editLabel) },
                    enabled = !locked, onClick = { expanded = false; onEdit() })
                DropdownMenuItem(text = { Text("重命名") }, enabled = editable,
                    onClick = { expanded = false; rename = label; dialog = "rename" })
                DropdownMenuItem(text = { Text(if (legacyAction == null) "更换图标" else "选择内置图标") }, enabled = editable,
                    onClick = {
                        expanded = false
                        when {
                            legacyAction != null -> dialog = "legacy-icon"
                            card != null -> { folderReturn?.clear(); PanelFileBrowser.requestComponentIcon(context, env!!.pageId, card.id) }
                            packageName.isNotBlank() -> { folderReturn?.clear(); PanelFileBrowser.requestAppIcon(context, packageName) }
                        }
                    })
                DropdownMenuItem(text = { Text("恢复默认图标") }, enabled = editable, onClick = {
                    expanded = false
                    val saved = when {
                        legacyAction != null -> mutateLegacy { it.copy(iconName = "bolt") }
                        card != null -> mutateCard { it.copy(iconUri = "") }
                        else -> repository.updateAppAppearance(packageName, iconUri = "")
                    }
                    if (!saved) failed()
                })
                if (card == null && legacyAction == null && packageName.isNotBlank()) {
                    DropdownMenuItem(text = { Text("恢复应用原名") }, enabled = editable, onClick = {
                        expanded = false
                        if (!repository.updateAppAppearance(packageName, displayName = "")) failed()
                    })
                }
                HorizontalDivider()
                DropdownMenuItem(text = { Text(if (card != null) "复制到页面 / 容器" else "添加到页面 / 容器") },
                    enabled = editable, onClick = { expanded = false; dialog = "copy" })
                if (card != null) DropdownMenuItem(text = { Text("移动到页面 / 容器") }, enabled = editable,
                    onClick = { expanded = false; dialog = "move" })
                if (card != null || legacyAction != null) {
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("移除此卡片…", color = MaterialTheme.colorScheme.error) }, enabled = editable,
                        onClick = { expanded = false; dialog = "remove" })
                }
            } else if (onEdit != null) {
                DropdownMenuItem(text = { Text(if (legacyAction != null) "编辑启动动作" else editLabel) },
                    enabled = !locked, onClick = { expanded = false; onEdit() })
            }
            if (packageName.isNotBlank()) {
                HorizontalDivider()
                DropdownMenuItem(text = { Text("应用详情") }, onClick = {
                    expanded = false
                    try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    catch (_: Exception) { Toast.makeText(context, "无法打开应用详情", Toast.LENGTH_SHORT).show() }
                })
            }
            if (applicationInfo != null && packageName != context.packageName &&
                applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0)
                DropdownMenuItem(text = { Text("卸载应用", color = MaterialTheme.colorScheme.error) }, onClick = {
                    expanded = false; dialog = "uninstall"
                })
            }
        }
    }
    if (dialog == "uninstall") PanelAlertDialog(onDismissRequest = { dialog = "" }, title = { Text("卸载“$label”？") },
        text = { Text("将交给系统再次确认卸载，应用数据可能被删除。") },
        confirmButton = { TextButton(onClick = {
            dialog = ""
            try {
                val current = context.packageManager.getApplicationInfo(packageName, 0)
                check(current.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0)
                check(packageName != context.packageName)
                closePanel?.invoke()
                context.startActivity(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) { Toast.makeText(context, "无法卸载此应用", Toast.LENGTH_SHORT).show() }
        }) { Text("继续", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("取消") } })
    if (dialog == "rename") PanelAlertDialog(onDismissRequest = { dialog = "" }, title = { Text("重命名 · $label") }, text = {
        Column {
            Text(if (card == null && legacyAction == null) "只改变负一屏中的应用显示名称，不修改系统应用。" else "只修改当前卡片名称。")
            OutlinedTextField(value = rename, onValueChange = { rename = it.take(100) }, singleLine = true, label = { Text("名称") })
        }
    }, confirmButton = { TextButton(enabled = rename.isNotBlank(), onClick = {
        val saved = when {
            legacyAction != null -> mutateLegacy { it.copy(title = rename.trim()) }
            card != null -> mutateCard { it.copy(title = rename.trim()) }
            else -> repository?.updateAppAppearance(packageName, displayName = rename.trim()) == true
        }
        if (!saved) failed()
        dialog = ""
    }) { Text("保存") } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("取消") } })
    if (dialog == "legacy-icon") PanelAlertDialog(onDismissRequest = { dialog = "" }, title = { Text("选择内置图标") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            listOf("bolt" to "闪电", "dashboard" to "仪表盘", "settings" to "设置", "explore" to "浏览", "storage" to "存储").forEach { (icon, name) ->
                TextButton(onClick = { if (!mutateLegacy { it.copy(iconName = "custom:$icon") }) failed(); dialog = "" }) { Text(name) }
            }
        }
    }, confirmButton = { TextButton(onClick = { dialog = "" }) { Text("取消") } })
    if (dialog == "remove") PanelAlertDialog(onDismissRequest = { dialog = "" }, title = { Text("移除卡片？") },
        text = { Text("从当前位置移除“$label”，不会卸载应用。") }, confirmButton = { TextButton(onClick = {
            val state = repository?.state?.value
            val index = state?.pages?.indexOfFirst { it.id == env?.pageId } ?: -1
            if (state == null || state.advancedConfig.layoutLocked || index < 0) failed()
            else if (legacyAction != null) repository?.removeActionFromContainer(index, env!!.item.id, legacyAction.id)
            else if (card != null) repository?.removeComponent(index, card.id)
            dialog = ""
        }) { Text("移除", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("取消") } })
    if ((dialog == "copy" || dialog == "move") && repository != null && env != null) {
        val moving = dialog == "move"
        ComponentDestinationDialog(repository, card?.id, if (moving) "移动到" else "添加 / 复制到", { dialog = "" }) { pageId, containerId ->
            val saved = if (card != null) repository.transferComponent(env.pageId, card.id, pageId, containerId, copy = !moving)
            else {
                val appearance = repository.state.value.appAppearanceOverrides[packageName]
                val created = if (legacyAction != null) ComponentItem(type = ComponentType.CUSTOM_SHORTCUT,
                    title = legacyAction.title, packageName = legacyAction.packageName, intentUri = legacyAction.intentUri, iconName = legacyAction.iconName)
                else ComponentItem(type = ComponentType.APP_ICON, title = appearance?.displayName?.takeIf { it.isNotBlank() } ?: label,
                    packageName = packageName, iconUri = appearance?.iconUri.orEmpty())
                repository.placeMenuComponent(pageId, containerId, created)
            }
            if (!saved) failed() else Toast.makeText(context, "已保存到所选位置", Toast.LENGTH_SHORT).show()
            dialog = ""
        }
    }
}

@Composable
internal fun ComponentDestinationDialog(repository: MinusOneRepository, excludedSubtreeId: String?, title: String,
    onDismiss: () -> Unit, onSelect: (String, String?) -> Unit) {
    val state by repository.state.collectAsState()
    val palette = LocalPanelPalette.current
    val excluded = state.pages.asSequence().mapNotNull { it.components.findComponent(excludedSubtreeId.orEmpty()) }.firstOrNull()
    val excludedIds = excluded?.let { listOf(it).flattenComponents().map { child -> child.id }.toSet() }.orEmpty()
    PanelAlertDialog(onDismissRequest = onDismiss, title = { Text(title, fontWeight = FontWeight.Bold) }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("选择目标页面或容器；内容将置于已有卡片下方。", fontSize = 12.sp, color = palette.textSecondary)
            state.pages.forEachIndexed { idx, page ->
                Surface(
                    onClick = { onSelect(page.id, null) },
                    enabled = !state.advancedConfig.layoutLocked,
                    shape = RoundedCornerShape(8.dp),
                    color = palette.accent.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "第 ${idx + 1} 页 · ${page.name.ifBlank { "页面 ${idx + 1}" }}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary
                        )
                        Text(page.layoutType.label, fontSize = 11.sp, color = palette.accent)
                    }
                }
                page.components.flattenComponents().filter { it.type.isContainerType() && it.id !in excludedIds }.forEach { container ->
                    val path = page.components.componentPath(container.id).orEmpty().map { id -> page.components.findComponent(id)?.title.orEmpty().ifBlank { "容器" } }
                    Surface(
                        onClick = { onSelect(page.id, container.id) },
                        enabled = !state.advancedConfig.layoutLocked,
                        shape = RoundedCornerShape(8.dp),
                        color = palette.cardBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder),
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp)
                    ) {
                        Text(
                            text = "↳ ${page.name} / ${path.joinToString(" / ")}",
                            fontSize = 12.sp,
                            color = palette.textPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
internal fun ContainerChildTile(action: QuickActionItem, isEditMode: Boolean, eyeCareActive: Boolean,
    modifier: Modifier, onClick: () -> Unit, onEdit: (() -> Unit)?) {
    val gridSpacing = LocalFolderGridSpacing.current
    AppContextMenuTarget(packageName = action.packageName, label = action.title, enabled = !isEditMode,
        onClick = onClick, modifier = modifier, onEdit = onEdit, editLabel = "编辑容器子项", legacyAction = action) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val measurer = rememberTextMeasurer()
            val widthPx = with(LocalDensity.current) { (maxWidth - 2.dp).coerceAtLeast(0.dp).toPx() }
            val labelFont = (11 downTo 9).firstOrNull { size ->
                measurer.measure(action.title, TextStyle(fontSize = size.sp), softWrap = false, maxLines = 1).size.width <= widthPx
            } ?: 9
            Column((if (gridSpacing != null) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .padding(vertical = gridSpacing?.paddingDp?.dp ?: 5.dp, horizontal = 1.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                RenderContainerActionIcon(action, eyeCareActive, Modifier.size(LocalFolderIconSizeDp.current?.dp ?: 38.dp))
                Spacer(Modifier.height(gridSpacing?.labelGapDp?.dp ?: 3.dp))
                Text(action.title, modifier = Modifier.semantics { contentDescription = action.title },
                    color = LocalCardColors.current.textPrimary, fontSize = labelFont.sp,
                    lineHeight = if (gridSpacing != null) 18.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}
