package com.example.globalminusone

import com.example.globalminusone.data.operations.savedWidgetIds
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.globalminusone.ui.AppIcons as Icons
import com.example.globalminusone.ui.panel.cards.IndexedSelectionList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.globalminusone.data.BoundWidgetInfo
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.service.WidgetHostManager
import com.example.globalminusone.theme.GlobalMinusOneTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 小部件条目展示模型
 */
private data class WidgetItem(
    val providerInfo: AppWidgetProviderInfo,
    val widgetLabel: String,
    val appName: String,
    val packageName: String,
    val iconDrawable: Drawable?,
    val minWidthDp: Int,
    val minHeightDp: Int
)

class WidgetPickerActivity : ComponentActivity() {

    private lateinit var widgetHostManager: WidgetHostManager
    private lateinit var repository: MinusOneRepository

    private var pendingAppWidgetId: Int? = null
    private var pendingProviderInfo: AppWidgetProviderInfo? = null
    private var systemPickerAllocatedId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        widgetHostManager = WidgetHostManager.getInstance(this)
        repository = MinusOneRepository.getInstance(this)

        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey("pending_app_widget_id")) {
                pendingAppWidgetId = savedInstanceState.getInt("pending_app_widget_id")
            }
            if (savedInstanceState.containsKey("pending_system_picker_id")) {
                systemPickerAllocatedId = savedInstanceState.getInt("pending_system_picker_id")
            }
            pendingProviderInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                savedInstanceState.getParcelable("pending_provider_info", AppWidgetProviderInfo::class.java)
            } else {
                @Suppress("DEPRECATION")
                savedInstanceState.getParcelable("pending_provider_info")
            }
            if (pendingProviderInfo == null && pendingAppWidgetId != null) {
                pendingProviderInfo = widgetHostManager.appWidgetManager.getAppWidgetInfo(pendingAppWidgetId!!)
            }
        }

        setContent {
            GlobalMinusOneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WidgetPickerScreen(
                        onNavigateBack = { finish() },
                        onWidgetSelected = { info -> handleWidgetSelection(info) },
                        onUseSystemPicker = { launchSystemWidgetPicker() }
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingAppWidgetId?.let { outState.putInt("pending_app_widget_id", it) }
        pendingProviderInfo?.let { outState.putParcelable("pending_provider_info", it) }
        systemPickerAllocatedId?.let { outState.putInt("pending_system_picker_id", it) }
    }

    override fun onDestroy() {
        // Configuration changes retain ownership for the restored result callback.
        if (isFinishing) releasePendingWidgetIds()
        super.onDestroy()
    }

    private fun releasePendingWidgetIds() {
        val savedIds = repository.state.value.savedWidgetIds()
        WidgetBindingPolicy.idsToRelease(
            listOfNotNull(pendingAppWidgetId, systemPickerAllocatedId), savedIds
        ).forEach(widgetHostManager::deleteWidgetId)
        pendingAppWidgetId = null
        pendingProviderInfo = null
        systemPickerAllocatedId = null
    }

    // 绑定权限结果回调 (ACTION_APPWIDGET_BIND)
    private val bindWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingAppWidgetId
        val provider = pendingProviderInfo
        if (result.resultCode == RESULT_OK && id != null && provider != null) {
            checkConfigureAndSave(id, provider)
        } else {
            releasePendingWidgetIds()
            Toast.makeText(this, "小部件授权已取消", Toast.LENGTH_SHORT).show()
        }
    }

    // 小部件配置结果回调 (ACTION_APPWIDGET_CONFIGURE)
    private val configureWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingAppWidgetId
        val provider = pendingProviderInfo
        if (result.resultCode == RESULT_OK && id != null && provider != null) {
            saveWidgetAndFinish(id, provider)
        } else {
            releasePendingWidgetIds()
            Toast.makeText(this, "小部件配置已取消", Toast.LENGTH_SHORT).show()
        }
    }

    // 系统原生选择器回调
    private val systemPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val allocatedId = systemPickerAllocatedId
        if (result.resultCode == RESULT_OK) {
            val returnedId = result.data?.takeIf { it.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID) }
                ?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val appWidgetId = WidgetBindingPolicy.ownedIdForResult(allocatedId, returnedId)
            val info = appWidgetId?.let {
                try { widgetHostManager.appWidgetManager.getAppWidgetInfo(it) } catch (_: Exception) { null }
            }
            if (appWidgetId != null && info != null) {
                // Transfer ownership to the configure flow before launching another activity.
                systemPickerAllocatedId = null
                checkConfigureAndSave(appWidgetId, info)
            } else {
                releasePendingWidgetIds()
                Toast.makeText(this, "无法获取小部件信息", Toast.LENGTH_SHORT).show()
            }
        } else {
            releasePendingWidgetIds()
            Toast.makeText(this, "已取消选择小部件", Toast.LENGTH_SHORT).show()
        }
        systemPickerAllocatedId = null
    }

    private fun handleWidgetSelection(providerInfo: AppWidgetProviderInfo) {
        if (pendingAppWidgetId != null || systemPickerAllocatedId != null) return
        val appWidgetManager = widgetHostManager.appWidgetManager
        val appWidgetId = widgetHostManager.allocateWidgetId()

        pendingAppWidgetId = appWidgetId
        pendingProviderInfo = providerInfo

        val bindSuccess = try {
            appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, providerInfo.provider)
        } catch (e: Exception) {
            false
        }

        if (bindSuccess) {
            checkConfigureAndSave(appWidgetId, providerInfo)
        } else {
            val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
            }
            try {
                bindWidgetLauncher.launch(bindIntent)
            } catch (_: Exception) {
                releasePendingWidgetIds()
                Toast.makeText(this, "无法打开小部件授权页面", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkConfigureAndSave(appWidgetId: Int, providerInfo: AppWidgetProviderInfo) {
        pendingAppWidgetId = appWidgetId
        pendingProviderInfo = providerInfo
        if (providerInfo.configure != null) {
            val configureIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = providerInfo.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            try {
                configureWidgetLauncher.launch(configureIntent)
            } catch (e: Exception) {
                releasePendingWidgetIds()
                Toast.makeText(this, "无法打开小部件配置页面", Toast.LENGTH_SHORT).show()
            }
        } else {
            saveWidgetAndFinish(appWidgetId, providerInfo)
        }
    }

    private fun saveWidgetAndFinish(appWidgetId: Int, providerInfo: AppWidgetProviderInfo) {
        val label = try {
            providerInfo.loadLabel(packageManager).ifBlank { "桌面小部件" }
        } catch (_: Exception) {
            null
        } ?: "桌面小部件"

        val widgetInfo = BoundWidgetInfo(
            appWidgetId = appWidgetId,
            providerPackage = providerInfo.provider.packageName,
            providerClass = providerInfo.provider.className,
            label = label.ifBlank { "桌面小部件" },
            heightDp = 180
        )

        val gc = intent.getIntExtra("target_grid_col", -1); val gr = intent.getIntExtra("target_grid_row", -1)
        val saved = repository.addBoundWidget(widgetInfo, intent.getStringExtra("target_page_id"), intent.getStringExtra("container_id"),
            intent.getStringExtra("before_component_id"), if (gc >= 0 && gr >= 0) gc to gr else null)
        if (!saved) {
            releasePendingWidgetIds()
            Toast.makeText(this, "布局已锁定或目标容器已删除，未添加小部件", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        Toast.makeText(this, "已添加小部件", Toast.LENGTH_SHORT).show()
        pendingAppWidgetId = null; pendingProviderInfo = null; finish()
    }

    private fun launchSystemWidgetPicker() {
        if (pendingAppWidgetId != null || systemPickerAllocatedId != null) return
        val allocatedId = widgetHostManager.allocateWidgetId()
        systemPickerAllocatedId = allocatedId
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, allocatedId)
        }
        try {
            systemPickerLauncher.launch(pickIntent)
        } catch (e: Exception) {
            widgetHostManager.deleteWidgetId(allocatedId)
            systemPickerAllocatedId = null
            Toast.makeText(this, "系统不支持原生小部件选择器，请在列表直接选择", Toast.LENGTH_SHORT).show()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun WidgetPickerScreen(
        onNavigateBack: () -> Unit,
        onWidgetSelected: (AppWidgetProviderInfo) -> Unit,
        onUseSystemPicker: () -> Unit
    ) {
        var searchQuery by remember { mutableStateOf("") }
        var widgetItems by remember { mutableStateOf<List<WidgetItem>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            val items = withContext(Dispatchers.IO) {
                val appWidgetManager = AppWidgetManager.getInstance(this@WidgetPickerActivity)
                val installed = try {
                    appWidgetManager.installedProviders ?: emptyList()
                } catch (e: Exception) {
                    emptyList<AppWidgetProviderInfo>()
                }

                val items = installed.mapNotNull { provider ->
                    try {
                        val pkgName = provider.provider.packageName
                        val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            packageManager.getApplicationInfo(pkgName, PackageManager.ApplicationInfoFlags.of(0L))
                        } else {
                            @Suppress("DEPRECATION")
                            packageManager.getApplicationInfo(pkgName, 0)
                        }
                        val appName = packageManager.getApplicationLabel(appInfo).toString()
                        val widgetLabel = try {
                            provider.loadLabel(packageManager).ifBlank { appName }
                        } catch (_: Exception) {
                            appName
                        }

                        WidgetItem(
                            providerInfo = provider,
                            widgetLabel = widgetLabel,
                            appName = appName,
                            packageName = pkgName,
                            iconDrawable = null,
                            minWidthDp = provider.minWidth,
                            minHeightDp = provider.minHeight
                        )
                    } catch (_: Exception) {
                        null
                    }
                }.sortedWith(compareBy({ it.appName }, { it.widgetLabel }))

                items
            }
            widgetItems = items
            isLoading = false
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "选择小部件",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("搜索小部件...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索"
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "清空"
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onUseSystemPicker,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("系统原生选择器")
                    }
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "正在加载小部件...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (widgetItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "未检测到支持的小部件" else "未找到与「$searchQuery」匹配的小部件",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    IndexedSelectionList(
                        entries = widgetItems,
                        key = { "${it.packageName}/${it.providerInfo.provider.className}" },
                        label = { it.appName }, searchText = { "${it.appName} ${it.widgetLabel} ${it.packageName}" },
                        queryOverride = searchQuery,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)
                    ) { item ->
                            WidgetListItemCard(
                                item = item,
                                onClick = { onWidgetSelected(item.providerInfo) }
                            )
                    }
                }
            }
        }
    }

    @Composable
    private fun WidgetListItemCard(
        item: WidgetItem,
        onClick: () -> Unit
    ) {
        var iconDrawable by remember(item.packageName) { mutableStateOf<Drawable?>(null) }
        LaunchedEffect(item.packageName) {
            iconDrawable = com.example.globalminusone.system.AppAndIntentLauncher.getAppInfoCached(this@WidgetPickerActivity, item.packageName)?.icon
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onClick() },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    if (iconDrawable != null) {
                        AndroidView(
                            factory = { context ->
                                ImageView(context).apply {
                                    scaleType = ImageView.ScaleType.FIT_CENTER
                                }
                            },
                            update = { imageView ->
                                imageView.setImageDrawable(iconDrawable)
                            },
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.widgetLabel,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.appName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.packageName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${item.minWidthDp}×${item.minHeightDp}dp",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
