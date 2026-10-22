package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.RemoteConnectionLostException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 화면 목록 조회([FtpCommandExecutor.listFiles])가 실패를 빈 목록으로 바꾸지 않고 예외로 알리며,
 * 작업용 트리 수집과 같은 방식(MLST 서버는 MLSD)으로 조회하는지 검증하는 단위 테스트(#215).
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

        val entries = runBlocking { commands.listFiles("ftp://host/r") }

        assertEquals(listOf("ftp://host/r/.hidden", "ftp://host/r/sub"), entries.map { it.path })
        assertEquals(listOf("MLSD /r"), client.listRequests)
    }

    @Test
    fun `MLSD 빈 폴더는 실패가 아닌 빈 목록이다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf("/r" to emptyList())

        assertEquals(emptyList<Any>(), runBlocking { commands.listFiles("ftp://host/r") })
    }

    @Test
    fun `MLSD 조회가 550이면 빈 목록 대신 서버 응답을 담은 실패를 던지고 세션은 유지한다`() {
        loggedIn()
        client.features = setOf("MLST")

        val error = assertThrows(FtpItemFailure::class.java) {
            runBlocking { commands.listFiles("ftp://host/missing") }
        }

        assertEquals("MLSD 실패: 550 No such directory", error.message)
        assertTrue("서버 오류 응답이므로 세션은 유지돼야 함", runBlocking { session.isReady() })
    }

    @Test
    fun `LIST 조회가 오류 응답이면 빈 목록 대신 실패를 던진다`() {
        loggedIn()
        client.listRefused = true

        val error = assertThrows(FtpItemFailure::class.java) {
            runBlocking { commands.listFiles("ftp://host/r") }
        }

        assertEquals("LIST 실패: 550 No such directory", error.message)
    }

    @Test
    fun `목록 형식을 해석할 수 없는 서버는 항목 실패로 알리고 세션을 끊지 않는다`() {
        loggedIn()
        client.listParserUnavailable = true

        val error = assertThrows(FtpItemFailure::class.java) {
            runBlocking { commands.listFiles("ftp://host/r") }
        }

        assertEquals("LIST 실패: 서버 목록 형식을 해석할 수 없습니다", error.message)
        assertEquals(0, client.disconnectCalls)
        assertTrue(runBlocking { session.isReady() })
    }

    @Test
    fun `연결되어 있지 않으면 빈 목록 대신 연결 끊김을 던진다`() {
        assertThrows(RemoteConnectionLostException::class.java) {
            runBlocking { commands.listFiles("ftp://host/r") }
        }
    }

    /** 세션을 로그인 완료 상태로 만든다. */
    private fun loggedIn() = runBlocking { session.markLoggedIn() }
}
