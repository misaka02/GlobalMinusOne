package com.example.globalminusone.screenassistant

import com.example.globalminusone.data.ScreenAssistantConfig
import com.example.globalminusone.data.ScreenOcrEngine
import com.example.globalminusone.service.capture.CaptureSurfaceState
import com.example.globalminusone.service.capture.CapturedFrame
import com.example.globalminusone.service.capture.OverlayCaptureBackend
import com.example.globalminusone.service.capture.OverlayCaptureConfig
import com.example.globalminusone.service.capture.OverlayCaptureSession
import com.example.globalminusone.service.capture.OverlayCaptureSettings
import com.example.globalminusone.service.capture.OverlayCaptureUi
import com.example.globalminusone.service.capture.OverlayScreenCapturePipeline
import com.example.globalminusone.service.ScreenAssistantMode
import com.example.globalminusone.service.screenassistant.ScreenAssistantPresentation
import com.example.globalminusone.system.privilege.UserRootSession
import com.example.globalminusone.system.media.computeMediaVolumeIndex
import com.example.globalminusone.system.media.computeMediaVolumePercent
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.StringReader
import java.io.StringWriter

class CleanSlateScreenAssistantTest {

    private fun writeUtf8Frame(dos: DataOutputStream, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        dos.writeInt(bytes.size)
        if (bytes.isNotEmpty()) dos.write(bytes)
    }

    @Test
    fun viewDebugDumpParser_parsesLengthPrefixedFieldsCoordinatesAndVisibility() {
        assertTrue(ViewDebugDumpParser.isValidViewDescriptor("android.widget.ImageView@1a2b3c4d"))
        assertTrue(ViewDebugDumpParser.isValidViewDescriptor("tv.danmaku.bili.widget.BiliImageView\$Inner@ff00"))
        assertFalse(ViewDebugDumpParser.isValidViewDescriptor("bad descriptor@1234"))
        assertFalse(ViewDebugDumpParser.isValidViewDescriptor("no_hash_here"))
        assertFalse(ViewDebugDumpParser.isValidViewDescriptor("android.widget.ImageView@zzzz"))

        fun prop(k: String, v: String) = "$k=${v.length},$v"

        val dumpText = buildString {
            append("com.android.internal.policy.DecorView@1000 ")
            append(prop("mID", "NO_ID")).append(' ')
            append(prop("getVisibility()", "VISIBLE")).append(' ')
            append(prop("layout:mLeft", "0")).append(' ')
            append(prop("layout:mTop", "0")).append(' ')
            append(prop("layout:getWidth()", "1080")).append(' ')
            append(prop("layout:getHeight()", "2400")).append('\n')

            // Visible RecyclerView with scrollY = 120
            append("  androidx.recyclerview.widget.RecyclerView@2000 ")
            append(prop("mID", "app:id/list")).append(' ')
            append(prop("getVisibility()", "VISIBLE")).append(' ')
            append(prop("layout:mLeft", "0")).append(' ')
            append(prop("layout:mTop", "100")).append(' ')
            append(prop("layout:mScrollY", "120")).append(' ')
            append(prop("layout:getWidth()", "1080")).append(' ')
            append(prop("layout:getHeight()", "2000")).append('\n')

            // Child ImageView without explicit getLocationOnScreen -> uses parent chain (100 - 120 + 200 = 180)
            append("    tv.danmaku.bili.widget.BiliImageView@3a4b5c ")
            append(prop("mID", "tv.danmaku.bili:id/cover_image")).append(' ')
            append(prop("text:sample", "hello world, = 123")).append(' ')
            append(prop("getVisibility()", "VISIBLE")).append(' ')
            append(prop("layout:mLeft", "40")).append(' ')
            append(prop("layout:mTop", "200")).append(' ')
            append(prop("layout:getWidth()", "480")).append(' ')
            append(prop("layout:getHeight()", "300")).append('\n')

            // Hidden container whose child ImageView must inherit visible=false
            append("  android.widget.FrameLayout@4000 ")
            append(prop("getVisibility()", "GONE")).append(' ')
            append(prop("layout:mLeft", "500")).append(' ')
            append(prop("layout:mTop", "200")).append(' ')
            append(prop("layout:getWidth()", "400")).append(' ')
            append(prop("layout:getHeight()", "400")).append('\n')

            append("    android.widget.ImageView@5000 ")
            append(prop("mID", "id/hidden_img")).append(' ')
            append(prop("getVisibility()", "VISIBLE")).append(' ')
            append(prop("layout:getLocationOnScreen_x()", "520")).append(' ')
            append(prop("layout:getLocationOnScreen_y()", "220")).append(' ')
            append(prop("layout:getWidth()", "360")).append(' ')
            append(prop("layout:getHeight()", "360")).append('\n')

            append("DONE.\n")
        }

        val nodes = ViewDebugDumpParser.parseDump(dumpText, 0, 0, 1080, 2400)
        assertEquals(5, nodes.size)

        val biliImg = nodes.first { it.descriptor == "tv.danmaku.bili.widget.BiliImageView@3a4b5c" }
        assertTrue(biliImg.visible)
        assertEquals(40, biliImg.leftPx)
        assertEquals(180, biliImg.topPx)
        assertEquals(520, biliImg.rightPx)
        assertEquals(480, biliImg.bottomPx)
        assertEquals("tv.danmaku.bili:id/cover_image", biliImg.viewId)

        val hiddenChild = nodes.first { it.descriptor == "android.widget.ImageView@5000" }
        assertFalse(hiddenChild.visible)

        val verifiedLeaf = ExtractedSubImageCandidate("leaf_1", 40, 180, 520, 480, "界面图像节点", 0.94f)
        val targets = ViewDebugDumpParser.selectCaptureTargets(nodes, listOf(verifiedLeaf), maxTargets = 12)
        assertEquals(1, targets.size)
        assertEquals("tv.danmaku.bili.widget.BiliImageView@3a4b5c", targets.single().first.descriptor)
        assertEquals("leaf_1", targets.single().second?.id)
    }

