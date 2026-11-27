package com.example.globalminusone.regression.v2452

import com.example.globalminusone.data.AdvancedConfig
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.ScreenAssistantConfig
import com.example.globalminusone.data.ScreenOcrEngine
import com.example.globalminusone.screenassistant.AccessibilityScreenSnapshot
import com.example.globalminusone.screenassistant.BreenoLaunchOutcome
import com.example.globalminusone.screenassistant.ExtractedSubImageCandidate
import com.example.globalminusone.screenassistant.ScreenAssistantOverlayUiPolicy
import com.example.globalminusone.screenassistant.ScreenCaptureHub
import com.example.globalminusone.screenassistant.ScreenTextBlock
import com.example.globalminusone.service.capture.CaptureSurfaceState
import com.example.globalminusone.service.capture.CapturedFrame
import com.example.globalminusone.service.capture.OverlayCaptureBackend
import com.example.globalminusone.service.capture.OverlayCaptureConfig
import com.example.globalminusone.service.capture.OverlayCaptureSession
import com.example.globalminusone.service.capture.OverlayCaptureSettings
import com.example.globalminusone.service.capture.OverlayCaptureUi
import com.example.globalminusone.service.capture.OverlayScreenCapturePipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V2452RootAndPaddleOcrTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun uiTreeParser_extractsTrueScreenBoundsCustomCoversAndObstructions() {
        val tree = """
            GMOU1
            O	700	120	1040	360
            N	0	2	0	0	1080	2400	com.example.globalminusone	android.widget.FrameLayout	id/self_root		
            N	1	1	0	0	1080	2400	tv.danmaku.bili	androidx.recyclerview.widget.RecyclerView	tv.danmaku.bili:id/feed		
            N	2	2	24	340	1056	660	tv.danmaku.bili	android.widget.FrameLayout	tv.danmaku.bili:id/card_root		
            N	3	0	120	340	520	620	tv.danmaku.bili	android.view.View	tv.danmaku.bili:id/slot		
            N	3	0	430	575	510	612	tv.danmaku.bili	android.widget.TextView	tv.danmaku.bili:id/duration	2:28	
        """.trimIndent()

        val snap = ScreenCaptureHub.parseUiTree(tree, 1080, 2400)
        assertEquals("tv.danmaku.bili", snap.foregroundPackage)
        assertEquals(1, snap.overlayObstructions.size)
        assertEquals(1, snap.textNodes.size)
        assertEquals("2:28", snap.textNodes.single().text)
        assertEquals(1, snap.mediaNodes.size)
        val cover = snap.mediaNodes.single()
        assertEquals(120, cover.leftPx)
        assertEquals(340, cover.topPx)
        assertEquals(520, cover.rightPx)
        assertEquals(620, cover.bottomPx)
        assertEquals("界面视觉节点", cover.sourceTag)
    }

    @Test
    fun uiTreeParser_ignoresSelfAndSystemUiAndSkipsFullChromeLeaves() {
        val tree = """
            GMOU1
            N	0	1	0	0	1080	2400	com.android.systemui	android.widget.ImageView	sys/bg		
            N	1	0	0	-400	500	-100	com.example.gallery	android.widget.ImageView	app:id/offscreen		
            N	1	0	0	0	1080	2400	com.example.gallery	android.view.View	app:id/backdrop		
            N	1	0	0	0	1080	2400	com.example.gallery	com.github.chrisbanes.photoview.PhotoView	app:id/photo_full		
        """.trimIndent()
        val snap = ScreenCaptureHub.parseUiTree(tree, 1080, 2400)
        assertEquals("com.example.gallery", snap.foregroundPackage)
        assertEquals(1, snap.mediaNodes.size)
        assertEquals("界面图像节点", snap.mediaNodes.single().sourceTag)
        assertEquals(0.94f, snap.mediaNodes.single().confidence, 0.001f)
    }

    @Test
    fun capturePipeline_autoUsesAuthorizedPrivilegedSessionWithoutSuPrompt() = runBlocking {
        val parentJob = SupervisorJob(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + parentJob)
        try {
            val events = mutableListOf<String>()
            var capturedAllowPrompt: Boolean? = null
            var deliveredSnapshot: AccessibilityScreenSnapshot? = null
            val expectedNode = ScreenTextBlock("p1", "Privileged", 10, 20, 120, 60)

            val backend = object : OverlayCaptureBackend<String, String> {
                override val sdkInt: Int = 36
                override val hasOverlayPermission: Boolean = true
                override val currentAccessibility: String = "a11y"
                override val hasActivePrivilegedSession: Boolean = true
                override fun snapshot(source: String) = AccessibilityScreenSnapshot(emptyList(), emptyList(), foregroundPackage = "demo.app")
                override suspend fun capturePrivileged(
                    baseSnapshot: AccessibilityScreenSnapshot,
                    extraObstructions: List<ExtractedSubImageCandidate>,
                    allowRootPrompt: Boolean
                ): CapturedFrame<String> {
                    events += "privileged"
                    capturedAllowPrompt = allowRootPrompt
                    return CapturedFrame("priv-frame", AccessibilityScreenSnapshot(listOf(expectedNode), emptyList(), foregroundPackage = "demo.app"))
                }
                override fun captureNative(source: String, reply: (String?, Int) -> Unit) {
                    events += "native"
                    reply("native-frame", 0)
                }
                override fun isRecycled(frame: String) = false
                override fun recycle(frame: String) {}
                override suspend fun launchBreeno(allowPrivilegedShell: Boolean) = BreenoLaunchOutcome(true, false, "ok", emptyList())
            }
            val settings = object : OverlayCaptureSettings {
                override fun readCaptureConfig() = OverlayCaptureConfig(usePrivilegedCapture = false, rootModeEnabled = false)
                override fun trackedForegroundPackage() = "demo.app"
                override fun allowBreenoPrivilegedShell() = false
            }
            val ui = object : OverlayCaptureUi {
                override val destroying = false
                override val panelVisible = false
                override fun dismissCaptureTools() {}
                override fun prepareCaptureSurface() = CaptureSurfaceState(false, false, emptyList())
                override fun restoreCaptureSurface() {}
                override fun requestAccessibilityPermission(closePanel: Boolean) {}
                override fun animateClosePanel() {}
                override fun showMessage(message: String, long: Boolean) {}
                override fun reportCaptureFailure(failure: Throwable) {}
            }
            val pipeline = OverlayScreenCapturePipeline(scope, OverlayCaptureSession(), backend, settings, ui)
            val job = pipeline.capture("识屏") { _, snap ->
                deliveredSnapshot = snap
            }
            assertNotNull(job)
            job?.join()
            assertEquals(listOf("privileged"), events)
            assertEquals(false, capturedAllowPrompt)
            assertEquals(listOf(expectedNode), deliveredSnapshot?.textNodes)
        } finally {
            parentJob.cancelAndJoin()
        }
    }

    @Test
    fun configAndInventory_roundtripPpOcrOnnxAndPreserveV5Defaults() {
        val state = GlobalMinusOneState(
            advancedConfig = AdvancedConfig(
                screenAssistantConfig = ScreenAssistantConfig(
                    ocrEngine = ScreenOcrEngine.PPOCR_ONNX,
                    ppOcrLangPack = "korean_v5"
                )
            )
        )
        val raw = json.encodeToString(state)
        val decoded = json.decodeFromString<GlobalMinusOneState>(raw)
        assertEquals(ScreenOcrEngine.PPOCR_ONNX, decoded.advancedConfig.screenAssistantConfig.ocrEngine)
        assertEquals("korean_v5", decoded.advancedConfig.screenAssistantConfig.ppOcrLangPack)

        // Ensure legacy v5 JSON without ppOcrLangPack decodes cleanly
        val legacyJson = raw.replace(""""ppOcrLangPack":"korean_v5",""", "")
        val legacyDecoded = json.decodeFromString<GlobalMinusOneState>(legacyJson)
        assertEquals("ch_v5", legacyDecoded.advancedConfig.screenAssistantConfig.ppOcrLangPack)

        assertEquals(
            "去下载模型 >",
            ScreenAssistantOverlayUiPolicy.settingsHintActionLabel("尚未下载 PP-OCR 语言包", "PP-OCR", ScreenOcrEngine.PPOCR_ONNX)
        )
    }
}
