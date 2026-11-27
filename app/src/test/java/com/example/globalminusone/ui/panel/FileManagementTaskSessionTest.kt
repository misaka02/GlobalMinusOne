package com.example.globalminusone.ui.panel

import com.example.globalminusone.system.file.FileCategory
import com.example.globalminusone.system.file.FileSearchResult
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.ui.panel.file.FileManagementTaskSession

@OptIn(ExperimentalCoroutinesApi::class)
class FileManagementTaskSessionTest {
    private class FakeIo {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<String>()
        suspend fun uncooperativeRead(): String = withContext(NonCancellable) {
            started.complete(Unit)
            finish.await()
        }
    }

    @Test fun cancelledOldFinallyCannotClearNewTaskBusyOrProgressButMutationStillRefreshes() = runTest {
        var refreshes = 0
        val session = FileManagementTaskSession(this) { refreshes++ }
        session.bindRoot("a", true)
        val oldIo = FakeIo(); val newIo = FakeIo()
        var oldPublished: String? = null
        var newPublished: String? = null
        session.run(mutates = true) { oldPublished = currentTaskIO { oldIo.uncooperativeRead() } }
        runCurrent()
        assertTrue(oldIo.started.isCompleted)
        session.analyzer.results.value = FileSearchResult(emptyList(), null, 0)
        session.cancel()
        session.bindRoot("b", true)
        session.run {
            currentTaskProgress("new progress")
            newPublished = currentTaskIO { newIo.uncooperativeRead() }
        }
        runCurrent()
        oldIo.finish.complete("old result")
        runCurrent()
        assertNull(oldPublished)
        assertTrue(session.busy.value)
        assertEquals("new progress", session.progress.value)
        assertEquals(1, refreshes)
        assertEquals(1, session.reload.intValue)
        newIo.finish.complete("new result")
        runCurrent()
        assertEquals("new result", newPublished)
        assertFalse(session.busy.value)
        assertNull(session.progress.value)
    }

    @Test fun directoryOwnerSwitchRejectsOldResultAndLateProgressAndClearsAnalyzerSelection() = runTest {
        val session = FileManagementTaskSession(this) {}
        session.bindRoot("a", true)
        session.analyzer.selected.value = setOf("old-selected-file")
        session.analyzer.selecting.value = true
        val io = FakeIo()
        var token: FileManagementTaskSession.Task? = null
        var result: String? = null
        session.run { token = this; result = currentTaskIO { io.uncooperativeRead() } }
        runCurrent()
        session.bindRoot("b", true)
        assertEquals("b", session.analyzer.rootKey)
        assertTrue(session.analyzer.selected.value.isEmpty())
        assertFalse(session.analyzer.selecting.value)
        token!!.currentTaskProgress("stale progress")
        token!!.publish { session.analyzer.selected.value = setOf("stale destructive target") }
        io.finish.complete("stale result")
        runCurrent()
        assertNull(result)
        assertNull(session.progress.value)
        assertTrue(session.analyzer.selected.value.isEmpty())
        assertFalse(session.busy.value)
    }

    @Test fun collapsingCancelsReadAndDoesNotAcceptNewWorkUntilExpanded() = runTest {
        val session = FileManagementTaskSession(this) {}
        session.bindRoot("a", true)
        val io = FakeIo(); var publications = 0
        session.run { currentTaskIO { io.uncooperativeRead() }; publications++ }
        runCurrent()
        session.bindRoot("a", false)
        session.run { publications++ }
        io.finish.complete("discard")
        runCurrent()
        assertEquals(0, publications)
        assertFalse(session.busy.value)
        session.bindRoot("a", true)
        session.run { currentTaskIO { "fresh" }; publications++ }
        runCurrent()
        assertEquals(1, publications)
    }

    @Test fun failedMutationRefreshesAndRecordsErrorWhileReadOnlyFailureDoesNotRefresh() = runTest {
        var refreshes = 0
        val session = FileManagementTaskSession(this) { refreshes++ }
        session.bindRoot("a", true)
        session.run(mutates = true) { currentTaskIO<Unit> { error("permission changed") } }
        runCurrent()
        assertEquals("permission changed", session.notice.value)
        assertEquals(1, refreshes)
        assertFalse(session.busy.value)
        session.run { currentTaskIO<Unit> { error("read rejected") } }
        runCurrent()
        assertEquals("read rejected", session.notice.value)
        assertEquals(1, refreshes)
    }

