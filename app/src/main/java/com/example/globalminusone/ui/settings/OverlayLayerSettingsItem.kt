package com.example.globalminusone.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.OverlayWindowLayer

@Composable
internal fun OverlayLayerSettingsItem(
    repository: MinusOneRepository,
    highestOverlayLevel: Boolean
) {
    val context = LocalContext.current
    val layerStatus by repository.overlayLayerStatus.collectAsState()
    val statusColor = when {
        !layerStatus.requestedAccessibilityLayer -> MaterialTheme.colorScheme.onSurfaceVariant
        !layerStatus.accessibilityConnected || !layerStatus.lastError.isNullOrBlank() ->
            MaterialTheme.colorScheme.error
        layerStatus.panelLayer == OverlayWindowLayer.ACCESSIBILITY ->
            MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        PreferenceSwitchItem(
            title = "无障碍悬浮层级",
            summary = "辅助服务运行时将面板与触发区置于无障碍悬浮层，解决电话本/设置等防遮挡界面无法呼出问题；失败自动回退普通层",
            checked = highestOverlayLevel,
            onCheckedChange = { enabled ->
                repository.updateAdvancedConfig { it.copy(highestOverlayLevel = enabled) }
            }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = layerStatus.summaryText(),
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
                modifier = Modifier.weight(1f)
            )
            if (highestOverlayLevel && !layerStatus.accessibilityConnected) {
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                ) {
                    Text("开启无障碍")
                }
            }
        }
    }
}
