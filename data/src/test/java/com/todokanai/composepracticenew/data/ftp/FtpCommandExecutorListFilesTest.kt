package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

/**
 * 화면 목록 조회([FtpCommandExecutor.listFiles])가 실패를 빈 목록으로 바꾸지 않고 실패 결과로 알리며,
 * 작업용 트리 수집과 같은 방식(MLST 서버는 MLSD)으로 조회하는지 검증하는 단위 테스트(#215).
 * 목록 데이터를 받다 난 실패는 그 폴더만의 실패이고, 완료 응답을 읽지 못한 실패만 세션 종료인지도 검증한다(#282 리뷰).
 * 서버 응답 코드·연결 끊김별 결과는 [FtpCommandContractTest]가 검증한다.
 */
class FtpCommandExecutorListFilesTest {

    private val client = FakeFtpClient()
    private val session = FtpSession(client)
    private val commands = FtpCommandExecutor(session)

    @Test
    fun `MLST 서버는 화면 목록도 MLSD로 조회하며 숨김 파일을 포함한다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf("/r" to listOf("type=cdir; /r", "type=file;size=3; .hidden", "type=dir; sub"))

        val entries = runBlocking { commands.listFiles("ftp://host/r") } as IoResult.Success

        assertEquals(listOf("ftp://host/r/.hidden", "ftp://host/r/sub"), entries.value.map { it.path })
        assertEquals(listOf("MLSD /r"), client.listRequests)
    }

    @Test
    fun `MLSD 빈 폴더는 실패가 아닌 빈 목록이다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf("/r" to emptyList())

        assertEquals(IoResult.Success(emptyList<FileEntry>()), runBlocking { commands.listFiles("ftp://host/r") })
    }

    @Test
    fun `LIST의 450은 응답 문구와 관계없이 빈 목록이 아닌 실패다`() {
        loggedIn()
        client.serverBehaviors = mapOf("LIST" to ServerBehavior.Refuse(450, "450 No files found"))

        assertEquals(IoResult.ItemFailed("LIST 실패: 450 No files found"), runBlocking { commands.listFiles("ftp://host/r") })
    }

    @Test
    fun `LIST가 빈 목록을 보내고 226으로 끝나면 빈 목록이다`() {
        loggedIn()

        assertEquals(IoResult.Success(emptyList<FileEntry>()), runBlocking { commands.listFiles("ftp://host/r") })
    }

    @Test
    fun `LIST가 목록을 보내기 시작한 뒤 450으로 끝나면 받은 항목이 있어도 실패다`() {
        loggedIn()
        client.remoteFiles = listOf("a.txt")
        client.serverBehaviors = mapOf("LIST" to ServerBehavior.RefuseAfterData(450, "450 Transfer aborted"))

        assertEquals(IoResult.ItemFailed("LIST 실패: 450 Transfer aborted"), runBlocking { commands.listFiles("ftp://host/r") })
    }

    @Test
    fun `트리 수집에서 LIST의 450은 빈 폴더가 아닌 실패다`() {
        loggedIn()
        client.serverBehaviors = mapOf("LIST" to ServerBehavior.Refuse(450, "450 File busy"))

        assertEquals(IoResult.ItemFailed("LIST 실패: 450 File busy"), runBlocking { commands.collectRemoteTree("ftp://host/r") })
    }

    @Test
    fun `LIST의 550은 없는 경로·권한 없음과 구분할 수 없으므로 빈 목록이 아닌 실패다`() {
        loggedIn()
        client.serverBehaviors = mapOf("LIST" to ServerBehavior.Refuse(550, "550 No files found"))

        assertEquals(IoResult.ItemFailed("LIST 실패: 550 No files found"), runBlocking { commands.listFiles("ftp://host/r") })
    }

    @Test
    fun `MLSD 목록 데이터를 받다 실패하면 완료 응답까지 읽고 그 폴더만 실패하며 세션은 유지한다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf("/r" to listOf("type=file;size=3; a.txt"))
        client.listingReadFailure = SocketTimeoutException("Read timed out")

        val result = runBlocking { commands.listFiles("ftp://host/r") }

        assertEquals(IoResult.ItemFailed("MLSD 실패: Read timed out"), result)
        assertEquals(1, client.listingCompletionCalls)
        assertTrue(runBlocking { session.isReady() })
    }

    @Test
    fun `목록을 받은 뒤 완료 응답을 읽지 못하면 응답 순서를 알 수 없어 세션을 끝낸다`() {
        loggedIn()
        client.remoteFiles = listOf("a.txt")
        client.listingCompletionFailure = SocketTimeoutException("Read timed out")

        val result = runBlocking { commands.listFiles("ftp://host/r") }

        assertTrue(result is IoResult.ConnectionLost)
        assertEquals(1, client.disconnectCalls)
    }

    /** 세션을 로그인 완료 상태로 만든다. */
    private fun loggedIn() = runBlocking { session.markLoggedIn(session.closeRequestMark(), home = null, serverName = "server") }
}
