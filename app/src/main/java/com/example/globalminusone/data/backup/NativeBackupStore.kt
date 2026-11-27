package com.example.globalminusone.data.backup

import android.content.Context
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.graphics.BitmapFactory
import android.content.SharedPreferences
import android.net.Uri
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.WidgetHostContract
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** Call from Dispatchers.IO. Reads only this app's configuration, browser preferences and images. */
class NativeBackupStore(context: Context, private val repository: MinusOneRepository) {
    private val context = context.applicationContext
    private val library = this.context.getSharedPreferences("independent_browser", Context.MODE_PRIVATE)
    private val settings = this.context.getSharedPreferences("independent_browser_prefs", Context.MODE_PRIVATE)

    fun capture(): DecodedNativeBackup = captureScoped(BackupExportMode.FULL)

    fun captureScoped(mode: BackupExportMode, selectedPageIds: Set<String> = emptySet()): DecodedNativeBackup {
        val corruptBackup = repository.ensureCorruptStateBackup()?.let {
            archiveCorruptStateRaw(File(context.filesDir, "native-backup-recovery"), it)
        }
        val state = scopeStateForExport(repository.state.value, mode, selectedPageIds)
        NativeBackupCodec.validateState(state)
        val warnings = mutableListOf<String>()
        if (corruptBackup != null) warnings += "原配置损坏：本备份保存当前回退后的面板，不是损坏原配置的恢复结果。原文已校验保存至应用恢复目录 ${corruptBackup.name}，请保留该文件。"
        val assets = linkedMapOf<String, ByteArray>()
        val refs = linkedMapOf<String, String>()
        var total = 0L
        val imageRefs = NativeBackupCodec.collectImageReferences(state)
        require(imageRefs.size <= 256) { "图片引用超过 256 个，无法创建完整备份" }
        imageRefs.forEachIndexed { index, ref ->
            runCatching {
                val uri = Uri.parse(ref)
                val input: InputStream? = when (uri.scheme) {
                    "content" -> context.contentResolver.openInputStream(uri)
                    "file", null -> {
                        val file = File(if (uri.scheme == "file") requireNotNull(uri.path) else ref).canonicalFile
                        require(file.toPath().startsWith(context.filesDir.canonicalFile.toPath())) { "图片不在应用管理目录内" }
                        file.inputStream()
                    }
                    else -> error("外部或网络图片不包含在备份中")
                }
                val bytes = requireNotNull(input) { "图片无法读取" }.use { NativeBackupCodec.readBounded(it, NativeBackupCodec.MAX_ASSET_BYTES) }
                validateImage(bytes)
                val path = "assets/${NativeBackupCodec.sha256(bytes)}.bin"
                if (path !in assets) {
                    require(total + bytes.size <= 32L * 1024 * 1024) { "图片总量超过 32 MiB" }
                    total += bytes.size
                    assets[path] = bytes
                }
                refs[ref] = path
            }.onFailure { warnings += "图片 ${index + 1} 未打包：${it.message.orEmpty().take(180)}" }
        }
        warnings += "SAF 目录授权、权限、登录信息和系统小部件绑定不能迁移；恢复后需重新授权或绑定。"
        val includeBrowser = mode == BackupExportMode.FULL
        if (includeBrowser) {
            warnings += "浏览器备份包含书签、历史和设置；不包含网页标签、Cookie、缓存或其它应用数据。"
        }
        val summary = buildBackupSourceSummary(repository.state.value, mode)
        return DecodedNativeBackup(
            NativeBackupPayload(
                state = state,
                browserLibrary = if (includeBrowser) snapshot(library) else emptyMap(),
                browserSettings = if (includeBrowser) snapshot(settings) else emptyMap(),
                assets = refs,
                warnings = warnings,
                installationId = installationId(),
                configurationOnly = mode == BackupExportMode.SETTINGS_ONLY,
                exportSummary = summary
            ),
            assets
        ).also { NativeBackupCodec.validatePayload(it.payload) }
    }

    fun export(output: OutputStream): NativeBackupPayload = exportScoped(output, BackupExportMode.FULL)

