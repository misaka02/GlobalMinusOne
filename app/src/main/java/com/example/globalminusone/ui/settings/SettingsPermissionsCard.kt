package com.example.globalminusone.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.globalminusone.ui.AppIcons as Icons

/** 权限入口展示系统可读状态；设备管理等无可靠检测项不伪报授权。 */
internal enum class PermissionVisualKind {
    GRANTED,
    WARNING,
    MISSING,
    SYSTEM_PAGE
}

internal fun resolvePermissionVisualKind(title: String, granted: Boolean): PermissionVisualKind = when {
    title.contains("需重开") -> PermissionVisualKind.WARNING
    title.startsWith("设备管理") || title.startsWith("后台弹出") -> PermissionVisualKind.SYSTEM_PAGE
    granted -> PermissionVisualKind.GRANTED
    else -> PermissionVisualKind.MISSING
}

@Composable
internal fun PermissionsHeaderCard(
    canDrawOverlays: Boolean,
    isAccessibilityEnabled: Boolean,
    isAccessibilityEnabledInSettings: Boolean,
    isStorageGranted: Boolean,
    canWriteSettings: Boolean,
    isNotificationPolicyGranted: Boolean,
    isUsageAccessGranted: Boolean,
    isBatteryOptIgnored: Boolean,
    permissionsExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val a11yNeedRestart = !isAccessibilityEnabled && isAccessibilityEnabledInSettings
    val checkedItems = listOf(
        canDrawOverlays,
        isAccessibilityEnabled,
        isStorageGranted,
        canWriteSettings,
        isNotificationPolicyGranted,
        isUsageAccessGranted,
        isBatteryOptIgnored
    )
    val grantedCount = checkedItems.count { it }
    val headerBg = when {
        canDrawOverlays && isAccessibilityEnabled ->
            if (darkTheme) Color(0xFF152419) else Color(0xFFEAF7EE)
        canDrawOverlays && a11yNeedRestart ->
            if (darkTheme) Color(0xFF2A1E0E) else Color(0xFFFFF4E3)
        !canDrawOverlays || !isAccessibilityEnabled ->
            if (darkTheme) Color(0xFF281619) else Color(0xFFFDF0F0)
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val headerBorder = when {
        canDrawOverlays && isAccessibilityEnabled ->
            if (darkTheme) Color(0xFF2E7D32) else Color(0xFF81C784)
        canDrawOverlays && a11yNeedRestart ->
            if (darkTheme) Color(0xFFF57C00) else Color(0xFFFFB74D)
        else ->
            if (darkTheme) Color(0xFFB71C1C) else Color(0xFFE57373)
    }
    val overlayAccent = if (canDrawOverlays) {
        if (darkTheme) Color(0xFF81C784) else Color(0xFF2E7D32)
    } else {
        if (darkTheme) Color(0xFFEF9A9A) else Color(0xFFC62828)
    }
    val a11yAccent = when {
        isAccessibilityEnabled -> if (darkTheme) Color(0xFF81C784) else Color(0xFF2E7D32)
        a11yNeedRestart -> if (darkTheme) Color(0xFFFFB74D) else Color(0xFFE65100)
        else -> if (darkTheme) Color(0xFFEF9A9A) else Color(0xFFC62828)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(headerBg)
            .border(1.dp, headerBorder.copy(alpha = 0.72f), RoundedCornerShape(14.dp))
            .clickable(onClick = onToggleExpand)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "权限与运行检查",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "核心 $grantedCount/${checkedItems.size} · ${if (permissionsExpanded) "收起" else "展开详情"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (permissionsExpanded) Icons.KeyboardArrowDown else Icons.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDotChip(
                text = if (canDrawOverlays) "● 悬浮窗 已授权" else "● 悬浮窗 未授权",
                accentColor = overlayAccent,
                darkTheme = darkTheme
            )
            StatusDotChip(
                text = when {
                    isAccessibilityEnabled -> "● 辅助服务 已连接"
                    a11yNeedRestart -> "● 辅助服务 需重开"
                    else -> "● 辅助服务 未连接"
                },
                accentColor = a11yAccent,
                darkTheme = darkTheme
            )
        }
    }
}

@Composable
private fun StatusDotChip(text: String, accentColor: Color, darkTheme: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accentColor.copy(alpha = if (darkTheme) 0.22f else 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = accentColor
        )
    }
}

