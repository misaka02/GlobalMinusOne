package com.example.globalminusone

import android.accessibilityservice.AccessibilityService
import androidx.compose.runtime.MonotonicFrameClock
import com.example.globalminusone.data.AppAppearanceOverride
import com.example.globalminusone.data.BoundWidgetInfo
import com.example.globalminusone.data.CardConfig
import com.example.globalminusone.data.CardType
import com.example.globalminusone.data.ClipboardRecord
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.ConfigProfile
import com.example.globalminusone.data.FileBrowserConfig
import com.example.globalminusone.data.FileBrowserRoot
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.TodoItem
import com.example.globalminusone.data.backup.BackupExportMode
import com.example.globalminusone.data.backup.BackupImportSelection
import com.example.globalminusone.data.backup.BackupSourceSummary
import com.example.globalminusone.data.backup.NativeBackupCodec
import com.example.globalminusone.data.backup.NativeBackupPayload
import com.example.globalminusone.data.backup.PageImportMode
import com.example.globalminusone.data.backup.isFullBackupExport
import com.example.globalminusone.data.backup.isSettingsOnlyExport
import com.example.globalminusone.data.backup.mergeSelectedBackupState
import com.example.globalminusone.data.backup.sanitizeExternalBackupShellCards
import com.example.globalminusone.data.backup.scopeStateForExport
import com.example.globalminusone.data.operations.SubPanelTemplate
import com.example.globalminusone.data.policy.ShellCommandSpec
import com.example.globalminusone.data.policy.ShellExecutionMode
import com.example.globalminusone.screenassistant.AccessibilityScreenSnapshot
import com.example.globalminusone.screenassistant.BreenoLaunchOutcome
import com.example.globalminusone.screenassistant.ExtractedSubImageCandidate
import com.example.globalminusone.service.capture.CaptureSurfaceState
import com.example.globalminusone.service.capture.CapturedFrame
import com.example.globalminusone.service.capture.OverlayCaptureBackend
import com.example.globalminusone.service.capture.OverlayCaptureConfig
import com.example.globalminusone.service.capture.OverlayCaptureSession
import com.example.globalminusone.service.capture.OverlayCaptureSettings
import com.example.globalminusone.service.capture.OverlayCaptureUi
import com.example.globalminusone.service.capture.OverlayScreenCapturePipeline
import com.example.globalminusone.ui.panel.layout.ResidentLoopPagerState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AuditV24126FixesTest {
    private val json = NativeBackupCodec.json

    @Test
    fun settingsOnlyExportFlagDistinguishesZipTemplatesFromLegacyJson() {
        val base = GlobalMinusOneState()
        val settingsExport = NativeBackupPayload(
            state = scopeStateForExport(base, BackupExportMode.SETTINGS_ONLY),
            configurationOnly = true,
            exportSummary = BackupSourceSummary(exportMode = BackupExportMode.SETTINGS_ONLY.name)
        )
        val fullExport = NativeBackupPayload(
            state = base,
            configurationOnly = false,
            exportSummary = BackupSourceSummary(exportMode = BackupExportMode.FULL.name)
        )
        val legacyJsonImport = NativeBackupPayload(
            state = base,
            configurationOnly = true,
            exportSummary = null
        )
        val selectedPagesExport = NativeBackupPayload(
            state = scopeStateForExport(base, BackupExportMode.SELECTED_PAGES),
            configurationOnly = false,
            exportSummary = BackupSourceSummary(exportMode = BackupExportMode.SELECTED_PAGES.name)
        )

        assertTrue(settingsExport.isSettingsOnlyExport())
        assertFalse(settingsExport.isFullBackupExport())
        assertFalse(fullExport.isSettingsOnlyExport())
        assertTrue(fullExport.isFullBackupExport())
        assertFalse(legacyJsonImport.isSettingsOnlyExport())
        assertTrue(legacyJsonImport.isFullBackupExport())
        assertFalse(selectedPagesExport.isSettingsOnlyExport())
        assertFalse(selectedPagesExport.isFullBackupExport())
    }

    @Test
    fun fullRestoreCopiesTopLevelUserDataAndPrunesRemovedBoundWidgets() {
        val widgetKept = BoundWidgetInfo(11, "pkg.a", "pkg.a.Provider", "Kept")
        val widgetIncoming = BoundWidgetInfo(22, "pkg.b", "pkg.b.Provider", "New")
        val widgetLegacy = BoundWidgetInfo(25, "pkg.l", "pkg.l.Provider", "Legacy")
        val widgetStale = BoundWidgetInfo(33, "pkg.c", "pkg.c.Provider", "Stale")
        val widgetRemoved = BoundWidgetInfo(44, "pkg.d", "pkg.d.Provider", "Removed")

        val current = GlobalMinusOneState(
            pages = listOf(
                PageConfig(
                    id = "cur-1",
                    name = "Cur1",
                    components = listOf(ComponentItem(id = "cw", type = ComponentType.APP_WIDGET, widgetId = 11))
                )
            ),
            cards = listOf(CardConfig(id = "legacy-w", type = CardType.APP_WIDGET, widgetId = 25)),
            boundWidgets = listOf(widgetKept, widgetLegacy, widgetRemoved),
            noteText = "old note"
        )
        val incoming = GlobalMinusOneState(
            pages = listOf(
                PageConfig(
                    id = "in-1",
                    name = "In1",
                    components = listOf(ComponentItem(id = "iw", type = ComponentType.APP_WIDGET, widgetId = 22))
                )
            ),
            cards = listOf(CardConfig(id = "legacy-w", type = CardType.APP_WIDGET, widgetId = 25)),
            boundWidgets = listOf(widgetIncoming, widgetLegacy, widgetStale),
            favoriteApps = listOf("com.example.fav"),
            appAppearanceOverrides = mapOf("com.example.fav" to AppAppearanceOverride(displayName = "Fav")),
            todos = listOf(TodoItem(id = "t1", content = "Audit fix")),
            clipboardHistory = listOf(ClipboardRecord(id = "c1", text = "copied")),
            fileBrowserConfig = FileBrowserConfig(roots = listOf(FileBrowserRoot("content://tree/1", "Docs"))),
            subPanelLibrary = listOf(SubPanelTemplate(id = "tpl-1", name = "RepoTemplate")),
            noteText = "restored note"
        )

        val restored = mergeSelectedBackupState(
            current = current,
            incoming = incoming,
            selection = BackupImportSelection(
                selectedPageIds = setOf("in-1"),
                pageImportMode = PageImportMode.REPLACE_ALL,
                restoreFullUserData = true
            )
        )

        assertEquals("restored note", restored.noteText)
        assertEquals(incoming.todos, restored.todos)
        assertEquals(incoming.clipboardHistory, restored.clipboardHistory)
        assertEquals(incoming.favoriteApps, restored.favoriteApps)
        assertEquals(incoming.appAppearanceOverrides, restored.appAppearanceOverrides)
        assertEquals(incoming.fileBrowserConfig, restored.fileBrowserConfig)
        assertEquals(incoming.subPanelLibrary, restored.subPanelLibrary)
        assertEquals(listOf(widgetLegacy, widgetIncoming), restored.boundWidgets)
    }

    @Test
    fun sanitizeExternalBackupShellCardsEnforcesConfirmationOnPrivilegedCards() {
        val rootCard = ComponentItem(
            id = "root-1",
            type = ComponentType.SHELL_COMMAND,
            intentUri = ShellCommandSpec(command = "id", mode = ShellExecutionMode.ROOT, requireConfirm = false).encodeToIntentUri()
        )
        val normalCard = ComponentItem(
            id = "sh-1",
            type = ComponentType.SHELL_COMMAND,
            intentUri = ShellCommandSpec(command = "echo hi", mode = ShellExecutionMode.NORMAL, requireConfirm = false).encodeToIntentUri()
        )
        val shizukuNested = ComponentItem(
            id = "folder-1",
            type = ComponentType.FOLDER,
            containerComponents = listOf(
                ComponentItem(
                    id = "shiz-1",
                    type = ComponentType.SHELL_COMMAND,
                    intentUri = ShellCommandSpec(command = "ls", mode = ShellExecutionMode.SHIZUKU, requireConfirm = false).encodeToIntentUri()
                )
            )
        )
        val snapState = GlobalMinusOneState(
            pages = listOf(PageConfig(id = "sp-page", name = "Snap", components = listOf(rootCard))),
            profiles = emptyList()
        )
        val rawState = GlobalMinusOneState(
            pages = listOf(PageConfig(id = "p1", name = "P1", components = listOf(rootCard, normalCard, shizukuNested))),
            subPanelLibrary = listOf(
                SubPanelTemplate(
                    id = "tpl",
                    name = "T",
                    pages = listOf(PageConfig(id = "tp", name = "TP", components = listOf(rootCard)))
                )
            ),
            profiles = listOf(ConfigProfile(id = "prof", name = "P", stateJson = json.encodeToString(snapState)))
        )

        val cleaned = sanitizeExternalBackupShellCards(rawState)
        assertTrue(ShellCommandSpec.fromComponent(cleaned.pages[0].components[0]).requireConfirm)
        assertFalse(ShellCommandSpec.fromComponent(cleaned.pages[0].components[1]).requireConfirm)
        assertTrue(ShellCommandSpec.fromComponent(cleaned.pages[0].components[2].containerComponents[0]).requireConfirm)
        assertTrue(ShellCommandSpec.fromComponent(cleaned.subPanelLibrary[0].pages[0].components[0]).requireConfirm)
        val decodedProfile = json.decodeFromString<GlobalMinusOneState>(cleaned.profiles[0].stateJson)
        assertTrue(ShellCommandSpec.fromComponent(decodedProfile.pages[0].components[0]).requireConfirm)
    }

    @Test
    fun backupReadRejectsMalformedUtf8EvenWhenHashMatches() {
        val badJsonBytes = "{\"noteText\":\"".toByteArray() + byteArrayOf(0xc3.toByte(), 0x28) + "\"}".toByteArray()
        val manifestBytes = "{\"format\":\"GlobalMinusOne\",\"version\":1,\"hashes\":{\"configuration.json\":\"${NativeBackupCodec.sha256(badJsonBytes)}\"}}"
            .toByteArray(Charsets.UTF_8)
        val zipBytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifestBytes); zip.closeEntry()
                zip.putNextEntry(ZipEntry("configuration.json")); zip.write(badJsonBytes); zip.closeEntry()
            }
        }.toByteArray()

        assertTrue(runCatching { NativeBackupCodec.read(ByteArrayInputStream(zipBytes)) }.isFailure)
    }

    @Test
    fun cancelledCaptureWithoutReplacementRestoresSurfaceWhileSupersededCaptureDefers() = runBlocking {
        val parentJob = SupervisorJob()
        val scope = CoroutineScope(coroutineContext + parentJob)
        val session = OverlayCaptureSession()
        val restores = mutableListOf<String>()
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()

        val backend = object : OverlayCaptureBackend<Any, String> {
            override val sdkInt: Int = AccessibilityService.GLOBAL_ACTION_BACK + 30
            override val hasOverlayPermission: Boolean = true
            override val currentAccessibility: String = "a11y"
            override fun snapshot(source: String) = AccessibilityScreenSnapshot(emptyList(), emptyList())
            override suspend fun capturePrivileged(
                baseSnapshot: AccessibilityScreenSnapshot,
                extraObstructions: List<ExtractedSubImageCandidate>,
                allowRootPrompt: Boolean
            ): CapturedFrame<Any>? = null
            override fun captureNative(source: String, reply: (Any?, Int) -> Unit) {
                started.complete(Unit)
                scope.launch {
                    gate.await()
                    reply(null, 1)
                }
            }
            override fun isRecycled(frame: Any): Boolean = false
            override fun recycle(frame: Any) = Unit
            override suspend fun launchBreeno(allowPrivilegedShell: Boolean) = BreenoLaunchOutcome(true, false, "ok", emptyList())
        }
        val settings = object : OverlayCaptureSettings {
            override fun readCaptureConfig() = OverlayCaptureConfig(false, false)
            override fun trackedForegroundPackage(): String = "pkg"
            override fun allowBreenoPrivilegedShell(): Boolean = false
        }
        val ui = object : OverlayCaptureUi {
            override val destroying: Boolean = false
            override val panelVisible: Boolean = false
            override fun dismissCaptureTools() = Unit
            override fun prepareCaptureSurface() = CaptureSurfaceState(false, true, emptyList())
            override fun restoreCaptureSurface() { restores += "restore" }
            override fun requestAccessibilityPermission(closePanel: Boolean) = Unit
            override fun animateClosePanel() = Unit
            override fun showMessage(message: String, long: Boolean) = Unit
            override fun reportCaptureFailure(failure: Throwable) = Unit
        }
        val pipeline = OverlayScreenCapturePipeline(scope, session, backend, settings, ui)

        val job = pipeline.capture("区域截屏") { _, _ -> }!!
        started.await()
        session.cancelRegionCapture()
        job.join()
        assertEquals(1, restores.size)

        parentJob.cancelAndJoin()
    }

    @Test
    fun cancellingResidentLoopPagerAnimationClearsAnimatingAndDraggingFlags() = runBlocking {
        val state = ResidentLoopPagerState(initialRealPage = 0, pageCount = 3)
        val clock = object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                delay(16L)
                return onFrame(System.nanoTime())
            }
        }
        val animJob = launch(clock) {
            state.animateToRealPage(targetReal = 1, direction = 1, animMs = 400)
        }
        while (!state.isAnimatingOrDragging) delay(1L)
        assertTrue(state.isAnimatingOrDragging)
        animJob.cancelAndJoin()
        assertFalse(state.isAnimatingOrDragging)
        assertFalse(state.isScrollInProgress)
    }
}
