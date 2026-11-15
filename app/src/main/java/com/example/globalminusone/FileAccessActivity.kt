package com.example.globalminusone

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.globalminusone.data.FileBrowserRoot
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.PendingDocumentAccess
import com.example.globalminusone.data.awaitFilePickerPanelRelease
import com.example.globalminusone.service.OverlayService
import com.example.globalminusone.system.PanelFileBrowser
import com.example.globalminusone.theme.GlobalMinusOneTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Only hosts system permission UI. Directory browsing always stays in the overlay. */
class FileAccessActivity : ComponentActivity() {
    private lateinit var repository: MinusOneRepository
    private var launched = false
    private var completed = false
    private var pendingDocument: PendingDocumentAccess? = null
    private var documentProcessing = false

    private val treePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val uri = data?.data
        if (result.resultCode != RESULT_OK || uri == null) {
            finishAccess("已取消添加目录")
        } else {
            lifecycleScope.launch {
                try {
                    val root = withContext(Dispatchers.IO) {
                        check(uri.scheme == "content" && DocumentsContract.isTreeUri(uri)) { "系统返回的目录无效" }
                        check((data?.flags ?: 0) and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) { "未获得目录读取权限" }
                        check((data?.flags ?: 0) and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) { "系统未提供持续授权，请重新选择目录" }
                        val requestedWrite = intent.action == ACTION_ADD_WRITABLE_DIRECTORY
                        val returned = (data?.flags ?: 0) and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        val granted = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            (if (requestedWrite) returned and Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
                        contentResolver.takePersistableUriPermission(uri, granted)
                        val document = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
                        val columns = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
                        val title = contentResolver.query(document, columns, null, null, null)?.use { cursor ->
                            check(cursor.moveToFirst() && cursor.getString(1) == DocumentsContract.Document.MIME_TYPE_DIR) { "无法读取选择的目录" }
                            cursor.getString(0)?.takeIf { it.isNotBlank() } ?: "已授权目录"
                        } ?: error("无法读取选择的目录")
                        FileBrowserRoot(uri.toString(), title)
                    }
                    val replace = intent.getStringExtra(EXTRA_REPLACE_URI)
                    val accepted = repository.updateFileBrowserConfig { config ->
                        config.copy(roots = com.example.globalminusone.data.mergeGrantedFileRoot(config.roots, root, replace))
                    }
                    if (!accepted) {
                        finishAccess("目录配置未保存，请重试；原有目录配置已保留")
                        return@launch
                    }
                    val writable = contentResolver.persistedUriPermissions.any { it.uri.toString() == root.uri && it.isWritePermission }
                    finishAccess("已添加${if (writable) "可管理" else "只读"}目录：${root.title}")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    finishAccess("目录授权失败：${e.message ?: "请重新选择目录"}")
                }
            }
        }
    }