    @Test fun stoppedTaskCannotStartSecondDestructiveOperationAfterOwnerChanged() = runTest {
        val session = FileManagementTaskSession(this) {}
        session.bindRoot("a", true)
        val first = FakeIo(); var deleteCalls = 0
        session.run(mutates = true) {
            currentTaskIO { deleteCalls++; first.uncooperativeRead() }
            currentTaskIO { deleteCalls++ }
        }
        runCurrent()
        session.bindRoot("b", true)
        first.finish.complete("first completed operation is not rolled back")
        runCurrent()
        assertEquals(1, deleteCalls)
    }
    @Test fun cancelledButIncompleteMutationBlocksNewWritesUntilItsJobActuallyCompletes() = runTest {
        var refreshes = 0
        val session = FileManagementTaskSession(this) { refreshes++ }
        session.bindRoot("a", true)
        val io = FakeIo(); var writes = 0
        session.run(mutates = true) { currentTaskIO { writes++; io.uncooperativeRead() } }
        runCurrent()
        session.cancel()
        session.bindRoot("b", true)
        session.run(mutates = true) { currentTaskIO { writes++ } }
        runCurrent()
        assertEquals(1, writes)
        assertTrue(session.notice.value!!.contains("等待"))
        io.finish.complete("actual IO exited")
        runCurrent()
        assertEquals(1, refreshes)
        session.run(mutates = true) { currentTaskIO { writes++ } }
        runCurrent()
        assertEquals(2, writes)
        assertEquals(2, refreshes)
    }

    @Test fun staleDirectoryCallbackCannotStartAnyFileMutationInNewOwner() = runTest {
        val session = FileManagementTaskSession(this) {}
        session.bindRoot("b", true)
        var deletes = 0
        session.run(mutates = true, expectedRootKey = "a") { currentTaskIO { deletes++ } }
        runCurrent()
        assertEquals(0, deletes)
        assertTrue(session.notice.value!!.contains("目录已变化"))
    }

    @Test fun searchResultsBelongToCapturedQueryAndCategoryEvenWhenInputsChangeDuringIO() = runTest {
        val session = FileManagementTaskSession(this) {}
        session.bindRoot("a", true)
        session.analyzer.updateQuery("before")
        val owner = session.analyzer.searchOwner()
        val gate = CompletableDeferred<Unit>()
        val oldResult = FileSearchResult(emptyList(), null, 0)
        session.run {
            val result = currentTaskIO { gate.await(); oldResult }
            session.analyzer.publishSearch(owner, result)
        }
        runCurrent()
        session.analyzer.updateQuery("after")
        session.analyzer.updateCategory(FileCategory.IMAGE)
        gate.complete(Unit)
        runCurrent()
        assertNull(session.analyzer.results.value)
        val freshOwner = session.analyzer.searchOwner()
        session.run { session.analyzer.publishSearch(freshOwner, currentTaskIO { oldResult }) }
        runCurrent()
        assertSame(oldResult, session.analyzer.results.value)
    }

    @Test fun normalMutationKeepsItsIncrementalAnalyzerResultAndNotifiesOutsideTheSessionMonitor() = runTest {
        lateinit var session: FileManagementTaskSession
        var refreshes = 0
        session = FileManagementTaskSession(this) {
            assertFalse(Thread.holdsLock(session))
            refreshes++
        }
        session.bindRoot("a", true)
        val incremental = FileSearchResult(emptyList(), null, 0)
        session.run(mutates = true) {
            assertFalse(Thread.holdsLock(session))
            currentTaskIO { "renamed" }
            session.analyzer.results.value = incremental
        }
        runCurrent()
        assertSame(incremental, session.analyzer.results.value)
        assertEquals(1, refreshes)
    }

    @Test fun alreadyCancelledScopeNeverLeavesBusyOrPublishesMutationRefresh() = runTest {
        val parent = Job()
        val stoppedScope = CoroutineScope(StandardTestDispatcher(testScheduler) + parent)
        parent.cancel()
        var writes = 0; var refreshes = 0
        val session = FileManagementTaskSession(stoppedScope) { refreshes++ }
        session.bindRoot("a", true)
        session.run(mutates = true) { currentTaskIO { writes++ } }
        runCurrent()
        assertFalse(session.busy.value)
        assertNull(session.progress.value)
        assertEquals(0, writes)
        assertEquals(0, refreshes)
    }

    @Test fun interruptedWriterReceiptInvalidatesNewerReadSnapshotWithoutClearingFollowingTaskBusy() = runTest {
        var refreshes = 0
        val session = FileManagementTaskSession(this) { refreshes++ }
        session.bindRoot("a", true)
        val oldWriter = FakeIo(); val staleReader = FakeIo(); val freshReader = FakeIo()
        var stalePublished: String? = null
        var freshPublished: String? = null
        session.run(mutates = true) { currentTaskIO { oldWriter.uncooperativeRead() } }
        runCurrent()
        session.cancel()
        session.run {
            val capturedOldSnapshot = "before writer completed"
            currentTaskIO { staleReader.uncooperativeRead() }
            stalePublished = capturedOldSnapshot
        }
        runCurrent()
        assertTrue(staleReader.started.isCompleted)
        oldWriter.finish.complete("mutation actually exited")
        runCurrent()
        assertEquals(1, refreshes)
        assertFalse(session.busy.value)
        session.run {
            currentTaskProgress("fresh reader")
            freshPublished = currentTaskIO { freshReader.uncooperativeRead() }
        }
        runCurrent()
        staleReader.finish.complete("late old snapshot")
        runCurrent()
        assertNull(stalePublished)
        assertTrue(session.busy.value)
        assertEquals("fresh reader", session.progress.value)
        assertEquals(1, refreshes)
        freshReader.finish.complete("after writer completed")
        runCurrent()
        assertEquals("after writer completed", freshPublished)
        assertFalse(session.busy.value)
    }

}
