package com.example.globalminusone.ui.panel.keyboard

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.TriggerSide
import com.example.globalminusone.data.policy.KeyboardCardColorTokens
import com.example.globalminusone.system.AppAndIntentLauncher
import com.example.globalminusone.ui.panel.cards.rememberUserIconBitmap
import com.example.globalminusone.ui.panel.gesture.PanelGestureAxis
import com.example.globalminusone.ui.panel.gesture.ownPanelScrollGestures
import com.example.globalminusone.ui.panel.gesture.rememberPanelFlingBehavior

@Composable
internal fun rememberCandidateAppBitmap(
    context: Context,
    candidate: KeyboardCandidateEntry
): ImageBitmap? {
    val customBitmap = rememberUserIconBitmap(context, candidate.iconUri)
    val cacheGen by AppAndIntentLauncher.cacheGeneration.collectAsState()
    val cachedAppBitmap = remember(candidate.packageName, cacheGen) {
        AppAndIntentLauncher.peekAppIconBitmap(candidate.packageName)
    }
    var loadedInfo by remember(candidate.packageName) {
        mutableStateOf(AppAndIntentLauncher.peekAppInfoCached(candidate.packageName))
    }
    LaunchedEffect(candidate.packageName, customBitmap == null && cachedAppBitmap == null) {
        if (customBitmap == null && cachedAppBitmap == null &&
            !AppAndIntentLauncher.isKnownMissingPackage(candidate.packageName)
        ) {
            loadedInfo = AppAndIntentLauncher.getAppInfoCached(context, candidate.packageName)
        }
    }
    return customBitmap ?: cachedAppBitmap ?: remember(loadedInfo) {
        (loadedInfo?.icon as? BitmapDrawable)?.bitmap?.let { runCatching { it.asImageBitmap() }.getOrNull() }
    }
}

@Composable
internal fun CandidateAppIconBox(
    bitmap: ImageBitmap?,
    label: String,
    accentColor: Color,
    sizeDp: Dp,
    cornerDp: Dp,
    letterSp: TextUnit
) {
    val shape = RoundedCornerShape(cornerDp)
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = label,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(sizeDp).clip(shape)
        )
    } else {
        Box(
            modifier = Modifier.size(sizeDp).clip(shape).background(accentColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label.take(1).uppercase(),
                color = accentColor,
                fontSize = letterSp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun KeyboardCandidateStrip(
    context: Context,
    candidates: List<KeyboardCandidateEntry>,
    query: String,
    showAppLabels: Boolean,
    isLoadingApps: Boolean,
    kbdColors: KeyboardCardColorTokens,
    accentColor: Color,
    activeSide: TriggerSide,
    enabled: Boolean,
    onLaunchApp: (KeyboardCandidateEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    if (candidates.isEmpty()) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(kbdColors.emptyStripBg))
                .border(0.6.dp, Color(kbdColors.chipBorder), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isLoadingApps) "正在加载应用..." else "无匹配应用，可退格调整",
                color = Color(kbdColors.subText),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        return
    }

    LazyRow(
        modifier = modifier.ownPanelScrollGestures(enabled, axis = PanelGestureAxis.Horizontal),
        reverseLayout = activeSide == TriggerSide.RIGHT,
        flingBehavior = rememberPanelFlingBehavior(),
        contentPadding = PaddingValues(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(items = candidates, key = { it.packageName }) { candidate ->
            KeyboardCandidateChip(
                context = context,
                candidate = candidate,
                kbdColors = kbdColors,
                showLabel = showAppLabels,
                enabled = enabled,
                highlighted = query.isNotEmpty() && candidates.firstOrNull()?.packageName == candidate.packageName,
                accentColor = accentColor,
                onClick = { onLaunchApp(candidate) }
            )
        }
    }
}

@Composable
internal fun KeyboardCandidateChip(
    context: Context,
    candidate: KeyboardCandidateEntry,
    kbdColors: KeyboardCardColorTokens,
    showLabel: Boolean,
    enabled: Boolean,
    highlighted: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val displayBitmap = rememberCandidateAppBitmap(context, candidate)
    val bgColor = if (highlighted) accentColor.copy(alpha = 0.20f) else Color(kbdColors.chipBg)
    val borderColor = if (highlighted) accentColor.copy(alpha = 0.65f) else Color(kbdColors.chipBorder)

    Row(
        modifier = Modifier
            .fillMaxHeight()
            .then(if (showLabel) Modifier.widthIn(min = 64.dp, max = 128.dp) else Modifier.width(38.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(if (highlighted) 1.dp else 0.6.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (showLabel) 7.dp else 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (showLabel) Arrangement.spacedBy(5.dp) else Arrangement.Center
    ) {
        CandidateAppIconBox(
            bitmap = displayBitmap,
            label = candidate.label,
            accentColor = accentColor,
            sizeDp = 24.dp,
            cornerDp = 6.dp,
            letterSp = 11.sp
        )
        if (showLabel) {
            Text(
                text = candidate.label,
                color = Color(kbdColors.keyText),
                fontSize = 11.sp,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
