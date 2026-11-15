package com.example.globalminusone.regression.v2414

import com.example.globalminusone.split.*
import org.junit.Assert.*
import org.junit.Test

class SplitTaskPolicyTest {
    private val source = SplitOrigin("app.source", 11, 0, 1)
    private fun task(id: Int, pkg: String, parent: Int?, mode: String, rect: SplitRect?) =
        SplitTask(id, parent, 0, 0, mode, true, "standard", pkg, rect)
    private fun pair() = SplitTaskSnapshot(listOf(
        task(100, "", null, "fullscreen", null), task(101, "", 100, "multi-window", null), task(102, "", 100, "multi-window", null),
        task(11, "app.source", 101, "multi-window", SplitRect(0, 0, 1000, 490)),
        task(22, "app.target", 102, "multi-window", SplitRect(0, 510, 1000, 1000))), 11)

    @Test fun actualTaskHierarchyAndCurrentBoundsExcludeIntentExtrasAndOtherDisplays() {
        val dump = """
            ACTIVITY MANAGER ACTIVITIES
            Display #4 (activities from top to bottom):
              * Task{aaa #31 type=standard A=10003:app.virtual U=0 visible=true mode=fullscreen}
                topResumedActivity=ActivityRecord{abc u0 app.virtual/.Main t31}
            Display #0 (activities from top to bottom):
              * Task{bbb #11 type=standard A=10001:app.affinity U=0 visible=true mode=fullscreen}
                topResumedActivity=ActivityRecord{abc u0 app.source/.Main t11}
                Intent { cmp=app.source/.Main dat=https://secret.invalid/private (has extras) }
                mActivityComponent=app.source/.Main
                mGlobalConfig={winConfig={ mBounds=Rect(0, 0 - 9999, 9999) }}
                CurrentConfiguration={winConfig={ mBounds=Rect(0, 0 - 1000, 1000) }}
                CurrentConfiguration={winConfig={ mBounds=Rect(1, 1 - 20, 20) }}
        """.trimIndent()
        val snapshot = parseSplitTaskDump(dump)
        assertEquals(11, snapshot.focusedId)
        assertEquals("app.source", snapshot.origin("app.source", 0)!!.packageName)
        assertEquals(SplitRect(0, 0, 1000, 1000), snapshot.tasks.last().bounds)
        val encoded = encodeSplitSnapshot(snapshot)
        assertFalse(encoded.contains("secret.invalid")); assertFalse(encoded.contains("extras"))
        assertEquals(snapshot, decodeSplitSnapshot(encoded))
    }
    @Test fun confirmationRequiresBothExactIdentitiesAndDifferentStagesInOneRoot() {
        assertTrue(confirmsSplitPair(pair(), source, 22, "app.target"))
        assertFalse(confirmsSplitPair(pair(), source.copy(packageName = "app.other"), 22, "app.target"))
        assertFalse(confirmsSplitPair(pair(), source, 22, "app.other"))
        val sameStage = pair().copy(tasks = pair().tasks.map { if (it.id == 22) it.copy(parentId = 101) else it })
        assertFalse(confirmsSplitPair(sameStage, source, 22, "app.target"))
    }
    @Test fun freeformOverlappingHiddenAndOtherUserTasksCannotMasqueradeAsSplit() {
        for (changed in listOf(
            pair().tasks.last().copy(mode = "freeform"), pair().tasks.last().copy(visible = false),
            pair().tasks.last().copy(userId = 10), pair().tasks.last().copy(displayId = 5),
            pair().tasks.last().copy(bounds = SplitRect(0, 0, 1000, 1000)))) {
            assertFalse(confirmsSplitPair(pair().copy(tasks = pair().tasks.dropLast(1) + changed), source, 22, "app.target"))
        }
    }
    @Test fun sourceCannotBePickerHomeOrAnAmbiguousUnfocusedTask() {
        val sourceTask = task(11, "app.source", null, "fullscreen", SplitRect(0, 0, 1000, 1000))
        assertNotNull(SplitTaskSnapshot(listOf(sourceTask), 11).origin("app.source", 0))
        assertNull(SplitTaskSnapshot(listOf(sourceTask), 99).origin("app.source", 0))
        assertNull(SplitTaskSnapshot(listOf(sourceTask.copy(type = "home")), 11).origin("app.source", 0))
        assertNull(SplitTaskSnapshot(listOf(sourceTask, sourceTask.copy(id = 12)), 11).target("app.source", 0))
    }
    @Test fun capabilityNeedsRealStageAndRollbackCommandsWithoutInventingStartTasks() {
        val help = "moveToSideStage <taskId> <SideStagePosition>\nremoveFromSideStage <taskId>\ntask focus <TASK_ID>"
        assertTrue(supportsSplitTaskCommands(help))
        assertFalse(supportsSplitTaskCommands(help.replace("removeFromSideStage", "unsupported")))
        assertFalse(supportsSplitTaskCommands("startTasks <task1> <task2>"))
    }
}
