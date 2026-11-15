package com.example.globalminusone

import com.example.globalminusone.system.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class RootWriteSafetyTest {
    @After fun reset() { UserRootSession.resetForTests() }
    private val target = "/data/system/users/0/settings_system.xml"
    private val old = "original 中文\n".toByteArray()
    private val updated = "new 中文\n".toByteArray()
    private fun snapshot() = RootFileSnapshot(target, "12|34|1000|1000|600|1|${old.size}|1700000000|u:object_r:system_data_file:s0",
        textFingerprint(old), UserRootSession.authorizedEpoch()!!)
    private suspend fun authorize() {
        UserRootSession.resetForTests(RootExecutor { r -> if (r.entryMarker == null) RootProcessOutcome(0, "0".toByteArray())
            else RootProcessOutcome(0, stderr = "${r.entryMarker}:0\n") }, { "/synthetic/su" })
        assertTrue(UserRootSession.requestAuthorization().succeeded)
    }
    @Test fun individualConfigurationPathsAllowedButSensitiveAndExecutableTargetsRefused() {
        listOf("/system/etc/hosts", "/vendor/etc/audio.conf", target, "/data/user/0/example.app/files/user.ini").forEach {
            assertNull("Unexpected denial $it", RootWritePolicy.refusal(it))
        }
        listOf("/dev/block/by-name/boot", "/proc/sys/kernel/x", "/sys/x", "/metadata/x", "/data/misc/keystore/x",
            "/data/system/locksettings.db", "/data/system/accounts.xml", "/data/user/0/example.app/databases/x",
            "/system/etc/init/app.rc", "/data/local/work/start.sh", "/data/adb/modules/x/config", "/data/local/.gmo-recovery-a/original").forEach {
            assertNotNull("Accepted dangerous $it", RootWritePolicy.refusal(it))
        }
        assertTrue(runCatching { RootWritePolicy.requireSafeDirectory("/system/etc") }.isFailure)
        assertTrue(runCatching { RootWritePolicy.requireSafeDirectory("/data/local/tmp") }.isFailure)
        assertTrue(runCatching { RootWritePolicy.requireSafeDirectory("/data/local/tmp/GMO-Root-example") }.isFailure)
        assertTrue(runCatching { RootWritePolicy.requireSafeDirectory("/data/user/0/example.app/files") }.isFailure)
    }
    @Test fun writingRequiresPerFileUnlockExactOriginalAndOneUseContentApproval() = runBlocking {
        authorize(); val file = snapshot()
        assertFalse(RootWritePolicy.canWrite(target))
        assertTrue(runCatching { RootWritePolicy.approve(target, file.hash, updated) }.isFailure)
        RootWritePolicy.unlock(file)
        assertTrue(RootWritePolicy.canWrite(target))
        assertTrue(runCatching { RootWritePolicy.approve(target, "0".repeat(64), updated) }.isFailure)
        RootWritePolicy.approve(target, file.hash, updated)
        assertTrue(runCatching { RootWritePolicy.consume(target, old) }.isFailure)
        assertTrue(runCatching { RootWritePolicy.consume(target, updated) }.isFailure)
        RootWritePolicy.approve(target, file.hash, updated)
        assertEquals(file, RootWritePolicy.consume(target, updated).snapshot)
        assertFalse(RootWritePolicy.canWrite(target))
        assertTrue(runCatching { RootWritePolicy.consume(target, updated) }.isFailure)
    }
    @Test fun revokeClearsFileApprovalAndDirectoryScopeAndRejectsOldSnapshotAfterNewSession() = runBlocking {
        authorize(); val original = snapshot()
        RootWritePolicy.unlock(original); RootWritePolicy.approve(target, original.hash, updated)
        RootWritePolicy.allowDirectory("/data/local/test-work")
        assertTrue(RootWritePolicy.canManage("/data/local/test-work/a.txt"))
        UserRootSession.revokeAuthorization()
        assertFalse(RootWritePolicy.canWrite(target)); assertFalse(RootWritePolicy.canManage("/data/local/test-work/a.txt"))
        assertTrue(runCatching { RootWritePolicy.consume(target, updated) }.isFailure)
        UserRootSession.requestAuthorization()
        assertTrue(runCatching { RootWritePolicy.unlock(original) }.isFailure)
    }
    @Test fun saveProtocolValidatesReceiptAndRecordsFailedConflictWithoutAutomaticRetry() = runBlocking {
        var requests = 0
        UserRootSession.resetForTests(RootExecutor { r ->
            requests++
            if (r.entryMarker == null) RootProcessOutcome(0, "0".toByteArray())
            else RootProcessOutcome(75, stderr = "${r.entryMarker}:0\n指纹冲突，未覆盖")
        }, { "/synthetic/su" })
        UserRootSession.requestAuthorization()
        val state = snapshot(); RootWritePolicy.unlock(state); RootWritePolicy.approve(target, state.hash, updated)
        assertTrue(runCatching { RootFileIo.write(RootFileIo.location(target), updated) }.isFailure)
        assertEquals(2, requests)
        assertFalse(RootFileIo.recoveryRecords.value.last().completed)
        assertTrue(runCatching { RootFileIo.write(RootFileIo.location(target), updated) }.isFailure)
        assertEquals(2, requests)
        assertEquals(RootStatus.Granted, UserRootSession.status.value)
    }
    @Test fun successfulTypedReceiptAndOriginalBackupAreRequiredBeforeUiSuccess() = runBlocking {
        UserRootSession.resetForTests(RootExecutor { r ->
            if (r.entryMarker == null) RootProcessOutcome(0, "0".toByteArray()) else {
                val record = RootFileIo.recoveryRecords.value.last()
                assertTrue(r.command.contains("原文件身份/元数据变化"))
                RootProcessOutcome(0, "${record.backupPath}\u0000${record.newHash}\u0000".toByteArray(), "${r.entryMarker}:0\n")
            }
        }, { "/synthetic/su" })
        UserRootSession.requestAuthorization()
        val state = snapshot(); RootWritePolicy.unlock(state); RootWritePolicy.approve(target, state.hash, updated)
        RootFileIo.write(RootFileIo.location(target), updated)
        val receipt = RootFileIo.recoveryRecords.value.last()
        assertTrue(receipt.completed); assertEquals(textFingerprint(old), receipt.originalHash)
        assertTrue(receipt.backupPath.startsWith("/data/local/.gmo-recovery-"))
    }
    @Test fun transactionPinsRegularInodeBacksUpSyncsAndNeverChangesPermissionOrMountState() {
        val before = RootFileSnapshot(target, "12|34|1000|1000|600|1|5|1700000000|u:object_r:system_data_file:s0", "a".repeat(64), 0)
        val bytes = ByteArray(MAX_EDIT_TEXT_BYTES) { 'a'.code.toByte() }
        val command = rootMutationCommand(RootMutationRequest.SaveText(RootChangeApproval(before, textFingerprint(bytes)), bytes,
            "/data/local/.gmo-recovery-" + "a".repeat(32)))
        assertTrue(command.contains("exec 3<")); assertTrue(command.contains("exec 4<>"))
        assertTrue(command.contains("原文件身份/元数据变化")); assertTrue(command.contains("指纹冲突"))
        assertTrue(command.contains("sync ")); assertTrue(command.contains("保存期间路径被替换"))
        assertTrue(command.contains("备份后内容变化，未写入"))
        assertTrue(command.contains("原内容恢复并回读一致"))
        assertTrue(command.contains("无法确认原内容恢复"))
        assertTrue(command.contains("/original")); assertTrue(command.contains("/metadata")); assertTrue(command.contains("/target"))
        assertTrue(command.contains("<<'GMO_CONTENT'\n")); assertFalse(command.contains("sh -c"))
        assertFalse(Regex("(?:^|[;\\s])(chmod|chown|setenforce|mount|dd)(?:[;\\s]|$)").containsMatchIn(command))
        val framed = rootTaskScript(command, "/data/local/.gmo-io-" + "b".repeat(32), "GMO_" + "c".repeat(32), 16_384, 9000)
        assertTrue(framed.contains("/system/bin/timeout -s KILL 8"))
        assertTrue(framed.contains("head -c 16385")); assertFalse(framed.contains("ulimit -f"))
        assertTrue(framed.contains("request.sh\" <<'")); assertFalse(framed.contains("/system/bin/sh -c"))
    }
    @Test fun directoryCommandsRefuseScopeEscapeUseNoOverwriteAndNeverRecursivelyDelete() = runBlocking {
        authorize(); RootWritePolicy.allowDirectory("/data/local/test-work")
        val delete = rootMutationCommand(RootMutationRequest.Delete("/data/local/test-work/empty"))
        assertTrue(delete.contains("Root不递归操作非空目录")); assertTrue(delete.contains("/proc/self/mountinfo"))
        assertFalse(delete.contains("rm -r")); assertTrue(delete.contains("rmdir"))
        assertTrue(delete.contains("祖先目录可由shell/app改写"))
        assertTrue(delete.contains("a='/data'")); assertTrue(delete.contains("a='/data/local'"))
        val copy = rootMutationCommand(RootMutationRequest.Copy("/data/local/test-work/a.txt", "/data/local/test-work/b.txt", false))
        assertTrue(copy.contains("set -C")); assertTrue(copy.contains("8388608"))
        assertTrue(runCatching { rootMutationCommand(RootMutationRequest.Copy("/data/local/test-work/a.txt", "/system/etc/b.txt", false)) }.isFailure)
        assertTrue(runCatching { rootMutationCommand(RootMutationRequest.Create("/data/local/test-work", "x.sh", byteArrayOf())) }.isFailure)
    }
    @Test fun workspaceUsesTrustedDataLocalAndTypedWritesRejectInvalidUtf8BeforeExecution() = runBlocking {
        val workspace = rootMutationCommand(RootMutationRequest.Workspace("/data/local/GMO-Root-example"))
        assertTrue(workspace.contains("祖先目录可由shell/app改写"))
        assertTrue(runCatching { rootMutationCommand(RootMutationRequest.Workspace("/data/local/tmp/GMO-Root-example")) }.isFailure)
        var calls = 0
        UserRootSession.resetForTests(RootExecutor { calls++; RootProcessOutcome(0, "0".toByteArray()) }, { "/synthetic/su" })
        UserRootSession.requestAuthorization()
        val invalid = byteArrayOf(0xC3.toByte(), 0x28)
        RootWritePolicy.allowDirectory("/data/local/test-work")
        assertTrue(runCatching { UserRootSession.runMutationRequest(RootMutationRequest.Create("/data/local/test-work", "a.txt", invalid)) }.isFailure)
        val state = snapshot(); RootWritePolicy.unlock(state)
        assertTrue(runCatching { RootWritePolicy.approve(target, state.hash, invalid) }.isFailure)
        assertEquals(1, calls)
    }
    @Test fun rootDiffAndProtocolLinesAreBoundedAndDoNotApplyPartialPatches() {
        val difference = rootTextDifference("旧\na\nb", "新\na\nc", 1)
        assertTrue(difference.contains("共2 行")); assertTrue(difference.contains("前1 处"))
        assertTrue(difference.contains("- 旧")); assertFalse(difference.contains("+ c"))
        assertEquals("abc", boundedRootLine("abc\n".byteInputStream(), 3))
        assertTrue(runCatching { boundedRootLine("abcd\n".byteInputStream(), 3) }.isFailure)
        assertTrue(runCatching { boundedRootLine("abc".byteInputStream(), 4) }.isFailure)
    }
    @Test fun constructedApprovalCannotBypassBackendOneUseTicket() = runBlocking {
        authorize()
        val forged = RootChangeApproval(snapshot(), textFingerprint(updated))
        val attempt = runCatching { UserRootSession.runMutationRequest(RootMutationRequest.SaveText(forged, updated,
            "/data/local/.gmo-recovery-" + "a".repeat(32))) }
        assertTrue(attempt.isFailure)
        assertTrue(attempt.exceptionOrNull()!!.message!!.contains("单次保存许可"))
    }
}