    @Test
    fun systemViewCaptureBridge_parsesGmoxDumpAndCaptureStreamsSafely() {
        val validPng = ByteArray(32) { idx ->
            when (idx) {
                0 -> 0x89.toByte()
                1 -> 0x50.toByte()
                2 -> 0x4E.toByte()
                3 -> 0x47.toByte()
                4 -> 0x0D.toByte()
                5 -> 0x0A.toByte()
                6 -> 0x1A.toByte()
                7 -> 0x0A.toByte()
                else -> (idx and 0xFF).toByte()
            }
        }
        assertTrue(SystemViewCaptureBridge.looksLikePng(validPng))
        assertFalse(SystemViewCaptureBridge.looksLikePng(byteArrayOf(1, 2, 3, 4)))

        val dumpBytes = ByteArrayOutputStream().apply {
            DataOutputStream(this).use { dos ->
                dos.write("GMOX".toByteArray(Charsets.US_ASCII))
                dos.writeInt(SystemViewCaptureBridge.STATUS_OK)
                writeUtf8Frame(dos, "ok")
                writeUtf8Frame(dos, "tv.danmaku.bili")
                dos.writeInt(0)
                dos.writeInt(0)
                dos.writeInt(1080)
                dos.writeInt(2400)
                writeUtf8Frame(dos, "android.widget.ImageView@1234 layout:mLeft=1,0\nDONE.\n")
            }
        }.toByteArray()
        val parsedDump = SystemViewCaptureBridge.parseDumpStream(dumpBytes)
        assertTrue(parsedDump.ok)
        assertEquals("tv.danmaku.bili", parsedDump.packageName)
        assertTrue(parsedDump.dumpText.contains("ImageView@1234"))

        val capBytes = ByteArrayOutputStream().apply {
            DataOutputStream(this).use { dos ->
                dos.write("GMOX".toByteArray(Charsets.US_ASCII))
                dos.writeInt(SystemViewCaptureBridge.STATUS_OK)
                writeUtf8Frame(dos, "ok")
                dos.writeInt(2)
                writeUtf8Frame(dos, "android.widget.ImageView@1234")
                dos.writeInt(0)
                writeUtf8Frame(dos, "ok")
                dos.writeInt(validPng.size)
                dos.write(validPng)
                writeUtf8Frame(dos, "android.widget.ImageView@5678")
                dos.writeInt(0)
                writeUtf8Frame(dos, "ok")
                val bogus = byteArrayOf(0x01, 0x02, 0x03, 0x04)
                dos.writeInt(bogus.size)
                dos.write(bogus)
            }
        }.toByteArray()
        val parsedCap = SystemViewCaptureBridge.parseCaptureStream(capBytes)
        assertTrue(parsedCap.ok)
        assertEquals(2, parsedCap.items.size)
        assertTrue(parsedCap.items[0].pngBytes.contentEquals(validPng))
        assertTrue(parsedCap.items[1].pngBytes.isEmpty())
        assertTrue(parsedCap.items[1].errMsg.contains("非有效 PNG"))

        val badStream = SystemViewCaptureBridge.parseCaptureStream("BAD!".toByteArray())
        assertFalse(badStream.ok)
        assertEquals(SystemViewCaptureBridge.STATUS_FAILED, badStream.status)
    }