    fun exportScoped(output: OutputStream, mode: BackupExportMode, selectedPageIds: Set<String> = emptySet()): NativeBackupPayload =
        captureScoped(mode, selectedPageIds).let {
            NativeBackupCodec.write(output, it)
            it.payload
        }

    fun preview(input: InputStream): DecodedNativeBackup = NativeBackupCodec.readCompatible(input, snapshot(library), snapshot(settings))

    fun latestRecoveryFile(): File? = File(context.filesDir, "native-backup-recovery").listFiles()
        ?.filter { it.isFile && it.name.startsWith("before-") && it.extension == "zip" }
        ?.maxByOrNull { it.lastModified() }

    /** Recovery archive is complete and closed before any replacement. Keep it on any error. */
    fun restore(backup: DecodedNativeBackup): File {
        val settingsOnly = backup.payload.isSettingsOnlyExport()
        return restoreSelective(
            backup,
            BackupImportSelection(
                importThemeAndPanel = true,
                importTriggersAndBall = true,
                importBrowserData = !backup.payload.configurationOnly,
                importProfiles = true,
                selectedPageIds = if (settingsOnly) emptySet() else backup.payload.state.pages.map { it.id }.toSet(),
                pageImportMode = if (settingsOnly) PageImportMode.APPEND else PageImportMode.REPLACE_ALL,
                applySourcePanelGeometry = true,
                restoreFullUserData = backup.payload.isFullBackupExport()
            )
        )
    }

    fun restoreSelective(backup: DecodedNativeBackup, selection: BackupImportSelection): File {
        check(!repository.state.value.advancedConfig.layoutLocked) { "请先解锁布局，再恢复备份" }
        check(!repository.state.value.editMode) { "请先保存或取消当前编辑，再恢复备份" }
        NativeBackupCodec.validatePayload(backup.payload)
        // Revalidate the bytes too: a caller cannot bypass ZIP checks by constructing a decoded object.
        require(backup.assets.keys == backup.payload.assets.values.toSet()) { "资源缺失" }
        backup.assets.forEach { (name, bytes) -> require(bytes.size <= NativeBackupCodec.MAX_ASSET_BYTES &&
            name == "assets/${NativeBackupCodec.sha256(bytes)}.bin") { "资源校验失败" }; validateImage(bytes) }
        val recoveryDir = File(context.filesDir, "native-backup-recovery").apply { check(isDirectory || mkdirs()) }
        val recovery = File(recoveryDir, "before-${System.currentTimeMillis()}-${UUID.randomUUID()}.zip")
        try { recovery.outputStream().use { NativeBackupCodec.write(it, capture()) } }
        catch (failure: Exception) { recovery.delete(); throw failure }
        val oldLibrary = snapshot(library)
        val oldSettings = snapshot(settings)
        val assetDir = File(context.filesDir, "native-backup-assets/${UUID.randomUUID()}")
        check(assetDir.mkdirs()) { "不能创建图片恢复目录" }
        var rollbackComplete = false
        var enteredTransaction = false
        try {
            val replacements = backup.assets.mapValues { (name, bytes) ->
                // Hash-only names, under our fresh private directory; never extract archive paths directly.
                val target = File(assetDir, name.substringAfterLast('/'))
                target.outputStream().use { it.write(bytes) }
                check(target.length() == bytes.size.toLong() && NativeBackupCodec.sha256(target.readBytes()) == NativeBackupCodec.sha256(bytes)) { "图片写入校验失败" }
                Uri.fromFile(target).toString()
            }
            val state = buildSafeMergedBackupState(backup, selection, replacements)
            NativeBackupCodec.validateState(state)
            val restoreBrowser = selection.importBrowserData && !backup.payload.configurationOnly
            enteredTransaction = true
            repository.restoreNativeBackup(NativeBackupCodec.json.encodeToString(state), commitCompanion = {
                if (restoreBrowser) {
                    check(replace(library, backup.payload.browserLibrary)) { "书签/历史写入失败" }
                    check(replace(settings, backup.payload.browserSettings)) { "浏览器设置写入失败" }
                }
            }, rollbackCompanion = {
                // Try both even if one fails. Failed disk commits can already have changed memory.
                val libraryOk = !restoreBrowser || replace(library, oldLibrary)
                val settingsOk = !restoreBrowser || replace(settings, oldSettings)
                rollbackComplete = libraryOk && settingsOk
                check(rollbackComplete) { "浏览器设置回滚失败" }
            })
            return recovery
        } catch (failure: Exception) {
            // Keep staged images once a transaction was attempted: a failed disk rollback may still
            // reference them on the next process start. The recovery archive is retained as well.
            if (!enteredTransaction) assetDir.deleteRecursively()
            throw IllegalStateException("恢复失败：${failure.message}。恢复前备份保留在 ${recovery.name}", failure)
        }
    }

