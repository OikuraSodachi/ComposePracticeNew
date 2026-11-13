package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.datasource.UnexpectedConflictResolver
import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.OperationNoticeKind
import com.todokanai.composepracticenew.repository.FtpPathAwareFileSystem
import com.todokanai.composepracticenew.repository.OperationNoticeReporter
import com.todokanai.composepracticenew.repository.OperationNotices
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * FTP 목록에서 해석하지 못한 줄을 버리지 않고 다루는 규칙(#255)을 검증하는 단위 테스트.
 * 화면 목록은 읽은 항목만 보여 주며 빠진 줄을 알리고, 트리를 쓰는 작업(폴더 다운로드·재귀 삭제)은 그 줄만 실패로 알리고 나머지를 처리한다.
 * MLSD의 심볼릭 링크 줄도 commons-net이 해석하지 못하는 줄이라 같은 규칙으로 실패한다(링크는 지원 범위 밖).
 */
class FtpListingInterpretationTest {

    /** 테스트의 로컬 파일을 두는 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("ftp-listing-test").toFile()
    /** 서버 응답을 정하고 보낸 명령을 기록하는 가짜 클라이언트. */
    private val client = FakeFtpClient().apply { features = setOf("MLST") }
    /** [client]를 쓰는 세션. */
    private val session = FtpSession(client)
    /** 검증 대상 명령 실행기. */
    private val commands = FtpCommandExecutor(session, DataCommandRunner(session))
    /** 화면 목록이 넣은 실패 메시지를 기록하는 저장소. */
    private val notices = RecordingNotices()

    @Before
    fun setUp() {
        runBlocking { session.markLoggedIn(session.closeRequestMark(), home = null, serverName = "server") }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `해석하지 못한 줄과 MLSD 링크 줄은 버리지 않고 받은 순서대로 남긴다`() {
        client.mlsdListings = mapOf("/r" to listOf(PROFTPD_LINK, PURE_FTPD_LINK, UNPARSEABLE_SIZE, "type=file;size=3; a.txt"))

        val items = (runBlocking { commands.listFiles("ftp://host/r") } as IoResult.Success).value

        assertEquals(
            listOf("ftp://host/r" to PROFTPD_LINK, "ftp://host/r" to PURE_FTPD_LINK, "ftp://host/r" to UNPARSEABLE_SIZE),
            items.filterIsInstance<ListedItem.Unreadable>().map { it.dirPath to it.line }
        )
        assertEquals(listOf("a.txt"), items.filterIsInstance<ListedItem.Entry>().map { it.entry.name })
    }

    @Test
    fun `화면 목록은 해석하지 못한 줄을 빼고 읽은 항목을 보여 주며 빠진 줄을 알린다`() {
        // 숫자가 아닌 size는 commons-net 해석기가 예외를 던지는 줄이고, 공백이 없는 줄은 해석기가 null을 돌려주는 줄이다
        client.mlsdListings = mapOf("/r" to listOf(UNPARSEABLE_SIZE, "garbage", "type=file;size=3; a.txt", PROFTPD_LINK))

        val result = runBlocking { FtpPathAwareFileSystem(commands, notices).listFiles("ftp://host/r") }

        assertEquals(listOf("a.txt"), (result as IoResult.Success).value.map { it.name })
        assertEquals(listOf("/r: 목록 항목을 읽지 못했습니다: $UNPARSEABLE_SIZE 외 2개 — 읽은 항목만 표시합니다"), notices.failures)
    }

    @Test
    fun `해석하지 못한 줄이 없으면 화면 목록은 알리지 않는다`() {
        client.mlsdListings = mapOf("/r" to listOf("type=file;size=3; a.txt"))

        runBlocking { FtpPathAwareFileSystem(commands, notices).listFiles("ftp://host/r") }

        assertTrue(notices.failures.isEmpty())
    }

    @Test
    fun `재귀 삭제는 해석하지 못한 줄만 실패로 알리고 그 줄을 품은 폴더는 지우지 않는다`() {
        client.mlsdListings = mapOf(
            "/r" to listOf("type=dir; sub", PROFTPD_LINK, "type=file;size=3; a.txt"),
            "/r/sub" to listOf(UNPARSEABLE_SIZE)
        )

        val result = runBlocking { commands.removeDirectoryRecursive("ftp://host/r") }

        assertEquals(
            IoResult.PartiallyFailed(
                "하위 항목 삭제 실패",
                listOf(
                    FailedItem("", "목록 항목을 읽지 못했습니다: $PROFTPD_LINK"),
                    FailedItem("sub", "목록 항목을 읽지 못했습니다: $UNPARSEABLE_SIZE")
                )
            ),
            result
        )
        assertEquals("읽은 파일은 지워야 함", listOf("/r/a.txt"), client.deleteRequests)
        assertTrue("비울 수 없는 폴더에는 RMD를 보내지 않아야 함", client.removeDirectoryRequests.isEmpty())
    }

    @Test
    fun `폴더 다운로드는 해석하지 못한 줄만 실패로 알리고 나머지를 받는다`() {
        client.mlsdListings = mapOf("/r" to listOf(PROFTPD_LINK, UNPARSEABLE_SIZE, "type=file;size=3; a.txt"))
        client.streamContent = "abc".toByteArray()

        val result = runBlocking {
            FtpTransferrer(session, commands, DataCommandRunner(session), OperationNotices())
                .download(listOf(DownloadSource("ftp://host/r", File(dir, "r").path, true)), UnexpectedConflictResolver)
        }

        assertEquals(
            IoResult.PartiallyFailed(
                "다운로드 실패",
                listOf(
                    FailedItem("r", "목록 항목을 읽지 못했습니다: $PROFTPD_LINK"),
                    FailedItem("r", "목록 항목을 읽지 못했습니다: $UNPARSEABLE_SIZE")
                )
            ),
            result
        )
        assertEquals(listOf("/r/a.txt"), client.retrieveRequests)
        assertEquals("abc", File(dir, "r/a.txt").readText())
    }

    /** 넣은 실패 메시지를 넣은 순서대로 기록하는 테스트용 메시지 저장소. */
    private class RecordingNotices : OperationNoticeReporter {
        /** 넣은 실패 메시지. */
        val failures = mutableListOf<String>()

        override fun report(kind: OperationNoticeKind, message: String) {
            if (kind == OperationNoticeKind.FAILURE) failures += message
        }
    }

    private companion object {
        /** ProFTPD 기본 설정이 심볼릭 링크에 보내는 줄. */
        const val PROFTPD_LINK = "type=OS.unix=symlink;modify=20240101000000; ln1"
        /** Pure-FTPd 기본 설정이 심볼릭 링크에 보내는 줄(대상 경로 포함). */
        const val PURE_FTPD_LINK = "type=OS.unix=slink:/home/u;modify=20240101000000; ln2"
        /** commons-net 해석기가 예외를 던지는(숫자가 아닌 size) 줄. */
        const val UNPARSEABLE_SIZE = "type=file;size=abc; bad.txt"
    }
}
