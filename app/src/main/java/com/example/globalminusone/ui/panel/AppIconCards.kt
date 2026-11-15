package com.example.globalminusone.ui.panel

import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.globalminusone.ui.AppIcons as Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.globalminusone.data.AppAppearanceOverride
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.system.AppAndIntentLauncher
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal val LocalAppAppearanceOverrides = compositionLocalOf<Map<String, AppAppearanceOverride>?> { null }

@Composable
internal fun rememberAppAppearanceOverrides(repository: MinusOneRepository?): Map<String, AppAppearanceOverride> {
    LocalAppAppearanceOverrides.current?.let { return it }
    if (repository == null) return emptyMap()
    val overrides = remember(repository) {
        repository.state.map { it.appAppearanceOverrides }.distinctUntilChanged()
    }
    // Only seed the collection once; the mapped StateFlow supplies all live updates.
    val initial = remember(repository) { repository.state.value.appAppearanceOverrides }
    return overrides.collectAsState(initial = initial).value
}

@Composable
fun AppIconComponent(
    packageName: String,
    title: String,
    enabled: Boolean = !LocalIsEditMode.current,
    drawerAppearanceOverrides: Map<String, AppAppearanceOverride>? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val cardColors = LocalCardColors.current
    val folderIconSize = LocalFolderIconSizeDp.current?.dp ?: 36.dp
    val gridSpacing = LocalFolderGridSpacing.current
    val menuEnvironment = LocalAppMenuEnvironment.current
    val sourceCard = menuEnvironment?.item?.takeIf {
        it.type == ComponentType.APP_ICON && (it.packageName == packageName || packageName.isBlank())
    }
    val effectivePkg = remember(packageName, sourceCard?.intentUri) {
        AppAndIntentLauncher.resolveEffectivePackageName(packageName, sourceCard?.intentUri.orEmpty())
    }
    val cacheGen by AppAndIntentLauncher.cacheGeneration.collectAsState()
    val cachedAppInfo = remember(effectivePkg, cacheGen) {
        AppAndIntentLauncher.peekAppInfoCached(effectivePkg)
    }
    val cachedBitmap = remember(effectivePkg, cacheGen) {
        AppAndIntentLauncher.peekAppIconBitmap(effectivePkg)
    }
    var fetchedAppInfo by remember(effectivePkg) { mutableStateOf(cachedAppInfo) }
    var fetchedBitmap by remember(effectivePkg) { mutableStateOf(cachedBitmap) }
    val appInfo = cachedAppInfo ?: fetchedAppInfo
    val cachedAppBitmap = cachedBitmap ?: fetchedBitmap
    LaunchedEffect(context, effectivePkg) {
        if (effectivePkg.isNotBlank() && (appInfo == null || cachedAppBitmap == null) &&
            !AppAndIntentLauncher.isKnownMissingPackage(effectivePkg)
        ) {
            val loaded = AppAndIntentLauncher.getAppInfoCached(context, effectivePkg)
            fetchedAppInfo = loaded
            fetchedBitmap = AppAndIntentLauncher.peekAppIconBitmap(effectivePkg)
        }
    }
    val appearanceOverrides = when {
        drawerAppearanceOverrides != null -> drawerAppearanceOverrides
        sourceCard != null -> emptyMap()
        else -> rememberAppAppearanceOverrides(menuEnvironment?.repository)
    }
    val appearance = if (sourceCard == null) appearanceOverrides[effectivePkg] ?: appearanceOverrides[packageName] else null
    val displayLabel = appearance?.displayName?.takeIf { it.isNotBlank() }
        ?: title.ifBlank { appInfo?.label ?: effectivePkg.ifBlank { packageName } }
    val customImageBitmap = rememberUserIconBitmap(
        context,
        sourceCard?.iconUri?.takeIf { it.isNotBlank() } ?: appearance?.iconUri.orEmpty()
    )

    AppContextMenuTarget(effectivePkg.ifBlank { packageName }, displayLabel, enabled, onClick, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .padding(vertical = gridSpacing?.paddingDp?.dp ?: 4.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val fallbackBitmapDrawable = remember(appInfo?.icon, cachedAppBitmap) {
                if (cachedAppBitmap == null) appInfo?.icon as? BitmapDrawable else null
            }
            val fallbackImageBitmap = remember(fallbackBitmapDrawable) { fallbackBitmapDrawable?.bitmap?.asImageBitmap() }
            val resolvedAppBitmap = cachedAppBitmap ?: fallbackImageBitmap
            val nonBitmapDrawable = remember(appInfo?.icon, resolvedAppBitmap) {
                if (resolvedAppBitmap == null) {
                    appInfo?.icon?.let { cached -> cached.constantState?.newDrawable()?.mutate() ?: cached }
                } else null
            }
            Box(Modifier.size(folderIconSize)) {
            if (customImageBitmap != null) {
                Image(bitmap = customImageBitmap, contentDescription = null, modifier = Modifier.size(folderIconSize).clip(RoundedCornerShape(10.dp)))
            } else if (resolvedAppBitmap != null) {
                Image(bitmap = resolvedAppBitmap, contentDescription = null, modifier = Modifier.size(folderIconSize))
            } else if (nonBitmapDrawable != null) {
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setImageDrawable(nonBitmapDrawable)
                        }
                    },
                    update = { it.setImageDrawable(nonBitmapDrawable) },
                    modifier = Modifier.size(folderIconSize)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(folderIconSize)
                        .clip(CircleShape)
                        .background(cardColors.iconBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Apps,
                        contentDescription = displayLabel,
                        tint = cardColors.accent,
                        modifier = Modifier.size(folderIconSize * .56f)
                    )
                }
            }
                if (sourceCard?.appLaunchMode == com.example.globalminusone.data.AppLaunchMode.FLOATING) {
                    Surface(Modifier.align(Alignment.BottomEnd), shape = RoundedCornerShape(4.dp), color = cardColors.accent) {
                        Text("窗", Modifier.padding(horizontal = 2.dp), color = Color.White, fontSize = 8.sp, lineHeight = 10.sp)
                    }
                }
            }
            val labelGapDp = gridSpacing?.labelGapDp ?: 2
            Spacer(modifier = Modifier.height(labelGapDp.coerceAtLeast(0).dp))
            Text(
                text = displayLabel,
                modifier = if (labelGapDp < 0) Modifier.offset(y = labelGapDp.dp) else Modifier,
                color = cardColors.textPrimary,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CustomShortcutComponent(
    item: ComponentItem,
    enabled: Boolean = !LocalIsEditMode.current,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val cardColors = LocalCardColors.current
    val folderIconSize = LocalFolderIconSizeDp.current?.dp ?: 34.dp
    val gridSpacing = LocalFolderGridSpacing.current
    val iconColor = Color(item.colorHex)
    val iconVector = fallbackIcon ?: getQuickActionIcon(item.iconName)
    val customImageBitmap = rememberUserIconBitmap(context, item.iconUri)
    val effectivePkg = remember(item.packageName, item.intentUri, item.iconName) {
        if (item.iconName.isBlank() || item.iconName in setOf("bolt", "apps", "star")) {
            AppAndIntentLauncher.resolveEffectivePackageName(item.packageName, item.intentUri)
        } else ""
    }
    val cacheGen by AppAndIntentLauncher.cacheGeneration.collectAsState()
    val cachedShortcutBitmap = remember(effectivePkg, cacheGen) {
        if (effectivePkg.isNotBlank()) AppAndIntentLauncher.peekAppIconBitmap(effectivePkg) else null
    }
    var loadedShortcutBitmap by remember(effectivePkg) {
        mutableStateOf(cachedShortcutBitmap)
    }
    val shortcutAppBitmap = cachedShortcutBitmap ?: loadedShortcutBitmap
    LaunchedEffect(context, effectivePkg, customImageBitmap) {
        if (customImageBitmap == null && effectivePkg.isNotBlank() && shortcutAppBitmap == null &&
            !AppAndIntentLauncher.isKnownMissingPackage(effectivePkg)
        ) {
            AppAndIntentLauncher.getAppInfoCached(context, effectivePkg)
            loadedShortcutBitmap = AppAndIntentLauncher.peekAppIconBitmap(effectivePkg)
        }
    }

    AppContextMenuTarget(effectivePkg.ifBlank { item.packageName }, item.title, enabled, onClick, modifier = Modifier.fillMaxSize(), editLabel = "编辑快捷方式卡片") {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .padding(vertical = gridSpacing?.paddingDp?.dp ?: 4.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(folderIconSize)
                    .then(
                        if (customImageBitmap == null && shortcutAppBitmap == null) {
                            Modifier.clip(CircleShape).background(iconColor.copy(alpha = 0.22f))
                        } else {
                            Modifier.clip(RoundedCornerShape(10.dp))
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (customImageBitmap != null) {
                    Image(bitmap = customImageBitmap, contentDescription = null, modifier = Modifier.size(folderIconSize * .9f).clip(RoundedCornerShape(10.dp)))
                } else if (shortcutAppBitmap != null) {
                    Image(bitmap = shortcutAppBitmap!!, contentDescription = null, modifier = Modifier.size(folderIconSize).clip(RoundedCornerShape(10.dp)))
                } else Icon(
                    imageVector = iconVector,
                    contentDescription = item.title,
                    tint = iconColor,
                    modifier = Modifier.size(folderIconSize * .56f)
                )
            }
            val labelGapDp = gridSpacing?.labelGapDp ?: 2
            Spacer(modifier = Modifier.height(labelGapDp.coerceAtLeast(0).dp))
            Text(
                text = item.title,
                modifier = if (labelGapDp < 0) Modifier.offset(y = labelGapDp.dp) else Modifier,
                color = cardColors.textPrimary,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
                    color = cardColors.textSecondary,
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
