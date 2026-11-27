@file:Suppress("MatchingDeclarationName")

package com.example.globalminusone.ui.panel.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentOrientation
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.policy.LauncherCardPresentationMode
import com.example.globalminusone.data.policy.withCardPresentationMode
import com.example.globalminusone.ui.AppIcons as Icons
import com.example.globalminusone.ui.panel.PanelOverlayRadii

internal enum class SliderCardStyle {
    HORIZONTAL,
    VERTICAL,
    POPUP_ICON
}

private data class SliderStyleOption(val style: SliderCardStyle, val title: String, val desc: String)

internal fun buildSliderComponentItem(isVolume: Boolean, style: SliderCardStyle): ComponentItem {
    val type = if (isVolume) ComponentType.VOLUME_BAR else ComponentType.BRIGHTNESS_BAR
    val shortTitle = if (isVolume) "音量" else "亮度"
    return when (style) {
        SliderCardStyle.HORIZONTAL -> ComponentItem(
            type = type,
            title = "${shortTitle}条",
            showTitle = false,
            orientation = ComponentOrientation.HORIZONTAL,
            w = 32,
            h = 5
        )
        SliderCardStyle.VERTICAL -> ComponentItem(
            type = type,
            title = "竖排${shortTitle}条",
            showTitle = false,
            orientation = ComponentOrientation.VERTICAL,
            w = 6,
            h = 16,
            gridSpanX = 1,
            gridSpanY = 3
        )
        SliderCardStyle.POPUP_ICON -> withCardPresentationMode(
            ComponentItem(type = type, title = shortTitle),
            LauncherCardPresentationMode.POPUP_ICON
        )
    }
}

@Composable
internal fun AddComponentSliderStyleSubPage(
    isVolume: Boolean,
    accent: Color,
    primaryText: Color,
    secondaryText: Color,
    itemBg: Color,
    itemBorderColor: Color,
    onAdd: (ComponentItem, String) -> Boolean
) {
    val icon = if (isVolume) Icons.Rounded.VolumeUp else Icons.Rounded.BrightnessMedium
    val subject = if (isVolume) "音量" else "亮度"
    val options = listOf(
        SliderStyleOption(SliderCardStyle.HORIZONTAL, "横向${subject}条", "适合横幅放置，手指左右滑动即时调节"),
        SliderStyleOption(SliderCardStyle.VERTICAL, "竖向${subject}条", "适合侧栏或网格多行，上下滑动调节"),
        SliderStyleOption(SliderCardStyle.POPUP_ICON, "1×1 悬浮单图标", "仅占 1 格图标位，点击就地弹出竖向滑条")
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "选择${subject}组件形态（添加后也可在属性页随时切换）：",
            color = secondaryText,
            fontSize = 12.sp
        )
        options.forEach { (style, title, desc) ->
            Surface(
                onClick = { onAdd(buildSliderComponentItem(isVolume, style), "已添加$title") },
                shape = RoundedCornerShape(PanelOverlayRadii.Control),
                color = itemBg,
                border = BorderStroke(0.8.dp, itemBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(accent.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, color = primaryText, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(desc, color = secondaryText, fontSize = 11.sp)
                    }
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
