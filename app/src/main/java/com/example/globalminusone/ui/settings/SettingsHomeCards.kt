package com.example.globalminusone.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.ui.AppIcons as Icons

private data class ServiceHeroSpec(
    val containerColor: Color,
    val borderColor: Color,
    val contentColor: Color,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val actionLabel: String
)

@Composable
private fun resolveServiceHeroSpec(
    isServiceRunning: Boolean,
    serviceEnabled: Boolean,
    canDrawOverlays: Boolean,
    serviceContentColor: Color
): ServiceHeroSpec = when {
    isServiceRunning -> ServiceHeroSpec(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f),
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
        contentColor = serviceContentColor,
        title = "全局负一屏已开启",
        subtitle = "边缘滑动或悬浮球即可随时呼出",
        icon = Icons.CheckCircle,
        actionLabel = "呼出预览面板"
    )
    serviceEnabled -> ServiceHeroSpec(
        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
        borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
        contentColor = serviceContentColor,
        title = if (canDrawOverlays) "服务未就绪" else "缺少悬浮窗权限",
        subtitle = if (canDrawOverlays) "开关已开启，可点击下方按钮重试" else "请授予悬浮窗权限后重新开启",
        icon = Icons.Warning,
        actionLabel = if (canDrawOverlays) "重试启动服务" else "授予悬浮窗权限"
    )
    else -> ServiceHeroSpec(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        title = "全局负一屏已关闭",
        subtitle = "打开右侧开关即可启用侧滑面板",
        icon = Icons.PowerSettingsNew,
        actionLabel = if (canDrawOverlays) "启动并预览面板" else "授予悬浮窗权限"
    )
}

@Composable
internal fun ServiceStatusHeroCard(
    isServiceRunning: Boolean,
    serviceEnabled: Boolean,
    canDrawOverlays: Boolean,
    serviceContentColor: Color,
    onToggleService: (Boolean) -> Unit,
    onPrimaryAction: () -> Unit
) {
    val spec = resolveServiceHeroSpec(isServiceRunning, serviceEnabled, canDrawOverlays, serviceContentColor)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = spec.containerColor,
        border = BorderStroke(0.8.dp, spec.borderColor),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ServiceHeroHeaderRow(spec, serviceEnabled, onToggleService)
            HorizontalDivider(color = spec.contentColor.copy(alpha = 0.12f), thickness = 0.6.dp)
            ServiceHeroActionRow(spec, isServiceRunning, serviceEnabled, onPrimaryAction) {
                onToggleService(false)
            }
        }
    }
}

@Composable
private fun ServiceHeroHeaderRow(
    spec: ServiceHeroSpec,
    serviceEnabled: Boolean,
    onToggleService: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(spec.contentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(spec.icon, contentDescription = null, tint = spec.contentColor, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(spec.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = spec.contentColor)
            Spacer(modifier = Modifier.height(2.dp))
            Text(spec.subtitle, fontSize = 12.5.sp, color = spec.contentColor.copy(alpha = 0.82f))
        }
        Switch(
            checked = serviceEnabled,
            onCheckedChange = onToggleService,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun ServiceHeroActionRow(
    spec: ServiceHeroSpec,
    isServiceRunning: Boolean,
    serviceEnabled: Boolean,
    onPrimaryAction: () -> Unit,
    onDisableService: () -> Unit
) {
    val container = if (isServiceRunning) MaterialTheme.colorScheme.primary else spec.contentColor.copy(alpha = 0.14f)
    val content = if (isServiceRunning) MaterialTheme.colorScheme.onPrimary else spec.contentColor
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalButton(
            onClick = onPrimaryAction,
            modifier = Modifier.weight(1f).heightIn(min = 40.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = container, contentColor = content)
        ) {
            Icon(
                imageVector = if (isServiceRunning) Icons.OpenInNew else Icons.PowerSettingsNew,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(spec.actionLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        if (serviceEnabled && !isServiceRunning) {
            TextButton(onClick = onDisableService) {
                Text("关闭主开关", color = spec.contentColor, fontSize = 12.5.sp)
            }
        }
    }
}

@Composable
internal fun MainMenuGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
internal fun MainMenuGroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 64.dp, end = 16.dp),
        thickness = 0.6.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    )
}

@Composable
internal fun MainMenuItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(1.dp))
            Text(subtitle, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.KeyboardArrowRight,
            contentDescription = "进入",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}
