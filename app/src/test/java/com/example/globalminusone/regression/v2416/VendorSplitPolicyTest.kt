package com.example.globalminusone.regression.v2416

import com.example.globalminusone.split.*
import org.junit.Assert.*
import org.junit.Test

class VendorSplitPolicyTest {
    private val source = SplitTask(11, null, 0, 0, "fullscreen", true, "standard", "app.source", SplitRect(0, 0, 720, 1280))
    private val homes = setOf("app.launcher")
    private fun allowed(task: SplitTask = source, focus: Int = 11, target: String = "app.target") =
        vendorSplitSourceAllowed(SplitTaskSnapshot(listOf(task), focus), 11, task.packageName, target, homes)

    @Test fun sourceMustBeTheExactVisibleFullscreenForegroundTaskOnDisplayZeroUserZero() {
        assertTrue(allowed())
        for (task in listOf(source.copy(id = 12), source.copy(visible = false), source.copy(mode = "freeform"),
            source.copy(mode = "multi-window"), source.copy(userId = 10), source.copy(displayId = 5),
            source.copy(type = "home"), source.copy(bounds = null))) assertFalse(allowed(task))
        assertFalse(allowed(focus = 99)); assertFalse(allowed(target = "app.source"))
    }
    @Test fun pickerSystemPagesAndHomeAreRejectedEvenIfTheyReportStandardFullscreen() {
        for (pkg in listOf("com.example.globalminusone", "com.android.settings", "com.android.systemui",
            "com.android.permissioncontroller", "app.launcher")) assertFalse(allowed(source.copy(packageName = pkg)))
        assertFalse(allowed(target = "app.launcher"))
        assertFalse(allowed(target = "com.example.globalminusone"))
        assertFalse(vendorSplitSourceAllowed(SplitTaskSnapshot(listOf(source), 11), 11, "app.source", "app.target", emptySet()))
        assertFalse(vendorSplitSourceAllowed(SplitTaskSnapshot(listOf(source, source.copy(id = 12, packageName = "app.other")), 11),
            11, "app.source", "app.target", homes))
    }
    @Test fun wirePreservesBothIdentitiesAndRejectsUnexpectedUsersAndShellFields() {
        val request = SplitSystemRequest.VendorEdge(11, "app.source", "app.target/.Main")
        assertEquals(request, SplitSystemProtocol.decode(7, 11, SplitSystemProtocol.component(request), 0))
        assertEquals(11, SplitSystemProtocol.taskId(request))
        for (value in listOf("app.source|app.target/.Main;id", "app.source|app.target/.Main|extra", "app.source|https://target"))
            assertThrows(IllegalArgumentException::class.java) { SplitSystemProtocol.decode(7, 11, value, 0) }
        assertThrows(IllegalArgumentException::class.java) { SplitSystemProtocol.decode(7, 11, "app.source|app.target/.Main", 10) }
        assertThrows(IllegalArgumentException::class.java) { SplitSystemProtocol.decode(7, 0, "app.source|app.target/.Main", 0) }
    }
    @Test fun rootEntryIsFixedAndCannotSmuggleAnApkShellCommandOrOrdinaryLaunch() {
        val command = SplitVendorRootCommand.command(SplitSystemRequest.VendorEdge(11, "app.source", "app.target/.Main"),
            "/data/app/~~token/app-package/base.apk")
        assertTrue(command.contains("com.example.globalminusone.split.VendorSplitEntry edge 11"))
        assertFalse(command.contains("am start")); assertFalse(command.contains("task focus")); assertFalse(command.contains("moveToSideStage"))
        for (path in listOf("/sdcard/foreign.apk", "/data/app/base.apk';id;'", "/data/app/base.apk\nreboot", "/data/app/../../sdcard/base.apk"))
            assertThrows(IllegalArgumentException::class.java) { SplitVendorRootCommand.command(SplitSystemRequest.VendorProbe, path) }
    }
    @Test fun vendorAcceptanceAndTransportExitZeroCannotStandInForVerifiedPair() {
        assertTrue(vendorSplitRequestAccepted(1))
        for (code in listOf(-1, 0, 2, 1001)) assertFalse(vendorSplitRequestAccepted(code))
        assertFalse(confirmsSplitPair(SplitTaskSnapshot(listOf(source), 11), SplitOrigin("app.source", 11, 0, 1), 22, "app.target"))
        assertFalse(SplitSystemProtocol.sanitize(SplitSystemRequest.VendorProbe, SplitSystemResult(0, "some manager exists", "")).succeeded)
        assertEquals("厂商分屏权限拒绝", SplitSystemProtocol.sanitize(SplitSystemRequest.VendorProbe, SplitSystemResult(4, "", "")).stderr)
    }
    @Test fun applicationControlledAffinityCannotMasqueradeAsTheForegroundPackage() {
        val snapshot = parseSplitTaskDump("""
            Display #0 (activities from top to bottom):
              * Task{abc #11 type=standard A=10001:app.source U=0 visible=true mode=fullscreen}
                topResumedActivity=ActivityRecord{aaa u0 app.actual/.Main t11}
                CurrentConfiguration={winConfig={ mBounds=Rect(0, 0 - 720, 1280) }}
        """.trimIndent())
        assertEquals("app.actual", snapshot.tasks.single().packageName)
        assertNull(snapshot.origin("app.source", 0))
        assertFalse(vendorSplitSourceAllowed(snapshot, 11, "app.source", "app.target", homes))
    }
    @Test fun observedPairRequiresTwoVisibleStageBranchesUnderTheSameRootAndSeparateBounds() {
        val snapshot = parseSplitTaskDump("""
            Display #0 (activities from top to bottom):
              * Task{root #100 type=undefined U=0 visible=true mode=fullscreen}
                * Task{left #101 type=undefined U=0 visible=true mode=multi-window}
                  * Task{source #11 type=standard U=0 visible=true mode=multi-window}
                    topResumedActivity=ActivityRecord{aaa u0 app.source/.Main t11}
                    mActivityComponent=app.source/.Main
                    CurrentConfiguration={winConfig={ mBounds=Rect(0, 0 - 720, 620) }}
                * Task{right #102 type=undefined U=0 visible=true mode=multi-window}
                  * Task{target #22 type=standard U=0 visible=true mode=multi-window}
                    mActivityComponent=app.target/.Main
                    CurrentConfiguration={winConfig={ mBounds=Rect(0, 660 - 720, 1280) }}
        """.trimIndent())
        val origin = SplitOrigin("app.source", 11, 0, 1)
        assertTrue(confirmsSplitPair(snapshot, origin, 22, "app.target"))
        for (replacement in listOf(
            snapshot.tasks.first { it.id == 102 }.copy(parentId = 999),
            snapshot.tasks.first { it.id == 101 }.copy(visible = false),
            snapshot.tasks.first { it.id == 22 }.copy(mode = "freeform"),
            snapshot.tasks.first { it.id == 22 }.copy(bounds = SplitRect(0, 0, 720, 1280)))) {
            val changed = snapshot.copy(tasks = snapshot.tasks.map { if (it.id == replacement.id) replacement else it })
            assertFalse(confirmsSplitPair(changed, origin, 22, "app.target"))
        }
    }
}
