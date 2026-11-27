package com.example.globalminusone

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.service.OverlayService
import com.example.globalminusone.system.file.PanelFileBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first

/** Exact destination IDs survive system-picker recreation; never falls back to another page. */
class PhotoCubePickerActivity : ComponentActivity() {
    private data class Destination(val page: String, val component: String?, val container: String?, val before: String?, val gridCell: Pair<Int, Int>?)
    private lateinit var destination: Destination
    private var processing = false
    private var completed = false
    private var launched = false
    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (processing || completed) return@registerForActivityResult
        if (uris.isEmpty()) {
            finishPicker()
            return@registerForActivityResult
        }
        processing = true
        val target = destination
        lifecycleScope.launch {
            val acquired = mutableSetOf<Uri>()
            var retained = emptySet<Uri>()
            try {
                val accepted = withContext(Dispatchers.IO) {
                    val existing = contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri }.toSet()
                    uris.distinct().take(100).filter { uri ->
                        ensureActive()
                        runCatching {
                            if (uri !in existing) {
                                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                acquired.add(uri)
                            }
                            contentResolver.openInputStream(uri)?.use { it.read() } ?: error("Unreadable")
                            true
                        }.getOrDefault(false)
                    }
                }
                ensureActive()
                val saved = accepted.isNotEmpty() && !isFinishing && !isDestroyed &&
                    MinusOneRepository.getInstance(this@PhotoCubePickerActivity).savePhotoCube(
                        target.page, target.component, accepted.map { it.toString() }, target.container, target.before, target.gridCell)
                if (saved) retained = accepted.toSet()
                finishPicker(if (saved) "已保存 ${accepted.size} 张图片" else "图片不可读取、布局锁定或原目标已失效，未保存")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                finishPicker("图片读取失败，未保存，请重新选择")
            } finally {
                // Cancellation must still release newly acquired grants. Existing user grants are untouched.
                withContext(NonCancellable + Dispatchers.IO) {
                    (acquired - retained).forEach { uri -> runCatching {
                        contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } }
                }
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gc = intent.getIntExtra("target_grid_col", -1); val gr = intent.getIntExtra("target_grid_row", -1)
        destination = Destination(intent.getStringExtra("page_id").orEmpty(), intent.getStringExtra("component_id"),
            intent.getStringExtra("container_id"), intent.getStringExtra("before_component_id"),
            if (gc >= 0 && gr >= 0) gc to gr else null)
        launched = savedInstanceState?.getBoolean("launched") ?: false
        onBackPressedDispatcher.addCallback(this) { finishPicker("已取消选择图片") }
        if (savedInstanceState?.getBoolean("processing") == true) {
            finishPicker("图片选择已中断，请重新选择")
        } else if (!launched) lifecycleScope.launch {
            val repository = MinusOneRepository.getInstance(this@PhotoCubePickerActivity)
            OverlayService.start(this@PhotoCubePickerActivity, OverlayService.ACTION_CLOSE_PANEL)
            withTimeoutOrNull(1_200L) { repository.isPanelExpanded.first { !it } }
            if (completed || isFinishing || isDestroyed) return@launch
            launched = true
            runCatching { picker.launch(arrayOf("image/*")) }.onFailure {
                finishPicker("没有可用的图片选择器")
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("processing", processing)
        outState.putBoolean("launched", launched)
        super.onSaveInstanceState(outState)
    }
    private fun finishPicker(message: String? = null) {
        if (completed || isDestroyed) return
        completed = true
        message?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        finish()
        PanelFileBrowser.completeAccess(this, message.orEmpty(), returnToBrowser = false)
    }
    companion object {
        fun start(context: Context, pageId: String, componentId: String? = null, containerId: String? = null, beforeId: String? = null, targetGridCell: Pair<Int, Int>? = null) {
            context.startActivity(Intent(context, PhotoCubePickerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("page_id", pageId).putExtra("component_id", componentId)
                .putExtra("container_id", containerId).putExtra("before_component_id", beforeId)
                .apply { targetGridCell?.let { putExtra("target_grid_col", it.first); putExtra("target_grid_row", it.second) } })
        }
    }
}