    @Test
    fun captureHub_preservesFullScreenPhotoViewAndRejectsSubWindowRoots() {
        assertTrue(ScreenCaptureHub.isSafeInspectablePackage("tv.danmaku.bili"))
        assertFalse(ScreenCaptureHub.isSafeInspectablePackage("com.example.globalminusone"))
        assertFalse(ScreenCaptureHub.isSafeInspectablePackage("com.android.systemui"))
        assertFalse(ScreenCaptureHub.isSafeInspectablePackage("bad pkg; rm -rf /"))

        val xml = """
            <hierarchy rotation="0">
              <node package="com.example.reader" class="android.widget.TextView" text="第一行正文内容" bounds="[40,120][500,180]" />
              <node package="com.example.reader" class="android.widget.ImageView" bounds="[0,0][1080,2400]" />
            </hierarchy>
        """.trimIndent()
        val parsed = ScreenCaptureHub.parseXml(xml, screenW = 1080, screenH = 2400)
        assertEquals("com.example.reader", parsed.foregroundPackage)
        assertEquals(1, parsed.textNodes.size)
        assertEquals(1, parsed.mediaNodes.size)

        val fullPhotoTree = """
            GMOU1
            N	0	1	0	0	1080	2400	com.example.gallery	android.widget.FrameLayout	gallery:id/root		
            N	1	0	0	0	1080	2400	com.example.gallery	com.github.chrisbanes.photoview.PhotoView	gallery:id/photo_full		
        """.trimIndent()
        val uiSnap = ScreenCaptureHub.parseUiTree(fullPhotoTree, 1080, 2400)
        assertEquals("com.example.gallery", uiSnap.foregroundPackage)
        assertEquals(1, uiSnap.mediaNodes.size)
        assertEquals(0.94f, uiSnap.mediaNodes.single().confidence, 0.001f)
    }

    @Test
    fun imageExtractor_snapObjectHandlesLeafTapEmptyTapAndLasso() {
        val fullW = 1080
        val fullH = 2160
        val scale = 4
        val leaf = ExtractedSubImageCandidate(
            id = "leaf_crop",
            leftPx = 40 * scale,
            topPx = 120 * scale,
            rightPx = 136 * scale,
            bottomPx = 216 * scale,
            sourceTag = "原生视图树图像",
            confidence = 0.92f
        )

        val leafTap = ScreenImageExtractor.snapObject(
            w = fullW,
            h = fullH,
            points = listOf(85 * scale to 165 * scale),
            mediaNodes = listOf(leaf),
            textNodes = emptyList()
        )
        assertEquals(leaf, leafTap)

        assertNull(
            ScreenImageExtractor.snapObject(
                w = fullW,
                h = fullH,
                points = listOf(85 * scale to 165 * scale),
                mediaNodes = emptyList(),
                textNodes = emptyList()
            )
        )
        val lasso = ScreenImageExtractor.snapObject(
            w = fullW,
            h = fullH,
            points = listOf(100 to 200, 320 to 220, 300 to 460),
            mediaNodes = emptyList(),
            textNodes = emptyList()
        )
        assertNotNull(lasso)
        assertEquals("手势圈选", lasso!!.sourceTag)
    }

