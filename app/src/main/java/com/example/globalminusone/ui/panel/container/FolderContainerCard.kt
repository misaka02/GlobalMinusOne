package com.example.globalminusone.ui.panel.container

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.globalminusone.data.*
import com.example.globalminusone.system.AppAndIntentLauncher
import com.example.globalminusone.system.SystemController
import com.example.globalminusone.ui.AppIcons as Icons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.example.globalminusone.data.policy.FolderPreviewMode
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.QuickActionItem
import com.example.globalminusone.data.policy.containerCompactTargetMetrics
import com.example.globalminusone.data.policy.containerIconSizeDp
import com.example.globalminusone.data.policy.containerRowGapDp
import com.example.globalminusone.data.operations.findComponent
import com.example.globalminusone.data.policy.panelBounds
import com.example.globalminusone.data.policy.resolveFolderPreviewMode
import com.example.globalminusone.ui.panel.editor.CardDestinationPicker
import com.example.globalminusone.ui.panel.editor.CardDestructiveAction
import com.example.globalminusone.ui.panel.editor.CardDestructiveConfirmationContent
import com.example.globalminusone.ui.panel.editor.CardDestructiveRequest
import com.example.globalminusone.ui.panel.cards.CardLayoutActions
import com.example.globalminusone.ui.panel.cards.CardLayoutSnapshot
import com.example.globalminusone.ui.panel.editor.CardPropertyEditors
import com.example.globalminusone.ui.panel.cards.LegacyContainerActions
import com.example.globalminusone.ui.panel.cards.LocalCardColors
import com.example.globalminusone.ui.panel.cards.LocalCloseContainerPopup
import com.example.globalminusone.ui.panel.LocalPanelActions
import com.example.globalminusone.ui.panel.LocalPanelPalette
import com.example.globalminusone.ui.panel.PanelModalBackHandler
import com.example.globalminusone.ui.panel.cards.UserIconCache
import com.example.globalminusone.ui.panel.cards.cardLayoutSnapshot
import com.example.globalminusone.ui.panel.editor.rememberCardDestinationPicker
import com.example.globalminusone.ui.panel.cards.rememberCardLayoutActions
import com.example.globalminusone.ui.panel.editor.rememberCardPropertyEditors
import com.example.globalminusone.ui.panel.cards.rememberLegacyContainerActions
import com.example.globalminusone.ui.panel.cards.rememberUserIconBitmap

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FolderContainerCard(
    item: ComponentItem,
    pageIndex: Int,
    state: GlobalMinusOneState,
    repository: MinusOneRepository?,
    system: SystemController?,
    editing: Boolean,
    onClosePanel: () -> Unit,
    onMain: () -> Unit,
    nestedContent: (@Composable () -> Unit)?
) {
    FolderContainerContent(item, pageIndex, state.cardLayoutSnapshot(), repository?.let { rememberCardLayoutActions(it) },
        repository?.let { rememberCardPropertyEditors(it, system) }, repository?.let { rememberCardDestinationPicker(it) },
        rememberLegacyContainerActions(repository, system), state.advancedConfig.eyeProtectionEnabled,
        editing, onClosePanel, onMain, nestedContent)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FolderContainerContent(
    item: ComponentItem, pageIndex: Int, state: CardLayoutSnapshot, layoutActions: CardLayoutActions?,
    propertyEditors: CardPropertyEditors?, destinationPicker: CardDestinationPicker?, legacyActions: LegacyContainerActions,
    eyeProtectionEnabled: Boolean, editing: Boolean, onClosePanel: () -> Unit, onMain: () -> Unit,
    nestedContent: (@Composable () -> Unit)?
) {
    var open by remember(item.id) { mutableStateOf(false) }
    var openRequested by remember(item.id) { mutableStateOf(false) }
    var editingInside by remember(item.id) { mutableStateOf(false) }
    var menuExpanded by remember(item.id) { mutableStateOf(false) }
    var properties by remember(item.id) { mutableStateOf(false) }
    var propertySection by remember(item.id) { mutableStateOf("") }
    var childPropertiesId by remember(item.id) { mutableStateOf<String?>(null) }
    var childPropertySection by remember(item.id) { mutableStateOf("") }
    var childTransferId by remember(item.id) { mutableStateOf<String?>(null) }
    var childDestructiveRequest by remember(item.id) { mutableStateOf<CardDestructiveRequest?>(null) }
    var anchorBounds by remember(item.id) { mutableStateOf(IntRect.Zero) }
    val anchorCoords = remember(item.id) { arrayOfNulls<LayoutCoordinates>(1) }
    var actionToEdit by remember { mutableStateOf<QuickActionItem?>(null) }

    fun openFolder() {
        anchorBounds = anchorCoords[0]?.takeIf { it.isAttached }?.boundsInWindow()?.let { b ->
            IntRect(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt())
        } ?: IntRect.Zero
        open = true
        openRequested = true
    }

    fun forceCloseFolder() {
        openRequested = false
        open = false
        editingInside = false
        menuExpanded = false
        properties = false
        propertySection = ""
        childPropertiesId = null
        childTransferId = null
        childDestructiveRequest = null
        actionToEdit = null
        val repo = layoutActions ?: return
        val currentState = repo.current
        val selectedId = currentState.selectedComponentId
        if (selectedId != null && currentState.pages.getOrNull(pageIndex)?.components?.findComponent(item.id)?.containerComponents?.findComponent(selectedId) != null) {
            repo.selectComponent(null)
        }
    }

    fun closeFolder() {
        menuExpanded = false
        if (state.animationDurationMs <= 0) {
            forceCloseFolder()
        } else {
            openRequested = false
        }
    }

    val folderReturn = LocalFolderReturnState.current
    val folderInteraction = LocalFolderItemInteraction.current
    SideEffect {
        folderInteraction?.tap = {
            if (!editing) openFolder()
        }
    }
    LaunchedEffect(folderReturn?.reopenFolderId, folderReturn?.triggerToken, item.id) {
        if (folderReturn?.consume(item.id) == true) {
            openFolder()
        }
    }

    val context = LocalContext.current
    val palette = LocalCardColors.current
    val panelPalette = LocalPanelPalette.current
    val add = LocalPanelActions.current.addToContainer
    val parentFolderEdit = LocalFolderEditAction.current
    val parentClosePopup = LocalCloseContainerPopup.current
    val customImageBitmap = rememberUserIconBitmap(context, item.iconUri)
    LaunchedEffect(context, item.id, item.containerComponents, item.containerActions) {
        val packages = linkedSetOf<String>()
        val uris = linkedSetOf<String>()
        collectComponentTreeIcons(listOf(item), packages, uris)
        if (packages.isNotEmpty()) AppAndIntentLauncher.pinPackages(packages)
        val revisions = layoutActions?.imageAccessRevisions.orEmpty()
        coroutineScope {
            if (uris.isNotEmpty()) launch { UserIconCache.prewarm(context, uris, revisions) }
            if (packages.isNotEmpty()) launch { AppAndIntentLauncher.prewarmPackages(context, packages) }
        }
    }
    val previewItems = remember(item.containerComponents, item.containerActions) {
        item.containerComponents.take(4).map {
            FolderPreviewEntry(
                title = it.title,
                packageName = AppAndIntentLauncher.resolveEffectivePackageName(it.packageName, it.intentUri),
                iconUri = it.iconUri,
                type = it.type
            )
        } + item.containerActions.take((4 - item.containerComponents.size).coerceAtLeast(0)).map {
            FolderPreviewEntry(
                title = it.title,
                packageName = AppAndIntentLauncher.resolveEffectivePackageName(it.packageName, it.intentUri),
                iconUri = "",
                type = null
            )
        }
    }
    val totalItemCount = item.containerComponents.size + item.containerActions.size
    val previewMode = com.example.globalminusone.data.policy.resolveFolderPreviewMode(totalItemCount)

    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { anchorCoords[0] = it }) {
        val gridSpacing = LocalFolderGridSpacing.current
        val hasGridLabel = !item.showTitle && item.title.isNotBlank()
        val gridLabelHeight = if (hasGridLabel) with(LocalDensity.current) { 20.sp.toDp() } else 0.dp
        val verticalPadding = (gridSpacing?.paddingDp ?: 0).coerceAtLeast(0).dp
        val labelGapInt = if (hasGridLabel) (gridSpacing?.labelGapDp ?: 0) else 0
        val labelGap = labelGapInt.dp
        val iconHeightBudget = if (gridSpacing != null) maxHeight - verticalPadding * 2 - gridLabelHeight - labelGap
            else maxHeight - if (item.showTitle) 0.dp else 20.dp
        val iconSize = minOf(LocalFolderIconSizeDp.current?.dp ?: 62.dp, maxWidth,
            iconHeightBudget.coerceAtLeast(1.dp))
        val cellSize = ((iconSize - 10.dp) / 2).coerceIn(1.dp, 22.dp)
        Column(
            Modifier.fillMaxSize().then(
                if (folderInteraction != null) Modifier else Modifier.combinedClickable(
                    onClick = { openFolder() },
                    onLongClick = {
                        if (!state.layoutLocked) {
                            if (parentFolderEdit != null) parentFolderEdit() else layoutActions?.setEditMode(true)
                            layoutActions?.selectComponent(item.id)
                        }
                    }
                )
            ).then(if (gridSpacing != null) Modifier.padding(vertical = verticalPadding) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (gridSpacing != null) Arrangement.Top else Arrangement.Center
        ) {
            if (customImageBitmap != null) {
                Image(customImageBitmap, null, Modifier.size(iconSize).clip(RoundedCornerShape(18.dp)))
            } else when (previewMode) {
                com.example.globalminusone.data.policy.FolderPreviewMode.EMPTY -> {
                    Box(
                        Modifier
                            .size(iconSize)
                            .clip(RoundedCornerShape(18.dp))
                            .background(palette.iconBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Folder,
                            contentDescription = item.title,
                            modifier = Modifier.size((iconSize * 0.52f).coerceIn(18.dp, 32.dp)),
                            tint = palette.textSecondary.copy(alpha = 0.65f)
                        )
                    }
                }
                com.example.globalminusone.data.policy.FolderPreviewMode.SINGLE -> {
                    val sole = previewItems.firstOrNull()
                    val singleSize = (iconSize - 6.dp).coerceAtLeast(20.dp)
                    Box(
                        Modifier
                            .size(iconSize)
                            .clip(RoundedCornerShape(18.dp))
                            .background(palette.iconBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        if (sole != null) {
                            FolderChildPreviewGlyph(sole, singleSize, palette.accent)
                        } else {
                            Icon(
                                Icons.Folder,
                                contentDescription = item.title,
                                modifier = Modifier.size((iconSize * 0.52f).coerceIn(18.dp, 32.dp)),
                                tint = palette.textSecondary.copy(alpha = 0.65f)
                            )
                        }
                    }
                }
                com.example.globalminusone.data.policy.FolderPreviewMode.GRID_2X2 -> {
                    Column(
                        Modifier.size(iconSize).clip(RoundedCornerShape(18.dp)).background(palette.iconBackground).padding(5.dp),
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        repeat(2) { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                repeat(2) { col ->
                                    val preview = previewItems.getOrNull(row * 2 + col)
                                    Box(Modifier.size(cellSize), contentAlignment = Alignment.Center) {
                                        if (preview != null) {
                                            FolderChildPreviewGlyph(preview, cellSize, palette.accent)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (gridSpacing != null) {
                if (hasGridLabel) {
                    Spacer(Modifier.height(labelGapInt.coerceAtLeast(0).dp))
                    Text(item.title,
                        modifier = Modifier.height(gridLabelHeight)
                            .then(if (labelGapInt < 0) Modifier.offset(y = labelGap) else Modifier),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium, lineHeight = 20.sp, color = palette.textPrimary)
                }
            } else if (!item.showTitle) {
                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, color = palette.textPrimary)
            }
        }

        if (open) {
            val latestEditingInside by rememberUpdatedState(editingInside)
            val latestMenuExpanded by rememberUpdatedState(menuExpanded)
            val innerBackOwner = remember(item.id) { Any() }
            DisposableEffect(innerBackOwner) {
                PanelModalBackHandler.register(innerBackOwner, PanelModalBackHandler.PRIORITY_FOLDER_INNER) {
                    when {
                        latestMenuExpanded -> {
                            menuExpanded = false
                            true
                        }
                        latestEditingInside -> {
                            editingInside = false
                            true
                        }
                        else -> false
                    }
                }
                onDispose { PanelModalBackHandler.unregister(innerBackOwner) }
            }

            ContainerAnchoredPopup(
                item = item,
                anchorBounds = anchorBounds,
                onDismiss = ::closeFolder,
                expanded = openRequested,
                forceFullColumns = editingInside,
                gridColumns = state.gridColumns,
                onDismissAnimationEnd = ::forceCloseFolder,
                overlay = {
                    actionToEdit?.let { action ->
                        if (layoutActions != null) {
                            propertyEditors?.LegacyAction(pageIndex, item.id, action) { actionToEdit = null }
                        }
                    }
                    // Keep an ID, not a copied child: layout changes and deletion must follow live state.
                    val propertyId = if (properties) item.id else childPropertiesId
                    val propertyTarget = propertyId?.let { state.pages.getOrNull(pageIndex)?.components?.findComponent(it) }
                    if (propertyTarget != null && layoutActions != null) {
                        FolderWindowLayer {
                            propertyEditors?.Component(propertyTarget, pageIndex,
                                initialSection = if (properties) propertySection else childPropertySection) {
                                properties = false
                                propertySection = ""
                                childPropertiesId = null
                                childPropertySection = ""
                            }
                        }
                    }
                    childDestructiveRequest?.let { request ->
                        if (layoutActions != null) {
                            FolderWindowLayer {
                                CardDestructiveConfirmationContent(request, layoutActions) { childDestructiveRequest = null }
                            }
                        }
                    }
                    childTransferId?.let { targetChildId ->
                        if (layoutActions != null) {
                            val currentPageId = state.pages.getOrNull(pageIndex)?.id.orEmpty()
                            FolderWindowLayer {
                                destinationPicker?.Show(
                                    excludedSubtreeId = targetChildId,
                                    title = "移动到",
                                    onDismiss = { childTransferId = null }
                                ) { targetPageId, targetContainerId ->
                                    val saved = layoutActions.transferComponent(currentPageId, targetChildId, targetPageId, targetContainerId, copy = false)
                                    if (!saved) android.widget.Toast.makeText(context, "布局已锁定或目标已失效", android.widget.Toast.LENGTH_SHORT).show()
                                    else android.widget.Toast.makeText(context, "已移动到所选位置", android.widget.Toast.LENGTH_SHORT).show()
                                    childTransferId = null
                                }
                            }
                        }
                    }
                },
                header = {
                val compactDensity = LocalDensity.current
                val compactPanelBounds = LocalContainerPresentation.current?.panelBounds
                val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.toFloat()
                val availableCompactWidthDp = compactPanelBounds?.let { bounds ->
                    with(compactDensity) { (bounds.right - bounds.left).toDp().value }
                } ?: screenWidthDp
                val selectedChild = if (editingInside && !state.layoutLocked) {
                    state.selectedComponentId?.let { selId -> item.containerComponents.firstOrNull { it.id == selId } }
                } else null
                val currentPageId = state.pages.getOrNull(pageIndex)?.id.orEmpty()
                FolderWindowToolbar(
                    item = item, editing = editingInside, locked = state.layoutLocked,
                    menuExpanded = menuExpanded, onMenu = { menuExpanded = it },
                    onEdit = { editingInside = !editingInside },
                    onOpenSection = { section -> propertySection = section; properties = true },
                    onAdd = if (!state.layoutLocked && add != null) ({
                        folderReturn?.markPending(item.id)
                        forceCloseFolder()
                        add(item.id)
                    }) else null,
                    onCompactGrid = if (layoutActions != null && item.containerLayout == ContainerLayoutType.GRID) ({
                        val compactMetrics = containerCompactTargetMetrics(
                            item = item,
                            availableWidthDp = availableCompactWidthDp,
                            editingInside = editingInside,
                            gridColumns = state.gridColumns,
                            configuredHeightUnitDp = state.heightUnitDp
                        )
                        layoutActions.compactContainerGridPlacements(
                            pageIndex = pageIndex,
                            containerId = item.id,
                            widthDp = compactMetrics.widthDp,
                            stepYDp = compactMetrics.stepYDp,
                            iconColumns = compactMetrics.columns
                        )
                    }) else null,
                    onInlineGridChange = if (layoutActions != null && item.containerLayout == ContainerLayoutType.GRID) { col, iconSz, gap, rowGap ->
                        layoutActions.updateComponent(pageIndex, item.id) { comp ->
                            comp.copy(
                                containerColumns = col ?: comp.containerColumns,
                                containerIconSizeDp = iconSz ?: comp.containerIconSizeDp,
                                containerItemSizeDp = if (iconSz != null || rowGap != null) 0 else comp.containerItemSizeDp,
                                containerItemGapDp = gap ?: comp.containerItemGapDp,
                                containerRowGapDp = rowGap ?: comp.containerRowGapDp
                            )
                        }
                    } else null,
                    selectedChild = selectedChild,
                    onOpenSelectedProperties = selectedChild?.let { sel ->
                        {
                            childPropertySection = ""
                            childPropertiesId = sel.id
                        }
                    },
                    onOpenSelectedGeometry = selectedChild?.let { sel ->
                        {
                            childPropertySection = "位置与尺寸"
                            childPropertiesId = sel.id
                        }
                    },
                    onMoveSelectedChild = if (layoutActions != null && selectedChild != null) ({ childTransferId = selectedChild.id }) else null,
                    onDeleteSelectedChild = if (layoutActions != null && selectedChild != null) ({
                        childDestructiveRequest = CardDestructiveRequest(
                            pageId = currentPageId,
                            componentId = selectedChild.id,
                            title = selectedChild.title.ifBlank { selectedChild.type.label },
                            action = CardDestructiveAction.DELETE
                        )
                    }) else null,
                    onContainerAnimChange = layoutActions?.let { actions -> { ms ->
                        actions.updateThemeConfig { it.copy(moduleAnimations = it.moduleAnimations.copy(containerPopupDurationMs = ms)) }
                    } },
                    onClose = ::closeFolder
                )
                }
            ) {
                val enterFolderEdit = remember { { editingInside = true } }
                val openChildProperties = remember { { child: ComponentItem -> childPropertiesId = child.id } }
                val folderClosePopup = remember(parentClosePopup) { { forceCloseFolder(); parentClosePopup() } }
                CompositionLocalProvider(
                    LocalFolderEditing provides editingInside,
                    LocalFolderIconSizeDp provides if (item.containerLayout == ContainerLayoutType.GRID) containerIconSizeDp(item) else null,
                    LocalFolderEditAction provides enterFolderEdit,
                    LocalFolderOpenProperties provides openChildProperties
                ) {
                    if ((layoutActions != null && item.containerActions.isNotEmpty()) || item.containerComponents.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(
                            if (item.containerLayout == ContainerLayoutType.GRID) containerRowGapDp(item).coerceAtLeast(0).dp else 8.dp
                        )) {
                            if (layoutActions != null && item.containerActions.isNotEmpty()) {
                                LegacyContainerGridContent(
                                    item, pageIndex, state.layoutLocked, eyeProtectionEnabled, layoutActions, legacyActions,
                                    editingInside, onClosePanel, onMain, onEdit = { actionToEdit = it }
                                )
                            }
                            if (item.containerComponents.isNotEmpty()) {
                                CompositionLocalProvider(LocalCloseContainerPopup provides folderClosePopup) {
                                    nestedContent?.invoke()
                                }
                            }
                        }
                    }
                }
                if (item.containerActions.isEmpty() && item.containerComponents.isEmpty()) {
                    Text("暂无项目", style = MaterialTheme.typography.bodySmall, color = panelPalette.textSecondary)
                }
            }
        }
    }
}

internal fun collectComponentTreeIcons(
    items: List<ComponentItem>,
    outPackages: MutableSet<String>,
    outUris: MutableSet<String>
) {
    items.forEach { comp ->
        val rawUri = comp.iconUri.trim()
        if (rawUri.isNotEmpty()) outUris.add(rawUri)
        val pkg = AppAndIntentLauncher.resolveEffectivePackageName(comp.packageName, comp.intentUri)
        if (pkg.isNotEmpty()) outPackages.add(pkg)
        comp.containerActions.forEach { action ->
            val actionPkg = AppAndIntentLauncher.resolveEffectivePackageName(action.packageName, action.intentUri)
            if (actionPkg.isNotEmpty()) outPackages.add(actionPkg)
        }
        if (comp.containerComponents.isNotEmpty()) {
            collectComponentTreeIcons(comp.containerComponents, outPackages, outUris)
        }
    }
}

private data class FolderPreviewEntry(
    val title: String,
    val packageName: String,
    val iconUri: String,
    val type: ComponentType?
)

@Composable
private fun FolderChildPreviewGlyph(entry: FolderPreviewEntry, size: androidx.compose.ui.unit.Dp, accentColor: Color) {
    if (entry.type == null || entry.packageName.isNotBlank() || entry.iconUri.isNotBlank()) {
        FolderPreviewIcon(entry.title, entry.packageName, entry.iconUri, size)
        return
    }
    val vector = when (entry.type) {
        ComponentType.BRIGHTNESS_BAR -> Icons.Rounded.BrightnessMedium
        ComponentType.VOLUME_BAR -> Icons.Rounded.VolumeUp
        ComponentType.SWITCH_WIFI -> Icons.Rounded.Wifi
        ComponentType.SWITCH_BLUETOOTH -> Icons.Rounded.Bluetooth
        ComponentType.SWITCH_TORCH -> Icons.Rounded.FlashlightOn
        ComponentType.SWITCH_MUTE, ComponentType.SWITCH_RINGER -> Icons.Rounded.VolumeMute
        ComponentType.SWITCH_VIBRATE -> Icons.Rounded.Vibration
        ComponentType.SWITCH_AIRPLANE -> Icons.Rounded.Flight
        ComponentType.SWITCH_LOCATION -> Icons.Rounded.LocationOn
        ComponentType.SWITCH_HOTSPOT -> Icons.Rounded.WifiTethering
        ComponentType.SWITCH_NFC -> Icons.Rounded.Nfc
        ComponentType.SWITCH_EYE_CARE -> Icons.Rounded.Visibility
        ComponentType.STAT_BATTERY_BAR, ComponentType.STAT_BATTERY_DETAIL -> Icons.Rounded.BatteryChargingFull
        ComponentType.STAT_RAM -> Icons.Rounded.Memory
        ComponentType.STAT_ROM -> Icons.Rounded.Storage
        ComponentType.NOTE_CARD -> Icons.Rounded.EditNote
        ComponentType.CLIPBOARD_CARD -> Icons.Rounded.ContentPaste
        ComponentType.FILE_BROWSER, ComponentType.FILE_SHORTCUT -> Icons.Rounded.Folder
        ComponentType.ACTION_LOCK -> Icons.Rounded.Lock
        ComponentType.ACTION_SCREENSHOT -> Icons.Rounded.Screenshot
        ComponentType.ACTION_REGION_SCREENSHOT -> Icons.Rounded.CropFree
        ComponentType.ACTION_SCREEN_ASSISTANT -> Icons.Rounded.Translate
        ComponentType.ACTION_BREENO_TOUCH -> Icons.Rounded.AutoAwesome
        ComponentType.ACTION_WECHAT_SCAN -> Icons.Rounded.QrCodeScanner
        ComponentType.APP_DRAWER_CARD -> Icons.Rounded.Apps
        ComponentType.APP_KEYBOARD_LAUNCHER -> Icons.Rounded.Keyboard
        ComponentType.RECENT_APPS_CARD -> Icons.Rounded.History
        ComponentType.APP_WIDGET -> Icons.Rounded.Widgets
        ComponentType.PHOTO_CUBE -> Icons.Rounded.PhotoLibrary
        ComponentType.HITOKOTO_CARD -> Icons.Rounded.FormatQuote
        ComponentType.TEXT_LABEL -> Icons.Rounded.TextFields
        ComponentType.FOLDER -> Icons.Rounded.Folder
        ComponentType.SUB_PANEL_ENTRY -> Icons.Rounded.Layers
        ComponentType.CONTAINER_BOX -> Icons.Rounded.GridView
        ComponentType.WEBSITE_LINK -> Icons.Rounded.Language
        ComponentType.SINGLE_IMAGE -> Icons.Rounded.Image
        ComponentType.SHELL_COMMAND -> Icons.Rounded.Code
        else -> null
    }
    if (vector != null) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape((size * 0.28f).coerceAtLeast(6.dp)))
                .background(accentColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = vector,
                contentDescription = entry.title,
                tint = accentColor,
                modifier = Modifier.size((size * 0.62f).coerceAtLeast(12.dp))
            )
        }
    } else {
        FolderPreviewIcon(entry.title, entry.packageName, entry.iconUri, size)
    }
}

@Composable
private fun FolderPreviewIcon(title: String, packageName: String, iconUri: String, size: Dp) {
    val context = LocalContext.current
    val palette = LocalCardColors.current
    val customImageBitmap = rememberUserIconBitmap(context, iconUri)
    val targetPx = with(LocalDensity.current) { size.roundToPx().coerceIn(32, 96) }
    val cacheGen by AppAndIntentLauncher.cacheGeneration.collectAsState()
    val cachedPreviewBitmap = remember(packageName, customImageBitmap, cacheGen) {
        if (customImageBitmap == null && packageName.isNotBlank()) {
            AppAndIntentLauncher.peekAppIconBitmap(packageName)
        } else null
    }
    var loadedPreviewBitmap by remember(packageName, customImageBitmap, cacheGen) {
        mutableStateOf(cachedPreviewBitmap)
    }
    val resolvedPreviewBitmap = cachedPreviewBitmap ?: loadedPreviewBitmap
    LaunchedEffect(context, packageName, customImageBitmap, targetPx, cacheGen) {
        if (customImageBitmap == null && packageName.isNotBlank() && resolvedPreviewBitmap == null &&
            !AppAndIntentLauncher.isKnownMissingPackage(packageName)
        ) {
            val appInfo = AppAndIntentLauncher.getAppInfoCached(context, packageName, notify = false)
            loadedPreviewBitmap = AppAndIntentLauncher.peekAppIconBitmap(packageName)
                ?: appInfo?.icon?.let { drawable ->
                    withContext(Dispatchers.Default) {
                        runCatching { drawable.toBitmap(targetPx, targetPx).asImageBitmap() }.getOrNull()
                    }
                }
        }
    }
    if (customImageBitmap != null) Image(customImageBitmap, null, Modifier.size(size).clip(RoundedCornerShape(6.dp)))
    else if (resolvedPreviewBitmap != null) Image(resolvedPreviewBitmap, null, Modifier.size(size))
    else Box(Modifier.size(size).background(palette.accent.copy(alpha = .15f), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
        Text(title.take(1), style = MaterialTheme.typography.labelSmall, color = palette.accent)
    }
}

/** A modal is a later child of the folder's full-window host, never another native
 * Popup attached to the icon. ComponentEditModal owns its scrim, input and back stack. */
@Composable
internal fun FolderWindowLayer(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) { content() }
}
