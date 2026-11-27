package com.example.globalminusone.ui.panel.cards

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
import com.example.globalminusone.system.file.PanelFileBrowser
import com.example.globalminusone.browser.EmbeddedAppTargets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.globalminusone.data.AppLaunchMode
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.policy.LauncherCardPresentationMode
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.policy.POPOUT_CAPABLE_CARD_TYPES
import com.example.globalminusone.data.QuickActionItem
import com.example.globalminusone.data.operations.componentPath
import com.example.globalminusone.data.operations.findComponent
import com.example.globalminusone.data.operations.flattenComponents
import com.example.globalminusone.data.policy.isSingleIconLauncherItem
import com.example.globalminusone.data.policy.withCardPresentationMode
import com.example.globalminusone.data.policy.withFloatingPodStyle
import com.example.globalminusone.ui.panel.EmbeddedApplicationTarget
import com.example.globalminusone.ui.panel.keyboard.FloatingPodStyleDialog
import com.example.globalminusone.ui.panel.container.LocalFolderEditAction
import com.example.globalminusone.ui.panel.container.LocalFolderGridSpacing
import com.example.globalminusone.ui.panel.container.LocalFolderIconSizeDp
import com.example.globalminusone.ui.panel.container.LocalFolderItemInteraction
import com.example.globalminusone.ui.panel.container.LocalFolderReturnState
import com.example.globalminusone.ui.panel.LocalPanelActions
import com.example.globalminusone.ui.panel.LocalPanelMenuPalette
import com.example.globalminusone.ui.panel.LocalPanelPalette
import com.example.globalminusone.ui.panel.PanelAlertDialog
import com.example.globalminusone.ui.panel.PanelDropdownMenu
import com.example.globalminusone.ui.panel.PanelMenuItem
import com.example.globalminusone.ui.AppIcons as Icons
import com.example.globalminusone.ui.panel.container.RenderContainerActionIcon
import com.example.globalminusone.ui.panel.editor.cardLongPressToEdit

internal val LocalAppEditAction = staticCompositionLocalOf<(() -> Unit)?> { null }
internal val LocalAppEditLabel = staticCompositionLocalOf { "编辑应用卡片" }
private data class AppMenuTargets(val launch: ComponentName? = null, val embedded: ComponentName? = null,
    val info: ApplicationInfo? = null)

