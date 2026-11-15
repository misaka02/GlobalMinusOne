package com.example.globalminusone.ui.panel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.globalminusone.ui.AppIcons as Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun QqAvatarIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // 黑色企鹅身体
        drawRoundRect(
            color = Color(0xFF212121),
            topLeft = Offset(w * 0.15f, h * 0.08f),
            size = Size(w * 0.70f, h * 0.82f),
            cornerRadius = CornerRadius(w * 0.35f, h * 0.40f)
        )
        // 白色肚子与面部
        drawOval(
            color = Color.White,
            topLeft = Offset(w * 0.25f, h * 0.20f),
            size = Size(w * 0.50f, h * 0.65f)
        )
        // 眼睛
        val eyeW = w * 0.08f
        val eyeH = h * 0.12f
        drawOval(
            color = Color(0xFF212121),
            topLeft = Offset(w * 0.38f - eyeW / 2, h * 0.28f),
            size = Size(eyeW, eyeH)
        )
        drawOval(
            color = Color(0xFF212121),
            topLeft = Offset(w * 0.62f - eyeW / 2, h * 0.28f),
            size = Size(eyeW, eyeH)
        )
        // 嘴巴 (橙色)
        drawRoundRect(
            color = Color(0xFFFF9800),
            topLeft = Offset(w * 0.40f, h * 0.39f),
            size = Size(w * 0.20f, h * 0.09f),
            cornerRadius = CornerRadius(w * 0.08f, h * 0.04f)
        )
        // 围巾 (经典大红)
        drawRoundRect(
            color = Color(0xFFE53935),
            topLeft = Offset(w * 0.18f, h * 0.48f),
            size = Size(w * 0.64f, h * 0.13f),
            cornerRadius = CornerRadius(w * 0.06f, h * 0.06f)
        )
        // 围巾左侧垂角
        drawRoundRect(
            color = Color(0xFFE53935),
            topLeft = Offset(w * 0.22f, h * 0.55f),
            size = Size(w * 0.14f, h * 0.24f),
            cornerRadius = CornerRadius(w * 0.05f, h * 0.05f)
        )
        // 双脚 (橙色)
        drawOval(
            color = Color(0xFFFF9800),
            topLeft = Offset(w * 0.24f, h * 0.84f),
            size = Size(w * 0.22f, h * 0.12f)
        )
        drawOval(
            color = Color(0xFFFF9800),
            topLeft = Offset(w * 0.54f, h * 0.84f),
            size = Size(w * 0.22f, h * 0.12f)
        )
    }
}

@Composable
fun WechatAvatarIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF07C160)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize(0.72f)) {
            val w = size.width
            val h = size.height
            // 左上较大气泡
            drawOval(
                color = Color.White,
                topLeft = Offset(0f, 0f),
                size = Size(w * 0.68f, h * 0.58f)
            )
            val p1 = Path().apply {
                moveTo(w * 0.15f, h * 0.52f)
                lineTo(w * 0.05f, h * 0.65f)
                lineTo(w * 0.30f, h * 0.56f)
                close()
            }
            drawPath(p1, Color.White)
            // 眼睛
            drawCircle(color = Color(0xFF07C160), radius = w * 0.045f, center = Offset(w * 0.22f, h * 0.26f))
            drawCircle(color = Color(0xFF07C160), radius = w * 0.045f, center = Offset(w * 0.44f, h * 0.26f))

            // 右下较小气泡
            drawOval(
                color = Color.White,
                topLeft = Offset(w * 0.38f, h * 0.36f),
                size = Size(w * 0.60f, h * 0.52f)
            )
            val p2 = Path().apply {
                moveTo(w * 0.85f, h * 0.82f)
                lineTo(w * 0.95f, h * 0.94f)
                lineTo(w * 0.72f, h * 0.86f)
                close()
            }
            drawPath(p2, Color.White)
            // 眼睛
            drawCircle(color = Color(0xFF07C160), radius = w * 0.04f, center = Offset(w * 0.58f, h * 0.58f))
            drawCircle(color = Color(0xFF07C160), radius = w * 0.04f, center = Offset(w * 0.78f, h * 0.58f))
        }
    }
}