    private val documentPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val uri = data?.data
        if (result.resultCode != RESULT_OK || uri == null) {
            finishAccess("已取消选择文件")
        } else if (!completed) {
            pendingDocument = PendingDocumentAccess(uri.toString(), data?.flags ?: 0)
            processPendingDocument()
        }
    }

    private fun processPendingDocument() {
        val selection = pendingDocument ?: return
        if (completed || documentProcessing) return
        documentProcessing = true
        lifecycleScope.launch {
            try {
                val uri = Uri.parse(selection.uri)
                val title = withContext(Dispatchers.IO) {
                    check(uri.scheme == "content") { "系统返回的文件无效" }
                    check((selection.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) { "未获得文件读取权限" }
                    val returned = selection.flags
                    check(returned and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) { "提供方未提供可持续授权，请换一个文件提供方" }
                    val grantFlags = returned and (Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        if (intent.action == ACTION_PICK_FILE) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
                    contentResolver.takePersistableUriPermission(uri, grantFlags)
                    val name = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        check(it.moveToFirst()) { "无法读取所选文件" }
                        it.getString(0)
                    }?.takeIf { it.isNotBlank() } ?: "未命名文件"
                    contentResolver.openFileDescriptor(uri, "r")?.use { } ?: error("无法读取所选文件")
                    name
                }
                if (completed) return@launch
                val imageRequest = intent.action in setOf(ACTION_PICK_IMAGE, ACTION_PICK_COMPONENT_ICON, ACTION_PICK_APP_ICON)
                if (imageRequest) check(contentResolver.getType(uri)?.startsWith("image/") == true) { "请选择图片文件" }
                val saved = when (intent.action) {
                    ACTION_PICK_COMPONENT_ICON -> repository.saveComponentIcon(
                        intent.getStringExtra(EXTRA_PAGE_ID) ?: error("目标页面已失效"),
                        intent.getStringExtra(EXTRA_COMPONENT_ID) ?: error("目标卡片已失效"), uri.toString())
                    ACTION_PICK_APP_ICON -> {
                        val targetPackage = intent.getStringExtra(EXTRA_ICON_PACKAGE) ?: error("目标应用已失效")
                        try { packageManager.getApplicationInfo(targetPackage, 0) }
                        catch (_: android.content.pm.PackageManager.NameNotFoundException) { error("目标应用已卸载，未保存图标") }
                        repository.saveAppIcon(targetPackage, uri.toString())
                    }
                    ACTION_PICK_IMAGE -> repository.saveImageCard(
                        intent.getStringExtra(EXTRA_PAGE_ID) ?: error("目标页面已失效"), intent.getStringExtra(EXTRA_COMPONENT_ID), title, uri.toString(), intent.getStringExtra("container_id"), intent.getStringExtra("before_component_id"))
                    else -> repository.saveFileShortcut(
                        intent.getStringExtra(EXTRA_PAGE_ID) ?: error("目标页面已失效"), intent.getStringExtra(EXTRA_COMPONENT_ID), title, uri.toString(), intent.getStringExtra("container_id"), intent.getStringExtra("before_component_id"))
                }
                val writable = contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
                finishAccess(if (saved) if (intent.action in setOf(ACTION_PICK_COMPONENT_ICON, ACTION_PICK_APP_ICON)) "已更新图标" else "已保存文件：$title${if (intent.action == ACTION_PICK_FILE) if (writable) "（已授读写，实际写入由提供方决定）" else "（仅只读；提供方未授写入）" else ""}"
                    else "布局已锁定或目标页面/卡片已删除，未保存")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { finishAccess("文件选择失败：${e.message ?: "请重新选择"}") }
            finally { documentProcessing = false }
        }
    }

    private val allFilesSettings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        finishAccess(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            "已授权手机共享存储"
        } else "未授予所有文件访问权限，可使用「添加目录」选择需要的目录")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = MinusOneRepository.getInstance(this)
        launched = savedInstanceState?.getBoolean("launched") ?: false
        pendingDocument = savedInstanceState?.let { saved ->
            saved.getString("pending_document_uri")?.let { uri ->
                PendingDocumentAccess(uri, saved.getInt("pending_document_flags"))
            }
        }
        setContent {
            GlobalMinusOneTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("正在打开系统授权页面…")
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this) { finishAccess("已取消文件访问授权") }
        if (pendingDocument != null) {
            launched = true
            processPendingDocument()
        } else if (!launched) {
            lifecycleScope.launch {
                if (repository.isOverlayServiceRunning.value) {
                    val previousClose = repository.panelCloseGeneration.value
                    OverlayService.start(this@FileAccessActivity, OverlayService.ACTION_CLOSE_PANEL)
                    if (!awaitFilePickerPanelRelease(repository.isPanelExpanded, repository.panelCloseGeneration, previousClose)) {
                        finishAccess("面板尚未收起，已取消打开选择器，请重试")
                        return@launch
                    }
                }
                if (completed) return@launch
                launched = true
                when (intent.action) {
                    ACTION_PICK_FILE, ACTION_PICK_IMAGE, ACTION_PICK_COMPONENT_ICON, ACTION_PICK_APP_ICON -> launchDocumentPicker()
                    ACTION_ALL_FILES -> launchAllFilesSettings()
                    ACTION_ADD_DIRECTORY, ACTION_ADD_WRITABLE_DIRECTORY -> launchTreePicker()
                    else -> finishAccess("文件访问请求无效")
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("launched", launched)
        pendingDocument?.let {
            outState.putString("pending_document_uri", it.uri)
            outState.putInt("pending_document_flags", it.flags)
        }
        super.onSaveInstanceState(outState)
    }

    private fun launchDocumentPicker() {
        try {
            documentPicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = if (intent.action in setOf(ACTION_PICK_IMAGE, ACTION_PICK_COMPONENT_ICON, ACTION_PICK_APP_ICON)) "image/*" else "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    if (intent.action == ACTION_PICK_FILE) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
            })
        } catch (_: Exception) { finishAccess("此设备无法打开文件选择器") }
    }

    private fun launchTreePicker() {
        try {
            treePicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or
                    (if (intent.action == ACTION_ADD_WRITABLE_DIRECTORY) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0))
                intent.getStringExtra(EXTRA_REPLACE_URI)?.let {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(it))
                }
            })
        } catch (_: Exception) {
            finishAccess("此设备无法打开目录选择器")
        }
    }

    private fun launchAllFilesSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            finishAccess("此系统请使用「添加目录」授予所需目录的读取权限")
            return
        }
        if (Environment.isExternalStorageManager()) {
            finishAccess("已授权手机共享存储")
            return
        }
        try {
            allFilesSettings.launch(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            try {
                allFilesSettings.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (_: Exception) {
                finishAccess("此设备无法打开所有文件访问设置，请使用「添加目录」")
            }
        }
    }

    private fun finishAccess(message: String) {
        if (completed) return
        completed = true
        pendingDocument = null
        finish()
        val pickedFile = intent.action in setOf(ACTION_PICK_FILE, ACTION_PICK_IMAGE, ACTION_PICK_COMPONENT_ICON, ACTION_PICK_APP_ICON)
        if (pickedFile) android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
        PanelFileBrowser.completeAccess(this, message, returnToBrowser = !pickedFile)
    }

    companion object {
        const val ACTION_PICK_COMPONENT_ICON = "com.example.globalminusone.action.PICK_COMPONENT_ICON"
        const val ACTION_PICK_APP_ICON = "com.example.globalminusone.action.PICK_APP_ICON"
        const val EXTRA_ICON_PACKAGE = "icon_package"
        const val ACTION_PICK_IMAGE = "com.example.globalminusone.action.PICK_IMAGE_CARD"
        const val ACTION_PICK_FILE = "com.example.globalminusone.action.PICK_FILE_SHORTCUT"
        const val EXTRA_PAGE_ID = "file_shortcut_page_id"
        const val EXTRA_COMPONENT_ID = "file_shortcut_component_id"
        const val ACTION_ADD_WRITABLE_DIRECTORY = "com.example.globalminusone.action.ADD_WRITABLE_FILE_DIRECTORY"
        const val ACTION_ADD_DIRECTORY = "com.example.globalminusone.action.ADD_FILE_DIRECTORY"
        const val ACTION_ALL_FILES = "com.example.globalminusone.action.AUTHORIZE_SHARED_FILES"
        const val EXTRA_REPLACE_URI = "replace_directory_uri"
    }
}