internal fun resolveAppContextRestoreVisibility(
    card: ComponentItem?,
    legacyAction: QuickActionItem?,
    appearance: AppAppearanceOverride?
): Pair<Boolean, Boolean> {
    val showRestoreIcon = when {
        legacyAction != null -> legacyAction.iconName.isNotBlank() && legacyAction.iconName != "bolt"
        card != null -> card.iconUri.isNotBlank()
        else -> appearance?.iconUri?.isNotBlank() == true
    }
    val showRestoreName = card == null && legacyAction == null && appearance?.displayName?.isNotBlank() == true
    return showRestoreIcon to showRestoreName
}

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
    isPopOutLauncherIcon: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val folderInteraction = LocalFolderItemInteraction.current
    val folderEdit = LocalFolderEditAction.current
    val folderReturn = LocalFolderReturnState.current
    val effectiveEnabled = enabled || folderInteraction != null
    val env = LocalAppMenuEnvironment.current
    val actions = env?.actions
    val liveState = actions?.observeMenuState()
    val locked = liveState?.layoutLocked ?: LocalLayoutLocked.current
    val card = env?.item?.takeIf {
        legacyAction == null && (
            isAppContextMenuCardType(it.type) ||
                (isPopOutLauncherIcon && it.type in POPOUT_CAPABLE_CARD_TYPES)
            )
    }
    val editable = actions != null && !locked
    var expanded by remember(packageName, label) { mutableStateOf(false) }
    var heldMenu by remember(packageName, label) { mutableStateOf(false) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val latestClick by rememberUpdatedState(onClick)
    DisposableEffect(folderInteraction, packageName, label) {
        val menu: () -> Unit = {
            heldMenu = true; expanded = true
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        }
        val finish: () -> Unit = { heldMenu = false; expanded = true }
        val dismiss: () -> Unit = { heldMenu = false; expanded = false }
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
    val panelActions = LocalPanelActions.current
    val openEmbedded = panelActions.openEmbeddedApplication
    val closePanel = panelActions.closePanelForExternalApp
    val (showRestoreIcon, showRestoreName) = resolveAppContextRestoreVisibility(
        card = card,
        legacyAction = legacyAction,
        appearance = actions?.currentAppearanceOverrides?.get(packageName)
    )
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
    fun failed() { Toast.makeText(context, "更改未保存，请重试", Toast.LENGTH_SHORT).show() }
    fun mutateCard(transform: (ComponentItem) -> ComponentItem): Boolean =
        mutateAppMenuCard(actions, env?.pageId, card, transform)
    fun mutateLegacy(transform: (QuickActionItem) -> QuickActionItem): Boolean =
        mutateAppMenuLegacy(actions, env?.pageId, env?.item?.id.orEmpty(), legacyAction, transform)
    Box(modifier.semantics(mergeDescendants = true) {
        contentDescription = label
        if (effectiveEnabled) onLongClick(label = "打开应用或快捷方式菜单") { expanded = true; true }
    }.cardLongPressToEdit(packageName to label, effectiveEnabled && (folderInteraction == null || locked)) { expanded = true }
        .clickable(enabled = effectiveEnabled, onClick = onClick)) {
        content()
        PanelDropdownMenu(
            expanded = expanded && effectiveEnabled,
            properties = PopupProperties(focusable = !heldMenu),
            onDismissRequest = { expanded = false; heldMenu = false }
        ) {
            com.example.globalminusone.ui.Android12WindowKeyGuard()
            val menuPalette = checkNotNull(LocalPanelMenuPalette.current)
            val openCardOrApp: () -> Unit = {
                expanded = false
                if (card?.type == ComponentType.APP_ICON && card.appLaunchMode == AppLaunchMode.FLOATING) {
                    com.example.globalminusone.system.AppAndIntentLauncher.launchApp(context, packageName)
                    if (liveState?.autoCloseOnWidgetClick == true) closePanel?.invoke()
                } else onClick()
            }
            val openIconPicker: () -> Unit = {
                expanded = false
                when {
                    legacyAction != null -> dialog = "legacy-icon"
                    card != null -> { folderReturn?.clear(); PanelFileBrowser.requestComponentIcon(context, env!!.pageId, card.id) }
                    packageName.isNotBlank() -> { folderReturn?.clear(); PanelFileBrowser.requestAppIcon(context, packageName) }
                }
            }
            AppContextQuickActionHeader(
                label = label,
                isPopOutLauncherIcon = isPopOutLauncherIcon,
                showEditProp = onEdit != null,
                editPropEnabled = !locked,
                editDesc = editLabel,
                showIconAction = actions != null,
                showRemoveAction = actions != null && (card != null || legacyAction != null),
                editable = editable,
                onOpen = openCardOrApp,
                onEditProp = { expanded = false; onEdit?.invoke() },
                onChangeIcon = openIconPicker,
                onRemove = { expanded = false; dialog = "remove" }
            )
            if (embeddedComponent != null && openEmbedded != null) {
                PanelMenuItem(text = { Text("面板小窗") }, onClick = {
                    expanded = false
                    openEmbedded(EmbeddedApplicationTarget(embeddedComponent, label))
                })
            }
            if (actions != null) {
                PanelMenuItem(text = { Text("编辑布局") }, enabled = editable,
                    onClick = {
                        expanded = false
                        val current = actions.currentLayout
                        val target = current.pages.firstOrNull { it.id == env?.pageId }?.components?.findComponent(env?.item?.id.orEmpty())
                        if (target != null && !current.layoutLocked) {
                            if (folderEdit != null) folderEdit() else actions.setEditMode(true)
                            actions.selectComponent(target.id)
                        } else failed()
                    })
                if (card != null && card.type in POPOUT_CAPABLE_CARD_TYPES) {
                    PanelMenuItem(
                        text = { Text("弹窗尺寸与样式…") },
                        enabled = editable,
                        onClick = {
                            expanded = false
                            dialog = "pod-style"
                        }
                    )
                    val nextMode = if (isSingleIconLauncherItem(card)) {
                        LauncherCardPresentationMode.INLINE_CARD
                    } else {
                        LauncherCardPresentationMode.POPUP_ICON
                    }
                    PanelMenuItem(
                        text = { Text("切换为${nextMode.label}") },
                        enabled = editable,
                        onClick = {
                            if (mutateCard { withCardPresentationMode(it, nextMode) }) expanded = false else failed()
                        }
                    )
                }
                PanelMenuItem(text = { Text("重命名") }, enabled = editable,
                    onClick = { expanded = false; rename = label; dialog = "rename" })
                if (showRestoreIcon) {
                    PanelMenuItem(text = { Text("恢复默认图标") }, enabled = editable, onClick = {
                        val saved = when {
                            legacyAction != null -> mutateLegacy { it.copy(iconName = "bolt") }
                            card != null -> mutateCard { it.copy(iconUri = "") }
                            else -> actions.updateAppAppearance(packageName, iconUri = "")
                        }
                        if (saved) expanded = false else failed()
                    })
                }
                if (showRestoreName) {
                    PanelMenuItem(text = { Text("恢复应用原名") }, enabled = editable, onClick = {
                        if (actions.updateAppAppearance(packageName, displayName = "")) expanded = false else failed()
                    })
                }
                HorizontalDivider()
                PanelMenuItem(text = { Text(if (card != null) "复制到页面 / 容器" else "添加到页面 / 容器") },
                    enabled = editable, onClick = { expanded = false; dialog = "copy" })
                if (card != null) PanelMenuItem(text = { Text("移动到页面 / 容器") }, enabled = editable,
                    onClick = { expanded = false; dialog = "move" })
            }
            if (packageName.isNotBlank()) {
                HorizontalDivider()
                PanelMenuItem(text = { Text("应用详情") }, onClick = {
                    expanded = false
                    try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    catch (_: Exception) { Toast.makeText(context, "无法打开应用详情", Toast.LENGTH_SHORT).show() }
                })
            }
            if (applicationInfo != null && packageName != context.packageName &&
                applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0)
                PanelMenuItem(text = { Text("卸载应用", color = menuPalette.danger) }, onClick = {
                    expanded = false; dialog = "uninstall"
                })
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
            else -> actions?.updateAppAppearance(packageName, displayName = rename.trim()) == true
        }
        if (saved) dialog = "" else failed()
    }) { Text("保存") } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("取消") } })
    if (dialog == "legacy-icon") PanelAlertDialog(onDismissRequest = { dialog = "" }, title = { Text("选择内置图标") }, text = {
        val options = listOf(
            Triple("bolt", "闪电", Icons.Bolt),
            Triple("dashboard", "仪表盘", Icons.Dashboard),
            Triple("settings", "设置", Icons.Settings),
            Triple("explore", "浏览", Icons.Explore),
            Triple("storage", "存储", Icons.Storage)
        )
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (iconKey, name, vector) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clickable { if (mutateLegacy { it.copy(iconName = "custom:$iconKey") }) dialog = "" else failed() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(vector, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(name, fontSize = 14.sp)
                }
            }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("取消") } })
    if (dialog == "remove") PanelAlertDialog(onDismissRequest = { dialog = "" }, title = { Text("移除卡片？") },
        text = { Text("从当前位置移除“$label”，不会卸载应用。") }, confirmButton = { TextButton(onClick = {
            val state = actions?.currentLayout
            val index = state?.pages?.indexOfFirst { it.id == env?.pageId } ?: -1
            if (state == null || state.layoutLocked || index < 0) failed()
            else if (legacyAction != null) {
                val parent = state.pages[index].components.findComponent(env!!.item.id)
                if (parent == null || parent.containerActions.none { it.id == legacyAction.id }) failed()
                else if (actions?.removeActionFromContainer(index, parent.id, legacyAction.id) == true) dialog = "" else failed()
            } else if (card != null) {
                if (actions?.removeComponent(index, card.id) == true) dialog = "" else failed()
            } else dialog = ""
        }) { Text("移除", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("取消") } })
    if ((dialog == "copy" || dialog == "move") && actions != null && env != null) {
        val moving = dialog == "move"
        env.destinationPicker.Show(card?.id, if (moving) "移动到" else "添加 / 复制到", { dialog = "" }) { pageId, containerId ->
            val saved = if (card != null) actions.transferComponent(env.pageId, card.id, pageId, containerId, copy = !moving)
            else {
                val appearance = actions.currentAppearanceOverrides[packageName]
                val created = if (legacyAction != null) ComponentItem(type = ComponentType.CUSTOM_SHORTCUT,
                    title = legacyAction.title, packageName = legacyAction.packageName, intentUri = legacyAction.intentUri, iconName = legacyAction.iconName)
                else ComponentItem(type = ComponentType.APP_ICON, title = appearance?.displayName?.takeIf { it.isNotBlank() } ?: label,
                    packageName = packageName, iconUri = appearance?.iconUri.orEmpty())
                actions.placeMenuComponent(pageId, containerId, created)
            }
            if (saved) {
                Toast.makeText(context, "已保存到所选位置", Toast.LENGTH_SHORT).show()
                dialog = ""
            } else failed()
        }
    }
    if (dialog == "pod-style" && card != null) {
        FloatingPodStyleDialog(
            item = card,
            onDismiss = { dialog = "" },
            onSave = { updatedStyle ->
                val saved = mutateCard { withFloatingPodStyle(it, updatedStyle) }
                if (saved) dialog = "" else failed()
                saved
            }
        )
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
    }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
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