    private fun buildSafeMergedBackupState(
        backup: DecodedNativeBackup,
        selection: BackupImportSelection,
        replacements: Map<String, String>
    ): GlobalMinusOneState {
        val sameInstallation = backup.payload.installationId.isNotBlank() && backup.payload.installationId == installationId()
        val ownWidgetIds = if (sameInstallation) runCatching {
            AppWidgetHost(context, WidgetHostContract.HOST_ID).appWidgetIds.toSet()
        }.getOrDefault(emptySet()) else emptySet()
        val manager = AppWidgetManager.getInstance(context)
        val mappedIncoming = NativeBackupCodec.verifiedWidgetBindings(
            NativeBackupCodec.mapImages(backup.payload.state) { ref ->
                backup.payload.assets[ref]?.let { requireNotNull(replacements[it]) } ?: ref
            }
        ) { binding ->
            binding.appWidgetId in ownWidgetIds && runCatching {
                val provider = manager.getAppWidgetInfo(binding.appWidgetId)?.provider
                provider != null && provider.packageName == binding.providerPackage && provider.className == binding.providerClass
            }.getOrDefault(false)
        }
        val safeIncoming = sanitizeExternalBackupShellCards(mappedIncoming)
        val effectiveSelection = when {
            backup.payload.isSettingsOnlyExport() -> selection.copy(selectedPageIds = emptySet(), restoreFullUserData = false)
            !backup.payload.isFullBackupExport() -> selection.copy(restoreFullUserData = false)
            else -> selection
        }
        return mergeSelectedBackupState(repository.state.value, safeIncoming, effectiveSelection)
    }

    private fun validateImage(bytes: ByteArray) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..8192 && bounds.outHeight in 1..8192 &&
            bounds.outWidth.toLong() * bounds.outHeight <= 32_000_000) { "图片无效或像素尺寸超过安全上限" }
    }

    private fun installationId(): String {
        val prefs = context.getSharedPreferences("native_backup_identity", Context.MODE_PRIVATE)
        return prefs.getString("installation_id", null) ?: UUID.randomUUID().toString().also {
            check(prefs.edit().putString("installation_id", it).commit()) { "不能保存备份安装标识" }
        }
    }

    private fun snapshot(prefs: SharedPreferences): Map<String, BackupPreference> = prefs.all.mapValues { (_, value) ->
        when (value) {
            is String -> BackupPreference("string", value)
            is Boolean -> BackupPreference("boolean", value.toString())
            is Int -> BackupPreference("int", value.toString())
            is Long -> BackupPreference("long", value.toString())
            is Float -> BackupPreference("float", value.toString())
            is Set<*> -> BackupPreference("set", values = value.map { it as String })
            else -> error("不支持的浏览器设置类型")
        }
    }

    private fun replace(prefs: SharedPreferences, values: Map<String, BackupPreference>): Boolean {
        val editor = prefs.edit().clear()
        values.forEach { (key, value) -> when (value.type) {
            "string" -> editor.putString(key, value.value)
            "boolean" -> editor.putBoolean(key, value.value.toBooleanStrict())
            "int" -> editor.putInt(key, value.value.toInt())
            "long" -> editor.putLong(key, value.value.toLong())
            "float" -> editor.putFloat(key, value.value.toFloat())
            "set" -> editor.putStringSet(key, value.values.toSet())
            else -> error("不支持的浏览器设置类型")
        } }
        return editor.commit()
    }
}
