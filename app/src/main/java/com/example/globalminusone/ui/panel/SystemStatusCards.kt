package com.example.globalminusone.ui.panel

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.globalminusone.data.ComponentOrientation
import com.example.globalminusone.ui.AppIcons as Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.map
import com.example.globalminusone.data.MinusOneRepository

@Composable
private fun VerticalStatusBarCard(
    percent: Int,
    label: String,
    detail: String? = null,
    activeColor: Color,
    icon: ImageVector? = null
) {
    val cardColors = LocalCardColors.current
    val fraction = (percent / 100f).coerceIn(0f, 1f)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = "${percent.coerceIn(0, 100)}%",
            color = cardColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .widthIn(min = 12.dp, max = 26.dp)
                .fillMaxWidth(0.45f)
                .clip(RoundedCornerShape(10.dp))
                .background(cardColors.trackColor),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction)
                        .clip(RoundedCornerShape(10.dp))
                        .background(activeColor)
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (icon != null) {
                Icon(icon, null, tint = activeColor, modifier = Modifier.size(13.dp))
            }
            Text(
                text = label,
                color = cardColors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!detail.isNullOrBlank()) {
            Text(
                text = detail,
                color = cardColors.textSecondary,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StatBatteryBarComponent(orientation: ComponentOrientation = ComponentOrientation.AUTO) {
    val cardColors = LocalCardColors.current
    val status = currentHardwareStatus()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val percentage = "${status.batteryLevel}%"
    val caption = if (status.isCharging) "充电中" else "剩余电量"
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp)) {
        val isVertical = when (orientation) {
            ComponentOrientation.VERTICAL -> true
            ComponentOrientation.HORIZONTAL -> false
            ComponentOrientation.AUTO -> maxHeight > maxWidth * 1.15f
        }
        if (isVertical) {
            val activeColor = if (status.batteryLevel <= 20 && !status.isCharging) Color(0xFFEF4444) else cardColors.accent
            VerticalStatusBarCard(
                percent = status.batteryLevel,
                label = caption,
                activeColor = activeColor,
                icon = if (status.isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd
            )
            return@BoxWithConstraints
        }
        val iconSize = 16.dp
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val fonts = (16 downTo 10).map { main -> main to (main * 11f / 16f).coerceAtLeast(9f) }
        val fit = fonts.firstOrNull { (main, secondary) ->
            val mainSize = measurer.measure(percentage, TextStyle(fontSize = main.sp, lineHeight = (main * 1.2f).sp, fontWeight = FontWeight.Bold),
                softWrap = false, maxLines = 1).size
            val captionSize = measurer.measure(caption, TextStyle(fontSize = secondary.sp, lineHeight = (secondary * 1.25f).sp), softWrap = false, maxLines = 1).size
            maxOf(mainSize.width + with(density) { 19.dp.toPx() }, captionSize.width.toFloat()) <= widthPx &&
                maxOf(mainSize.height.toFloat(), with(density) { iconSize.toPx() }) +
                    with(density) { 2.dp.toPx() } + captionSize.height <= heightPx
        } ?: fonts.last()
        Column(Modifier.align(Alignment.Center).fillMaxWidth().heightIn(max = maxHeight)
            .ownPanelScrollGestures(!LocalIsEditMode.current)
            .verticalScroll(rememberScrollState(), enabled = !LocalIsEditMode.current),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(if (status.isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                    null, tint = if (status.batteryLevel <= 20 && !status.isCharging) Color(0xFFEF4444) else cardColors.accent,
                    modifier = Modifier.size(iconSize))
                Spacer(Modifier.width(3.dp))
                Text(percentage, color = cardColors.textPrimary, fontSize = fit.first.sp,
                    fontWeight = FontWeight.Bold, lineHeight = (fit.first * 1.2f).sp, maxLines = 1, softWrap = false)
            }
            Spacer(Modifier.height(2.dp))
            Text(caption, color = cardColors.textSecondary, fontSize = fit.second.sp, lineHeight = (fit.second * 1.25f).sp, maxLines = 1)
        }
    }
}

@Composable
fun StatBatteryDetailComponent() {
    val cardColors = LocalCardColors.current
    val status = currentHardwareStatus()

    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val scroll = rememberScrollState()
    val editing = LocalIsEditMode.current
    val metrics = listOf(
        "${status.batteryLevel}%" to if (status.isCharging) "充电中" else "剩余电量",
        "${status.batteryTempCelsius}°C" to "电池温度",
        "${status.batteryVoltageMv}mV" to "实时电压"
    )
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp)) {
        val columns = (maxWidth.value / (76f * density.fontScale.coerceAtLeast(1f))).toInt().coerceIn(1, 3)
        Column(Modifier.fillMaxSize().ownPanelScrollGestures(!editing,
            canScrollVertically = { dy -> if (dy < 0f) scroll.canScrollForward else scroll.canScrollBackward },
            axis = PanelGestureAxis.Vertical).verticalScroll(scroll, enabled = !editing),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            metrics.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (value, label) ->
                        BoxWithConstraints(Modifier.weight(1f)) {
                            val availableWidth = with(density) { maxWidth.toPx() }
                            val font = (18 downTo 10).firstOrNull { size ->
                                measurer.measure(value, TextStyle(fontSize = size.sp, fontWeight = FontWeight.Bold),
                                    softWrap = false, maxLines = 1).size.width <= availableWidth
                            } ?: 10
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(value, color = cardColors.textPrimary, fontSize = font.sp,
                                    lineHeight = (font * 1.25f).sp, fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(label, color = cardColors.textSecondary, fontSize = 10.sp,
                                    lineHeight = 13.sp, maxLines = 2, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
fun StatRamComponent(orientation: ComponentOrientation = ComponentOrientation.AUTO) {
    val cardColors = LocalCardColors.current
    val status = currentHardwareStatus()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isVertical = when (orientation) {
            ComponentOrientation.VERTICAL -> true
            ComponentOrientation.HORIZONTAL -> false
            ComponentOrientation.AUTO -> maxHeight > maxWidth * 1.15f
        }
        if (isVertical) {
            VerticalStatusBarCard(
                percent = status.ramPercent,
                label = "RAM",
                detail = "${status.ramUsedGb}G/${status.ramTotalGb}G",
                activeColor = if (status.ramPercent > 85) Color(0xFFEF4444) else cardColors.accent
            )
            return@BoxWithConstraints
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${status.ramPercent}%",
                color = cardColors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = "${status.ramUsedGb}G/${status.ramTotalGb}G",
                color = cardColors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { (status.ramPercent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = if (status.ramPercent > 85) Color(0xFFEF4444) else cardColors.accent,
            trackColor = cardColors.trackColor
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "RAM占用",
            color = cardColors.textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
    }
}

@Composable
fun StatRomComponent(orientation: ComponentOrientation = ComponentOrientation.AUTO) {
    val cardColors = LocalCardColors.current
    val status = currentHardwareStatus()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isVertical = when (orientation) {
            ComponentOrientation.VERTICAL -> true
            ComponentOrientation.HORIZONTAL -> false
            ComponentOrientation.AUTO -> maxHeight > maxWidth * 1.15f
        }
        if (isVertical) {
            VerticalStatusBarCard(
                percent = status.romPercent,
                label = "存储",
                detail = "${status.romUsedGb}G/${status.romTotalGb}G",
                activeColor = Color(0xFF10B981)
            )
            return@BoxWithConstraints
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${status.romPercent}%",
                color = cardColors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = "${status.romUsedGb}G/${status.romTotalGb}G",
                color = cardColors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { (status.romPercent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = Color(0xFF10B981),
            trackColor = cardColors.trackColor
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "存储占用",
            color = cardColors.textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
    }
}

@Composable
fun StatUptimeComponent() {
    val cardColors = LocalCardColors.current
    val context = LocalContext.current
    val expanded by remember(context) { MinusOneRepository.getInstance(context).isPanelExpanded }.collectAsState()
    // A fresh snapshot on every opening, without sampling while the panel is hidden.
    val uptimeMs = remember(expanded) { SystemClock.elapsedRealtime() }
    val hours = uptimeMs / 3600000
    val minutes = (uptimeMs % 3600000) / 60000

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(6.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${hours}小时${minutes}分",
            color = cardColors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "开机时间",
            color = cardColors.textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun StatScreenTimeComponent() {
    RealScreenTime()
}

@Composable
fun StatCountComponent(countText: String, labelText: String, icon: ImageVector) {
    val cardColors = LocalCardColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(6.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = cardColors.accent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = countText,
                color = cardColors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = labelText,
            color = cardColors.textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