    @Test
    fun nativeOcrAndVolumeMath_behaveAccurately() {
        val frameOut = ByteArrayOutputStream()
        val px = intArrayOf(
            0xFF112233.toInt(),
            0xFF445566.toInt(),
            0xFF778899.toInt(),
            0xFFAABBCC.toInt()
        )
        NativeOcrWireCodec.encodeGmorRgb24(px, width = 2, height = 2, out = frameOut)
        assertEquals(16 + 2 * 2 * 3, frameOut.toByteArray().size)

        val replyJson = """{"id":"req-1","ok":true,"elapsedMs":85,"engine":"arm64-cpu","blocks":[{"text":"你好世界","left":100,"top":200,"right":300,"bottom":260}]}"""
        val parsed = NativeOcrWireCodec.parseResponseJson(
            rawLine = replyJson,
            frameWidth = 540,
            frameHeight = 1200,
            originalWidth = 1080,
            originalHeight = 2400
        )
        assertTrue(parsed.ok)
        assertEquals("你好世界", parsed.blocks.single().text)

        assertNull(UserRootSession.openDirectSuProcess("echo hi", expectedEpoch = -1L))
        assertNull(UserRootSession.openInteractiveSuProcess(expectedEpoch = -1L))
        assertNull(
            NativeOcrRunner.handshakeRootDaemon(
                BufferedReader(StringReader("""{"id":"boot","ok":true,"pong":true}""")),
                BufferedWriter(StringWriter())
            ) { "" }
        )

        val cfg = ScreenAssistantConfig(ocrEngine = ScreenOcrEngine.AUTO)
        assertEquals(ScreenOcrEngine.AUTO, cfg.ocrEngine)
        assertFalse(cfg.lsposedViewCaptureEnabled)
        assertEquals(
            "识别成功",
            ScreenOcrCoordinator.extractOpenAiContent("""{"choices":[{"message":{"content":"识别成功"}}]}""")
        )

        assertEquals(0, computeMediaVolumeIndex(0, minVol = 0, maxVol = 15))
        assertEquals(8, computeMediaVolumeIndex(50, minVol = 0, maxVol = 15))
        assertEquals(15, computeMediaVolumeIndex(100, minVol = 0, maxVol = 15))
        assertEquals(53, computeMediaVolumePercent(8, minVol = 0, maxVol = 15))
        assertEquals(1, computeMediaVolumeIndex(0, minVol = 1, maxVol = 15))
        assertEquals(0, computeMediaVolumePercent(1, minVol = 1, maxVol = 15))
        assertEquals(100, computeMediaVolumePercent(15, minVol = 1, maxVol = 15))
    }