@Composable
internal fun PermissionsChecklistContent(
    canDrawOverlays: Boolean,
    isAccessibilityEnabled: Boolean,
    isAccessibilityEnabledInSettings: Boolean,
    isStorageGranted: Boolean,
    canWriteSettings: Boolean,
    isNotificationPolicyGranted: Boolean,
    isUsageAccessGranted: Boolean,
    isBatteryOptIgnored: Boolean,
    onCollapse: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PermissionCircleItem(
                title = if (!isAccessibilityEnabled && isAccessibilityEnabledInSettings) "辅助(需重开)»" else "辅助服务»",
                icon = IconAccessibility,
                granted = isAccessibilityEnabled,
                onClick = {
                    if (!isAccessibilityEnabled && isAccessibilityEnabledInSettings) {
                        Toast.makeText(context, "系统已开启但服务被后台断开，请关闭后再重新开启一次", Toast.LENGTH_LONG).show()
                    }
                    try {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    } catch (_: Exception) {
                        Toast.makeText(context, "无法打开辅助服务设置", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            PermissionCircleItem(
                title = "悬浮窗»",
                icon = IconOverlayWindow,
                granted = canDrawOverlays,
                onClick = {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                        )
                    } catch (_: Exception) {
                        Toast.makeText(context, "无法打开悬浮窗设置", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            PermissionCircleItem(
                title = "手机存储»",
                icon = IconFloppy,
                granted = isStorageGranted,
                onClick = {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                            )
                        } else {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            )
                        }
                    } catch (_: Exception) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        )
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            PermissionCircleItem(
                title = "修改设置»",
                icon = Icons.Settings,
                granted = canWriteSettings,
                onClick = {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                        )
                    } catch (_: Exception) {
                        Toast.makeText(context, "无法打开修改系统设置页", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            PermissionCircleItem(
                title = "勿扰»",
                icon = IconDoNotDisturb,
                granted = isNotificationPolicyGranted,
                onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    } catch (_: Exception) {
                        Toast.makeText(context, "无法打开勿扰权限设置", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            PermissionCircleItem(
                title = "使用数据»",
                icon = IconBarChart,
                granted = isUsageAccessGranted,
                onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    } catch (_: Exception) {
                        Toast.makeText(context, "无法打开使用数据访问设置", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            PermissionCircleItem(
                title = "设备管理»",
                icon = IconPhoneAndroid,
                granted = false,
                onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                    } catch (_: Exception) {
                        Toast.makeText(context, "无法打开设备管理设置", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            PermissionCircleItem(
                title = "后台弹出界面»",
                icon = IconProhibited,
                granted = canDrawOverlays,
                onClick = {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        )
                    } catch (_: Exception) {
                        Toast.makeText(context, "无法打开应用详情设置", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            PermissionCircleItem(
                title = "电池优化»",
                icon = Icons.BatteryStd,
                granted = isBatteryOptIgnored,
                onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (_: Exception) {
                        Toast.makeText(context, "无法打开电池优化设置", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        TextButton(
            onClick = onCollapse,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text("收起权限检查")
        }
    }
}

@Composable
private fun PermissionCircleItem(
    title: String,
    icon: ImageVector,
    granted: Boolean,
    onClick: () -> Unit
) {
    val visualKind = resolvePermissionVisualKind(title, granted)
    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val accentColor: Color
    val cardBgColor: Color
    val borderColor: Color
    val pillBgColor: Color
    val pillTextColor: Color
    val statusBadgeText: String
    val statusBadgeIcon: ImageVector
    val subtitleText: String

    when (visualKind) {
        PermissionVisualKind.GRANTED -> {
            accentColor = if (darkTheme) Color(0xFF81C784) else Color(0xFF2E7D32)
            cardBgColor = if (darkTheme) Color(0xFF152419) else Color(0xFFEEF8F1)
            borderColor = if (darkTheme) Color(0xFF2E7D32) else Color(0xFF81C784)
            pillBgColor = if (darkTheme) Color(0xFF1E3A24) else Color(0xFFC8E6C9)
            pillTextColor = if (darkTheme) Color(0xFFA5D6A7) else Color(0xFF1B5E20)
            statusBadgeText = "已授权"
            statusBadgeIcon = Icons.CheckCircle
            subtitleText = "已授权 · 点击管理"
        }
        PermissionVisualKind.WARNING -> {
            accentColor = if (darkTheme) Color(0xFFFFB74D) else Color(0xFFE65100)
            cardBgColor = if (darkTheme) Color(0xFF2B1E0D) else Color(0xFFFFF4E3)
            borderColor = if (darkTheme) Color(0xFFF57C00) else Color(0xFFFFB74D)
            pillBgColor = if (darkTheme) Color(0xFF432C11) else Color(0xFFFFE0B2)
            pillTextColor = if (darkTheme) Color(0xFFFFCC80) else Color(0xFFBF360C)
            statusBadgeText = "需重开"
            statusBadgeIcon = Icons.Warning
            subtitleText = "系统已开启但连接中断 · 点击重开"
        }
        PermissionVisualKind.MISSING -> {
            accentColor = if (darkTheme) Color(0xFFEF9A9A) else Color(0xFFC62828)
            cardBgColor = if (darkTheme) Color(0xFF2A1518) else Color(0xFFFDF0F0)
            borderColor = if (darkTheme) Color(0xFFB71C1C) else Color(0xFFE57373)
            pillBgColor = if (darkTheme) Color(0xFF421C21) else Color(0xFFFFCDD2)
            pillTextColor = if (darkTheme) Color(0xFFFFCDD2) else Color(0xFFB71C1C)
            statusBadgeText = "未授权"
            statusBadgeIcon = Icons.Close
            subtitleText = "未授权 · 点击按需设置"
        }
        PermissionVisualKind.SYSTEM_PAGE -> {
            accentColor = MaterialTheme.colorScheme.onSurfaceVariant
            cardBgColor = MaterialTheme.colorScheme.surfaceContainerLow
            borderColor = MaterialTheme.colorScheme.outlineVariant
            pillBgColor = MaterialTheme.colorScheme.surfaceContainerHigh
            pillTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            statusBadgeText = "系统页"
            statusBadgeIcon = Icons.Info
            subtitleText = "需在系统设置中手动确认"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cardBgColor)
            .border(1.dp, borderColor.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = if (darkTheme) 0.22f else 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                title.removeSuffix("»"),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(pillBgColor)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                statusBadgeIcon,
                contentDescription = null,
                tint = pillTextColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = statusBadgeText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = pillTextColor
            )
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(6.dp))
}
