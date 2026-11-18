package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.data.progress.IndeterminateProgressReporter
import com.todokanai.composepracticenew.data.progress.PreparationProgressReporter
import com.todokanai.composepracticenew.data.progress.ProgressThrottle
import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.RemoteStep
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.IndeterminatePhase
import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

/**
 * 트리 수집([FtpTreeCollector.collectRemoteTree])이 받은 항목마다 준비 단계 진행을 알려 그 보고에서 취소를 받고,
 * 삭제([FtpRemoteDeletion.delete])가 모든 대상을 먼저 모은 뒤 작업 전체의 항목 수 기준으로 진행을 보고하고, 대상별 실패를 모아 알리는지 검증하는 단위 테스트(#284).
 */
class FtpTreeProgressTest {

    private val client = FakeFtpClient()
    private val session = FtpSession(client)
    private val commands = FtpOperationParts(session)

    @Test
    fun `수집하는 동안 받은 항목마다 찾은 항목 수를 알린다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf(
            "/r" to listOf("type=dir; sub", "type=file;size=1; a.txt"),
            "/r/sub" to listOf("type=file;size=1; b.txt")
        )
        val reported = mutableListOf<ProgressState>()

        runBlocking { commands.trees.collectRemoteTree("ftp://host/r", everyEntry { reported += it }) }

        assertEquals(
            listOf(IndeterminatePhase.Preparing(1, "sub"), IndeterminatePhase.Preparing(2, "b.txt"), IndeterminatePhase.Preparing(3, "a.txt")),
            reported.map { it.indeterminate }
        )
    }

    @Test
    fun `수집 중 진행 보고가 취소를 알리면 남은 폴더를 조회하지 않고 멈춘다`() {
        // 잠금을 바로 얻으면 취소를 확인하지 않으므로, 수집 중 취소는 진행 보고로만 받는다
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf(
            "/r" to listOf("type=dir; sub"),
            "/r/sub" to listOf("type=file;size=1; b.txt")
        )

        assertThrows(CancellationException::class.java) {
            runBlocking { commands.trees.collectRemoteTree("ftp://host/r", everyEntry { throw CancellationException("cancelled") }) }
        }
        assertEquals(listOf("MLSD /r"), client.listRequests)
    }

    @Test
    fun `재귀 삭제는 지우는 동안 처리한 항목 수를 알리고 시작 폴더를 지우기 전까지 100%를 알리지 않는다`() {
        // 100%는 작업이 끝났다는 뜻이다 — 진행 다이얼로그는 100%인 작업을 끝난 작업으로 본다
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf("/r" to listOf("type=file;size=1; a.txt", "type=file;size=1; b.txt"))
        val deleting = mutableListOf<ProgressState>()

        runBlocking { commands.deletion.delete(listOf(directoryTarget("ftp://host/r"))) { if (it.indeterminate == null) deleting += it } }

        assertEquals(listOf(0 to "b.txt", 1 to "a.txt", 2 to "r"), deleting.map { it.currentIndex to it.currentFileName })
        assertTrue(deleting.all { it.listSize == 3 && it.progress!! < 100 })
    }

    @Test
    fun `여러 대상을 지우면 모든 대상을 먼저 모으고 작업 전체의 항목 수 기준으로 진행을 알린다`() {
        // 대상마다 진행을 다시 세면 파일 여럿을 지우는 동안 0 / 1에 머문다
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf("/r" to listOf("type=file;size=1; a.txt"))
        val reported = mutableListOf<ProgressState>()

        runBlocking { commands.deletion.delete(listOf(fileTarget("ftp://host/x.txt"), directoryTarget("ftp://host/r"))) { reported += it } }

        val firstDeletion = reported.indexOfFirst { it.indeterminate == null }
        assertEquals(IndeterminatePhase.Preparing(1, "x.txt"), reported.first().indeterminate)
        assertTrue(reported.take(firstDeletion).all { it.indeterminate != null })
        assertEquals(
            listOf(Triple(0, 3, "x.txt"), Triple(1, 3, "a.txt"), Triple(2, 3, "r")),
            reported.drop(firstDeletion).map { Triple(it.currentIndex, it.listSize, it.currentFileName) }
        )
    }

    @Test
    fun `여러 대상 중 일부가 실패해도 나머지를 지우고 대상 이름 아래 실패를 모아 알린다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf("/r" to listOf("type=dir; sub"), "/r/sub" to listOf("type=file;size=1; x.txt"))
        client.onDeleteFile = { path -> path != "/a.txt" && path != "/r/sub/x.txt" }

        val result = runBlocking { commands.deletion.delete(listOf(fileTarget("ftp://host/a.txt"), directoryTarget("ftp://host/r"), fileTarget("ftp://host/ok.txt"))) {} }

        assertEquals(
            IoResult.PartiallyFailed(listOf(FailedItem("a.txt", FailureReason.ServerRefused(RemoteStep.DELETE_FILE, "550 Permission denied")), FailedItem("r/sub/x.txt", FailureReason.ServerRefused(RemoteStep.DELETE_FILE, "550 Permission denied")))),
            result
        )
        assertEquals(listOf("/a.txt", "/r/sub/x.txt", "/ok.txt"), client.deleteRequests)
    }

    @Test
    fun `트리를 모으지 못한 대상은 지우지 않고 실패로 알리며 다른 대상은 지운다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf("/ok" to emptyList())

        val result = runBlocking { commands.deletion.delete(listOf(directoryTarget("ftp://host/missing"), directoryTarget("ftp://host/ok"))) {} }

        assertEquals(listOf("missing"), (result as IoResult.PartiallyFailed).failures.map { it.path })
        assertEquals(listOf("/ok"), client.removeDirectoryRequests)
    }

    @Test
    fun `여러 대상을 지우는 중 연결이 끊기면 남은 대상을 시도하지 않는다`() {
        loggedIn()
        client.onDeleteFile = { throw SocketTimeoutException("Read timed out") }

        val result = runBlocking { commands.deletion.delete(listOf(fileTarget("ftp://host/a.txt"), fileTarget("ftp://host/b.txt"))) {} }

        assertTrue("$result", result is IoResult.ConnectionLost)
        assertEquals(listOf("/a.txt"), client.deleteRequests)
    }

    @Test
    fun `파일 삭제는 명령을 보내기 전에 진행을 알려 그 보고에서 취소를 받는다`() {
        loggedIn()
        val reporter: (ProgressState) -> Unit = { if (it.indeterminate == null) throw CancellationException("cancelled") }

        assertThrows(CancellationException::class.java) {
            runBlocking { commands.deletion.delete(listOf(fileTarget("ftp://host/r/a.txt")), reporter) }
        }
        assertEquals(emptyList<String>(), client.deleteRequests)
    }

    /** 찾은 항목마다 [onProgress]로 알리는 준비 단계 보고기 — 기본 보고 간격이면 테스트가 시간에 기대게 된다. */
    private fun everyEntry(onProgress: (ProgressState) -> Unit) = PreparationProgressReporter(IndeterminateProgressReporter(onProgress, ProgressThrottle(intervalNanos = 0)))

    private fun loggedIn() = runBlocking { session.markLoggedIn(home = null) }
}