    @Test
    fun translationClustering_typographyAndWrapperFiltering_behaveCleanly() {
        val heading = ScreenTextBlock(
            id = "h0",
            text = "Mobilizing to protect the vote",
            leftPx = 80,
            topPx = 100,
            rightPx = 900,
            bottomPx = 152,
            sourceTag = "ocr",
            textColorArgb = 0xFF181A20.toInt(),
            bgColorArgb = 0xFFFFFFFF.toInt(),
            bgBottomColorArgb = 0xFFFFFFFF.toInt(),
            isBoldHint = true,
            inkHeightPx = 38,
            sourceLineCount = 1
        )
        val line1 = ScreenTextBlock(
            id = "p1",
            text = "As threats to voters intensify nationwide,",
            leftPx = 80,
            topPx = 180,
            rightPx = 940,
            bottomPx = 216,
            sourceTag = "ocr",
            textColorArgb = 0xFF33363C.toInt(),
            bgColorArgb = 0xFFFFFFFF.toInt(),
            bgBottomColorArgb = 0xFFFFFFFF.toInt(),
            alignmentHint = ScreenTextInpainter.ALIGN_LEFT,
            inkHeightPx = 22,
            sourceLineCount = 1
        )
        val line2 = line1.copy(
            id = "p2",
            text = "communities face growing barriers.",
            topPx = 226,
            bottomPx = 262,
            rightPx = 820,
            textColorArgb = 0xFF383C42.toInt(),
            bgColorArgb = 0xFFFCFCFD.toInt(),
            bgBottomColorArgb = 0xFFFCFCFD.toInt(),
            alignmentHint = ScreenTextInpainter.ALIGN_CENTER
        )
        val clustered = ScreenTextSelectionPolicy.clusterParagraphBlocks(listOf(heading, line1, line2))
        assertEquals(2, clustered.size)
        assertEquals(heading.text, clustered[0].text)
        assertEquals("As threats to voters intensify nationwide, communities face growing barriers.", clustered[1].text)
        assertEquals(ScreenTextInpainter.ALIGN_LEFT, clustered[1].alignmentHint)
        assertEquals(2, clustered[1].sourceLineCount)

        val rw = 160
        val rh = 32
        val patch = IntArray(rw * rh) { 0xFFFFFFFF.toInt() }
        for (y in 8 until 24) {
            for (x in 12 until 148) {
                patch[y * rw + x] = 0xFF1A1A1A.toInt()
            }
        }
        val typo = ScreenTextInpainter.analyzeBlockTypographyFromRegion(
            regionPixels = patch,
            regionWidth = rw,
            regionHeight = rh,
            offsetX = 0,
            offsetY = 0,
            blockLeft = 0,
            blockTop = 0,
            blockRight = rw,
            blockBottom = rh,
            tightenBounds = true,
            explicitLineCount = 1
        )
        assertEquals(ScreenTextInpainter.ALIGN_LEFT, typo.alignmentHint)

        val tree = """
            GMOU1
            N	0	1	0	0	1080	2400	tv.danmaku.bili	android.widget.FrameLayout	tv.danmaku.bili:id/root		
            N	1	1	20	120	520	420	tv.danmaku.bili	android.widget.FrameLayout	tv.danmaku.bili:id/cover_container		
            N	2	0	20	120	520	420	tv.danmaku.bili	tv.danmaku.bili.widget.BiliImageView	tv.danmaku.bili:id/cover		
        """.trimIndent()
        val snap = ScreenCaptureHub.parseUiTree(tree, 1080, 2400)
        assertEquals(1, snap.mediaNodes.size)
        assertEquals(0.94f, snap.mediaNodes.single().confidence, 0.001f)
    }

    @Test
    fun captureHub_ignoresOversizedOrFullscreenObstructionsInUiTree() {
        val tree = """
            GMOU1
            O	0	0	1080	2400
            O	700	120	1040	360
            N	0	1	0	0	1080	2400	com.example.reader	android.widget.FrameLayout	id/root		
            N	1	0	80	180	980	780	com.example.reader	android.widget.ImageView	id/cover		
        """.trimIndent()
        val snap = ScreenCaptureHub.parseUiTree(tree, 1080, 2400)
        assertEquals(1, snap.overlayObstructions.size)
        assertEquals(700, snap.overlayObstructions.single().leftPx)
        assertEquals(1, snap.mediaNodes.size)
    }

