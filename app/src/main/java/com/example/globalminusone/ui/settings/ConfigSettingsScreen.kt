package com.example.globalminusone.ui.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.globalminusone.ui.AppIcons as Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.globalminusone.data.ConfigProfile
import com.example.globalminusone.data.GestureAction
import com.example.globalminusone.data.IconClickBehavior
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.PageTransition
import com.example.globalminusone.data.ScenarioModeItem
import com.example.globalminusone.data.ShowSideMode
import com.example.globalminusone.data.TriggerPosition
import com.example.globalminusone.data.TriggerWay
import com.example.globalminusone.service.GlobalAccessibilityService
import com.example.globalminusone.service.OverlayService

@Composable
internal fun ConfigSettingsScreen(
    repository: MinusOneRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by repository.state.collectAsState()
    val profiles = state.profiles
    val activeProfileId = state.activeProfileId
    val configScroll = rememberScrollState()

    var showImportDialog by remember { mutableStateOf(false) }
    var showNewProfileDialog by remember { mutableStateOf(false) }
    var selectedProfileForDetail by remember { mutableStateOf<ConfigProfile?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SubPageTopBar(title = "配置、备份与迁移", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(configScroll)
                .padding(top = 18.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                items(profiles) { profile ->
                    ConfigMockupCard(
                        profile = profile,
                        isActive = profile.id == activeProfileId,
                        onClick = { selectedProfileForDetail = profile }
                    )
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            NativeBackupEntry(repository)
            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            PanelsImportEntry(repository)
        }

        // 底部两个大圆角药丸按钮：导入 与 新建
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // This fixed footer is a sibling of the scroll viewport. Its blank padding
                // must scroll the same content rather than silently swallowing a swipe.
                .scrollable(configScroll, Orientation.Vertical, reverseDirection = true)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Button(
                onClick = { showImportDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(text = "配置码", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }

            Button(
                onClick = { showNewProfileDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(text = "新建", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    // 导入配置码弹窗
    if (showImportDialog) {
        ImportConfigCodeDialog(
            repository = repository,
            onDismiss = { showImportDialog = false }
        )
    }

    // 新建配置方案弹窗
    if (showNewProfileDialog) {
        NewConfigProfileDialog(
            repository = repository,
            onDismiss = { showNewProfileDialog = false }
        )
    }

    // 配置方案操作详情弹窗
    if (selectedProfileForDetail != null) {
        val p = selectedProfileForDetail!!
        ProfileDetailActionDialog(
            profile = p,
            isActive = p.id == activeProfileId,
            repository = repository,
            onDismiss = { selectedProfileForDetail = null }
        )
    }
}

@Composable
internal fun ConfigMockupCard(
    profile: ConfigProfile,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = "布局示意（非方案截图）",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Box(
            modifier = Modifier
                .width(226.dp)
                .height(376.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF22262E))
                .border(width = 1.dp, color = Color(0xFF383C44), shape = RoundedCornerShape(16.dp))
        ) {
            // 微缩负一屏卡片内容
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 顶部微缩状态小条
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF2E7D32))
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "布局示意",
                        color = Color.White,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 容器 1：应用
                MiniContainerCard(
                    title = "应用",
                    labels = listOf("QQ", "微信", "酷安", "负一屏"),
                    colors = listOf(0xFF1296DB, 0xFF07C160, 0xFF11A668, 0xFF2196F3)
                )

                // 容器 2：快捷方式
                MiniContainerCard(
                    title = "快捷方式",
                    labels = listOf("区域截屏", "全局护眼", "播放/暂停", "语音助手"),
                    colors = listOf(0xFF2196F3, 0xFF78909C, 0xFFFFA000, 0xFF43A047)
                )

                // 容器 3：支付
                MiniContainerCard(
                    title = "支付",
                    labels = listOf("扫一扫", "付款码", "扫一扫", "付款码"),
                    colors = listOf(0xFF1677FF, 0xFF00A6FF, 0xFF07C160, 0xFF09BB07)
                )

                // 容器 4：网站
                MiniContainerCard(
                    title = "网站",
                    labels = listOf("百度", "微博", "京东", "淘宝"),
                    colors = listOf(0xFF1E88E5, 0xFFE53935, 0xFFD32F2F, 0xFFFF6D00)
                )

                // 底部微缩操作栏加号
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0x33FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "+", color = Color.White, fontSize = 9.sp)
                }
            }

            // 右上角蓝色斜角标 "使用中"
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Box(
                        modifier = Modifier
                            .offset(x = 14.dp, y = (-2).dp)
                            .rotate(45f)
                            .background(Color(0xFF1E88E5))
                            .width(66.dp)
                            .padding(vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "使用中",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = profile.name,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
internal fun MiniContainerCard(
    title: String,
    labels: List<String>,
    colors: List<Long>
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 7.sp, color = Color(0xFF444444), fontWeight = FontWeight.Bold)
                Text(text = "⋮", fontSize = 7.sp, color = Color(0xFF888888))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                labels.zip(colors).forEach { (lbl, col) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(42.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(col)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = lbl.take(1),
                                color = Color.White,
                                fontSize = 6.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = lbl,
                            fontSize = 5.sp,
                            color = Color(0xFF666666),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ImportConfigCodeDialog(
    repository: MinusOneRepository,
    onDismiss: () -> Unit
) {
    LegacyConfigCodeImportDialog(repository, onDismiss)
}

@Composable
internal fun NewConfigProfileDialog(
    repository: MinusOneRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var profileName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            com.example.globalminusone.service.Android12WindowKeyGuard()
            Text("新建配置方案", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("将当前负一屏所有设置与卡片排版保存为一个全新方案：", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    placeholder = { Text("例如：极简工作风、游戏模式...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val name = profileName.trim().ifBlank { "配置方案" }
                    runCatching { repository.createConfigProfile(name = name) }
                        .onSuccess {
                            Toast.makeText(context, "已新建方案：$name", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }.onFailure {
                            Toast.makeText(context, repository.persistenceError.value ?: it.message ?: "方案未保存，请重试", Toast.LENGTH_LONG).show()
                            repository.acknowledgePersistenceError()
                        }
                }
            ) {
                Text("保存", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
internal fun ProfileDetailActionDialog(
    profile: ConfigProfile,
    isActive: Boolean,
    repository: MinusOneRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            com.example.globalminusone.service.Android12WindowKeyGuard()
            Text(text = profile.name, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("作者：${profile.author}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (profile.description.isNotBlank()) {
                    Text("说明：${profile.description}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("状态：${if (isActive) "当前正在使用" else "未启用"}", fontSize = 13.sp, color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (!isActive) {
                    TextButton(
                        onClick = {
                            if (!repository.applyConfigProfile(profile.id)) {
                                Toast.makeText(context, if (repository.state.value.advancedConfig.layoutLocked) "请先解锁布局，再切换方案" else "配置方案无效，无法切换", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            Toast.makeText(context, "已切换为：${profile.name}", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    ) {
                        Text("使用此配置", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(
                    onClick = {
                        val code = repository.exportConfigProfileCode(profile.id)
                        if (code == null) {
                            Toast.makeText(context, "配置方案无效，无法导出", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Config Code", code))
                        Toast.makeText(context, "已复制配置码到剪贴板！", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                ) {
                    Text("导出配置码", color = MaterialTheme.colorScheme.primary)
                }
                if (!isActive && profile.id != "default") {
                    TextButton(
                        onClick = {
                            repository.deleteConfigProfile(profile.id)
                            Toast.makeText(context, "已删除配置方案", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    ) {
                        Text("删除此方案", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
