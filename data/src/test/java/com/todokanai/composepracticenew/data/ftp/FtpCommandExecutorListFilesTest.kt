package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.apache.commons.net.ftp.parser.ParserInitializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 화면 목록 조회([FtpCommandExecutor.listFiles])가 실패를 빈 목록으로 바꾸지 않고 실패 결과로 알리며,
 * 작업용 트리 수집과 같은 방식(MLST 서버는 MLSD)으로 조회하는지 검증하는 단위 테스트(#215).
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

        assertEquals(IoResult.Success(emptyList<Any>()), runBlocking { commands.listFiles("ftp://host/r") })
    }

    @Test
    fun `빈 폴더에 450 No files found를 주는 LIST 서버에서 빈 폴더는 빈 목록이다`() {
        loggedIn()
        client.serverBehaviors = mapOf("LIST" to ServerBehavior.Refuse(450, "450 No files found"))

        assertEquals(IoResult.Success(emptyList<Any>()), runBlocking { commands.listFiles("ftp://host/r") })
    }

    @Test
    fun `LIST가 목록을 보내기 시작한 뒤 450으로 끝나면 받은 항목이 있어도 빈 목록이 아닌 실패다`() {
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
    fun `목록 형식을 해석할 수 없는 서버는 항목 실패로 알리고 세션을 끊지 않는다`() {
        loggedIn()
        client.serverBehaviors = mapOf("LIST" to ServerBehavior.Fail(ParserInitializationException("Unknown parser type: UNKNOWN Type: L8")))

        val result = runBlocking { commands.listFiles("ftp://host/r") }

        assertEquals(IoResult.ItemFailed("LIST 실패: 서버 목록 형식을 해석할 수 없습니다"), result)
        assertEquals(0, client.disconnectCalls)
        assertTrue(runBlocking { session.isReady() })
    }

    /** 세션을 로그인 완료 상태로 만든다. */
    private fun loggedIn() = runBlocking { session.markLoggedIn() }
}