    @Test
    fun capturePipeline_runsEnrichmentConcurrentlyBeforeMountingOverlay() = runBlocking {
        val parentJob = SupervisorJob(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + parentJob)
        try {
            val events = mutableListOf<String>()
            var deliveredSnap: AccessibilityScreenSnapshot? = null
            val mediaLeaf = ExtractedSubImageCandidate("leaf_1", 40, 120, 440, 400, "界面图像节点", 0.94f)
            val backend = object : OverlayCaptureBackend<String, String> {
                override val sdkInt: Int = 36
                override val hasOverlayPermission: Boolean = true
                override val currentAccessibility: String = "a11y"
                override val hasActivePrivilegedSession: Boolean = true
                override val supportsFastDeferredCapture: Boolean = true
                override fun snapshot(source: String) = AccessibilityScreenSnapshot(emptyList(), emptyList())
                override suspend fun collectEnrichedSnapshot(
                    source: String?,
                    trackedPackage: String,
                    extraObstructions: List<ExtractedSubImageCandidate>,
                    config: OverlayCaptureConfig
                ): AccessibilityScreenSnapshot {
                    events += "enrich"
                    return AccessibilityScreenSnapshot(
                        textNodes = listOf(ScreenTextBlock("t1", "标题", 40, 420, 400, 460)),
                        mediaNodes = listOf(mediaLeaf),
                        foregroundPackage = "tv.danmaku.bili"
                    )
                }
                override suspend fun captureNativeFast(source: String): Pair<String?, Int> {
                    events += "pixels"
                    return "fast-frame" to 0
                }
                override suspend fun capturePrivileged(
                    baseSnapshot: AccessibilityScreenSnapshot,
                    extraObstructions: List<ExtractedSubImageCandidate>,
                    allowRootPrompt: Boolean
                ): CapturedFrame<String>? = null
                override fun captureNative(source: String, reply: (String?, Int) -> Unit) = reply("fast-frame", 0)
                override fun isRecycled(frame: String) = false
                override fun recycle(frame: String) {}
                override suspend fun launchBreeno(allowPrivilegedShell: Boolean) = BreenoLaunchOutcome(true, false, "ok", emptyList())
            }
            val settings = object : OverlayCaptureSettings {
                override fun readCaptureConfig() = OverlayCaptureConfig(usePrivilegedCapture = false, rootModeEnabled = false)
                override fun trackedForegroundPackage() = "tv.danmaku.bili"
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
            pipeline.captureWithEnrichment("全局识屏") { _, snap, _ ->
                events += "mount"
                deliveredSnap = snap
            }!!.join()

            assertTrue(events.indexOf("enrich") in 0 until events.indexOf("mount"))
            assertEquals("tv.danmaku.bili", deliveredSnap?.foregroundPackage)
            assertEquals(listOf(mediaLeaf), deliveredSnap?.mediaNodes)
        } finally {
            parentJob.cancelAndJoin()
        }
    }

    @Test
    fun screenAssistantConfig_persistsFabAndLsposedDefaultsSafely() {
        val json = Json { ignoreUnknownKeys = true }
        val legacy = json.decodeFromString<ScreenAssistantConfig>("""{"wasmOcrPackId":"ch_v5"}""")
        assertEquals(0.88f, legacy.fabXFraction, 0.0001f)
        assertEquals(0.62f, legacy.fabYFraction, 0.0001f)
        assertFalse(legacy.lsposedViewCaptureEnabled)

        val custom = ScreenAssistantConfig(
            fabXFraction = 0.24f,
            fabYFraction = 0.77f,
            lsposedViewCaptureEnabled = true
        )
        val decoded = json.decodeFromString<ScreenAssistantConfig>(json.encodeToString(custom))
        assertEquals(0.24f, decoded.fabXFraction, 0.0001f)
        assertEquals(0.77f, decoded.fabYFraction, 0.0001f)
        assertTrue(decoded.lsposedViewCaptureEnabled)
    }

    @Test
    fun screenCaptureHub_mergeSnapshotsPrioritizesPrimaryOverFallback() {
        val primaryNode = ExtractedSubImageCandidate("p", 10, 10, 210, 210, "界面视觉节点", 0.95f)
        val fallbackNode = ExtractedSubImageCandidate("f", 12, 12, 208, 208, "无障碍图片", 0.80f)
        val merged = ScreenCaptureHub.mergeSnapshots(
            primary = AccessibilityScreenSnapshot(emptyList(), listOf(primaryNode), foregroundPackage = "tv.danmaku.bili"),
            uiDump = AccessibilityScreenSnapshot(emptyList(), listOf(fallbackNode), foregroundPackage = "com.miui.home")
        )
        assertEquals("tv.danmaku.bili", merged.foregroundPackage)
        assertEquals(1, merged.mediaNodes.size)
        assertEquals("p", merged.mediaNodes[0].id)
    }

    @Test
    fun presentation_showsOriginalSourceNoteWhenOnlyCropsExtracted() {
        val pres = ScreenAssistantPresentation(ScreenAssistantMode.TEXT_SELECT).apply {
            subImagesDone = true
            originalSourceNote = "LSPosed 系统框架未激活"
            selectSubImage(0)
        }
        val status = pres.statusText(imageCount = 3, originalCount = 0)
        assertTrue("Expected crop status with note, got: $status", status.contains("仅裁图"))
        assertTrue("Expected originalSourceNote inside status, got: $status", status.contains("LSPosed"))
    }
}
