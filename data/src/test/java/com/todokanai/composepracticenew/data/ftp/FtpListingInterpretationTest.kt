package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.datasource.UnexpectedConflictResolver
import com.todokanai.composepracticenew.model.ConflictChoice
import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.repository.ConnectionLossNotices
import com.todokanai.composepracticenew.repository.FtpPathAwareFileSystem
import com.todokanai.composepracticenew.repository.OperationNotices
import com.todokanai.composepracticenew.repository.WriteConflictResolver
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
 * 화면 목록은 읽은 항목만 돌려주며 뺀 줄 수를 목록 결과에 담고, 트리를 쓰는 작업(폴더 다운로드·재귀 삭제)은 그 줄만 실패로 알리고 나머지를 처리한다.
 * MLSD의 심볼릭 링크 줄도 commons-net이 해석하지 못하는 줄이라 같은 규칙으로 실패한다(링크는 지원 범위 밖).
 */
class FtpListingInterpretationTest {

    /** 테스트의 로컬 파일을 두는 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("ftp-listing-test").toFile()
    /** 서버 응답을 정하고 보낸 명령을 기록하는 가짜 클라이언트. */
    private val client = FakeFtpClient().apply { features = setOf("MLST") }
    /** [client]를 쓰는 세션. */
    private val session = FtpSession(client, ConnectionLossNotices())
    /** 검증 대상 명령 실행기. */
    private val commands = FtpCommandExecutor(session, DataCommandRunner(session))

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
    fun `화면 목록은 해석하지 못한 줄을 빼고 읽은 항목과 뺀 줄 수를 돌려준다`() {
        // 숫자가 아닌 size는 commons-net 해석기가 예외를 던지는 줄이고, 공백이 없는 줄은 해석기가 null을 돌려주는 줄이다
        client.mlsdListings = mapOf("/r" to listOf(UNPARSEABLE_SIZE, "garbage", "type=file;size=3; a.txt", PROFTPD_LINK))

        val result = runBlocking { FtpPathAwareFileSystem(commands).listFiles("ftp://host/r") }

        assertEquals(listOf("a.txt"), (result as IoResult.Success).value.entries.map { it.name })
        assertEquals("빠진 줄 수를 목록 상태로 표시하고 이름 충돌 판정에서 빼도록 돌려줘야 함", 3, result.value.unreadableCount)
    }

    @Test
    fun `LIST 목록의 total 줄과 자신·상위 항목은 해석하지 못한 줄로 남기지 않고 뺀다`() {
        // LIST를 쓰는 서버 — 해석기가 total 줄을 거르지 않으면 모든 목록이 불완전해져 폴더 삭제·다운로드가 실패한다
        val listClient = FakeFtpClient().apply {
            listHeaderLines = listOf("total 3", "drwxr-xr-x 1 user group 0 Jan  1 00:00 .", "drwxr-xr-x 1 user group 0 Jan  1 00:00 ..")
            remoteFiles = listOf("a.txt")
        }
        val listSession = FtpSession(listClient, ConnectionLossNotices())
        runBlocking { listSession.markLoggedIn(listSession.closeRequestMark(), home = null, serverName = "server") }

        val items = (runBlocking { FtpCommandExecutor(listSession, DataCommandRunner(listSession)).listFiles("ftp://host/r") } as IoResult.Success).value

        assertEquals(listOf("a.txt"), items.map { (it as ListedItem.Entry).entry.name })
    }

    @Test
    fun `해석하지 못한 줄이 없으면 화면 목록의 뺀 줄 수는 0이다`() {
        client.mlsdListings = mapOf("/r" to listOf("type=file;size=3; a.txt"))

        val result = runBlocking { FtpPathAwareFileSystem(commands).listFiles("ftp://host/r") }

        assertEquals(0, (result as IoResult.Success).value.unreadableCount)
    }

    @Test
    fun `재귀 삭제는 해석하지 못한 줄만 실패로 알리고 그 줄을 품은 폴더는 지우지 않는다`() {
        client.mlsdListings = mapOf(
            "/r" to listOf("type=dir; sub", PROFTPD_LINK, "type=file;size=3; a.txt"),
            "/r/sub" to listOf(UNPARSEABLE_SIZE)
        )

        val result = runBlocking { commands.delete(listOf(directoryTarget("ftp://host/r"))) {} }

        assertEquals(
            IoResult.PartiallyFailed(
                "삭제 실패",
                listOf(
                    FailedItem("r/$PROFTPD_LINK", "목록 항목을 읽지 못했습니다: $PROFTPD_LINK"),
                    FailedItem("r/sub/$UNPARSEABLE_SIZE", "목록 항목을 읽지 못했습니다: $UNPARSEABLE_SIZE")
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
                    FailedItem("r/$PROFTPD_LINK", "목록 항목을 읽지 못했습니다: $PROFTPD_LINK"),
                    FailedItem("r/$UNPARSEABLE_SIZE", "목록 항목을 읽지 못했습니다: $UNPARSEABLE_SIZE")
                )
            ),
            result
        )
        assertEquals(listOf("/r/a.txt"), client.retrieveRequests)
        assertEquals("abc", File(dir, "r/a.txt").readText())
    }

    @Test
    fun `해석하지 못한 줄이 여러 개면 실패 이름은 나온 폴더가 아니라 받은 줄로 서로 구분된다`() {
        client.mlsdListings = mapOf("/r" to listOf(PROFTPD_LINK, UNPARSEABLE_SIZE))

        val result = runBlocking { commands.delete(listOf(directoryTarget("ftp://host/r"))) {} }

        assertEquals(
            "같은 폴더 이름만 반복되지 않아야 함(#347)",
            setOf("r/$PROFTPD_LINK", "r/$UNPARSEABLE_SIZE"),
            (result as IoResult.PartiallyFailed).failures.map { it.path }.toSet()
        )
    }

    @Test
    fun `건너뛰기로 한 폴더 안의 해석하지 못한 줄은 표시 이름과 무관하게 실패로 알리지 않는다`() {
        File(dir, "r/sub").mkdirs()
        client.mlsdListings = mapOf("/r" to listOf("type=dir; sub"), "/r/sub" to listOf(UNPARSEABLE_SIZE))

        val result = runBlocking {
            FtpTransferrer(session, commands, DataCommandRunner(session), OperationNotices())
                .download(listOf(DownloadSource("ftp://host/r", File(dir, "r").path, true)), WriteConflictResolver { conflict ->
                    // 받을 폴더 r은 합치고, 그 안의 sub만 건너뛴다
                    if (conflict.name == "sub") ConflictChoice.SKIP else ConflictChoice.PROCEED
                })
        }

        assertEquals("건너뛴 폴더 안인지는 줄이 나온 폴더의 자리로 판정해야 함", IoResult.Done, result)
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