@Composable
fun CoolapkAvatarIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFF11B95C)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize(0.65f)) {
            val strokeW = size.width * 0.15f
            val w = size.width
            val h = size.height
            // 左环
            drawCircle(
                color = Color.White,
                radius = w * 0.23f,
                center = Offset(w * 0.31f, h * 0.50f),
                style = Stroke(width = strokeW)
            )
            // 右环
            drawCircle(
                color = Color.White,
                radius = w * 0.23f,
                center = Offset(w * 0.69f, h * 0.50f),
                style = Stroke(width = strokeW)
            )
        }
    }
}

@Composable
fun GlobalMinusOneAvatarIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF2196F3)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize(0.55f)) {
            val w = size.width
            val h = size.height
            val pageW = w * 0.44f
            val pageH = h * 0.78f
            // 左书页
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.04f, h * 0.11f),
                size = Size(pageW, pageH),
                cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
            )
            // 右书页
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.52f, h * 0.11f),
                size = Size(pageW, pageH),
                cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
            )
        }
    }
}

@Composable
fun AlipayPayAvatarIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1677FF)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "支",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun WechatPayAvatarIcon(modifier: Modifier = Modifier) {
    WechatAvatarIcon(modifier = modifier)
}

@Composable
fun CropShortcutIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFF2196F3)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            val strokeW = 2.4.dp.toPx()
            val w = size.width
            val h = size.height
            // 裁剪折角与交叉线
            drawLine(Color.White, Offset(w * 0.12f, h * 0.40f), Offset(w * 0.12f, h * 0.12f), strokeW)
            drawLine(Color.White, Offset(w * 0.12f, h * 0.12f), Offset(w * 0.60f, h * 0.12f), strokeW)
            drawLine(Color.White, Offset(w * 0.88f, h * 0.60f), Offset(w * 0.88f, h * 0.88f), strokeW)
            drawLine(Color.White, Offset(w * 0.88f, h * 0.88f), Offset(w * 0.40f, h * 0.88f), strokeW)
            drawLine(Color.White, Offset(w * 0.12f, h * 0.68f), Offset(w * 0.68f, h * 0.68f), strokeW)
            drawLine(Color.White, Offset(w * 0.68f, h * 0.12f), Offset(w * 0.68f, h * 0.68f), strokeW)
        }
    }
}

@Composable
fun EyeShortcutIcon(active: Boolean, modifier: Modifier = Modifier) {
    val bgCol = if (active) Color(0xFFFFA000) else Color(0xFF9E9E9E)
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bgCol),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Visibility,
            contentDescription = "全局护眼",
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun PlayPauseShortcutIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFFFBC02D)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = "播放/暂停",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun VoiceShortcutIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFF43A047)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Mic,
            contentDescription = "语音助手",
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun WebLinkAvatarIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFF2196F3)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            val strokeW = 2.2.dp.toPx()
            val w = size.width
            val h = size.height
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.38f, h * 0.12f),
                size = Size(w * 0.48f, h * 0.34f),
                cornerRadius = CornerRadius(w * 0.17f, h * 0.17f),
                style = Stroke(width = strokeW)
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.14f, h * 0.54f),
                size = Size(w * 0.48f, h * 0.34f),
                cornerRadius = CornerRadius(w * 0.17f, h * 0.17f),
                style = Stroke(width = strokeW)
            )
            drawLine(
                color = Color.White,
                start = Offset(w * 0.36f, h * 0.64f),
                end = Offset(w * 0.64f, h * 0.36f),
                strokeWidth = strokeW
            )
        }
    }
}

// -----------------------------------------------------------------------------------------
// 统一组件外壳与自适应容器 (ComponentWrapper)
// -----------------------------------------------------------------------------------------
