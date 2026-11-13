package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.data.progress.PreparationProgressReporter
import com.todokanai.composepracticenew.data.progress.ProgressThrottle
import com.todokanai.composepracticenew.model.PreparationProgress
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.repository.ConnectionLossNotices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 트리 수집([FtpCommandExecutor.collectRemoteTree])이 받은 항목마다 준비 단계 진행을 알려 그 보고에서 취소를 받고,
 * 재귀 삭제([FtpCommandExecutor.removeDirectoryRecursive])가 지우는 동안 항목 수 기준 진행을 보고하는지 검증하는 단위 테스트(#284).
 */
class FtpTreeProgressTest {

    private val client = FakeFtpClient()
    private val session = FtpSession(client, ConnectionLossNotices())
    private val commands = FtpCommandExecutor(session, DataCommandRunner(session))

    @Test
    fun `수집하는 동안 받은 항목마다 찾은 항목 수를 알린다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf(
            "/r" to listOf("type=dir; sub", "type=file;size=1; a.txt"),
            "/r/sub" to listOf("type=file;size=1; b.txt")
        )
        val reported = mutableListOf<ProgressState>()

        runBlocking { commands.collectRemoteTree("ftp://host/r", everyEntry { reported += it }) }

        assertEquals(
            listOf(PreparationProgress(1, "sub"), PreparationProgress(2, "b.txt"), PreparationProgress(3, "a.txt")),
            reported.map { it.preparing }
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
            runBlocking { commands.collectRemoteTree("ftp://host/r", everyEntry { throw CancellationException("cancelled") }) }
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

        runBlocking { commands.removeDirectoryRecursive("ftp://host/r") { if (it.preparing == null) deleting += it } }

        assertEquals(listOf(0 to "b.txt", 1 to "a.txt", 2 to "r"), deleting.map { it.currentIndex to it.currentFileName })
        assertTrue(deleting.all { it.listSize == 3 && it.progress!! < 100 })
    }

    @Test
    fun `파일 삭제는 명령을 보내기 전에 진행을 알려 그 보고에서 취소를 받는다`() {
        loggedIn()

        assertThrows(CancellationException::class.java) {
            runBlocking { commands.deleteFile("ftp://host/r/a.txt") { throw CancellationException("cancelled") } }
        }
        assertEquals(emptyList<String>(), client.deleteRequests)
    }

    /** 찾은 항목마다 [onProgress]로 알리는 준비 단계 보고기 — 기본 보고 간격이면 테스트가 시간에 기대게 된다. */
    private fun everyEntry(onProgress: (ProgressState) -> Unit) = PreparationProgressReporter(onProgress, ProgressThrottle(intervalNanos = 0))

    private fun loggedIn() = runBlocking { session.markLoggedIn(session.closeRequestMark(), home = null, serverName = "server") }
}
