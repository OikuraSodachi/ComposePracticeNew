package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * LIST 서버의 목록 조회가 세션을 조회한 폴더 안에 남겨도, 그 폴더나 상위 폴더를 지우거나 옮기는 명령은 먼저 대상 밖으로 나간 뒤 보내는지 검증하는 단위 테스트.
 * 대상 밖에 있으면 나가지 않아 명령 왕복이 늘지 않는다.
 */
class FtpLeaveTargetTest {

    private val client = FakeFtpClient()
    private val session = FtpSession(client)
    private val commands = FtpCommandExecutor(session)

    @Test
    fun `폴더를 지우면 트리 수집이 들어간 폴더에서 상위 폴더로 한 번 나간 뒤 RMD를 보낸다`() {
        loggedIn()
        client.remoteFiles = listOf("a.txt")

        val result = runBlocking { commands.removeDirectoryRecursive("ftp://host/r/x") }

        assertEquals(IoResult.Done, result)
        assertEquals(listOf("CWD /r/x", "LIST", "CWD /r"), client.listRequests)
        assertEquals(listOf("CWD", "RMD"), client.sentCommands.takeLast(2))
        assertEquals(listOf("/r/x"), client.removeDirectoryRequests)
    }

    @Test
    fun `대상 밖으로 나가는 이동이 거부되면 RMD를 보내지 않고 이동 실패로 알린다`() {
        loggedIn()
        client.remoteFiles = emptyList()
        client.onChangeWorkingDirectory = { path -> path != "/r" }

        val result = runBlocking { commands.removeDirectoryRecursive("ftp://host/r/x") }

        assertEquals(IoResult.ItemFailed("CWD 실패: 550 Permission denied"), result)
        assertEquals(emptyList<String>(), client.removeDirectoryRequests)
    }

    @Test
    fun `방금 연 폴더의 이름을 바꾸면 상위 폴더로 나간 뒤 RENAME을 보낸다`() {
        loggedIn()
        runBlocking { commands.listFiles("ftp://host/r/x") }

        val result = runBlocking { commands.rename("ftp://host/r/x", "ftp://host/r/y") }

        assertEquals(IoResult.Done, result)
        assertEquals("CWD /r", client.listRequests.last())
        assertEquals(listOf("CWD", "RENAME"), client.sentCommands.takeLast(2))
    }

    @Test
    fun `세션이 대상 밖에 있으면 이름을 바꿀 때 디렉터리를 옮기지 않는다`() {
        loggedIn()
        runBlocking { commands.listFiles("ftp://host/r") }

        runBlocking { commands.rename("ftp://host/r/a.txt", "ftp://host/r/b.txt") }

        assertEquals(listOf("CWD /r", "LIST"), client.listRequests)
    }

    @Test
    fun `MLSD 서버는 목록 조회가 디렉터리를 옮기지 않으므로 폴더를 지울 때도 옮기지 않는다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf("/r/x" to listOf("type=file;size=1; a.txt"))

        runBlocking { commands.removeDirectoryRecursive("ftp://host/r/x") }

        assertFalse(client.listRequests.toString(), client.listRequests.any { it.startsWith("CWD") })
    }

    /** 세션을 로그인 완료 상태로 만든다. 로그인 직후 세션은 홈에 있다. */
    private fun loggedIn() = runBlocking { session.markLoggedIn() }
}
