package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 작업용 트리 수집([FtpCommandExecutor.collectRemoteTree])이 MLSD 지원 서버에서는 MLSD로 빈 폴더와 조회 실패를 구분하고,
 * 자신·상위 디렉터리 항목(type=cdir/pdir)을 걸러내며, 미지원 서버에서는 LIST를 쓰고 오류 응답을 실패 결과로 보는지 검증하는 단위 테스트.
 */
class FtpCommandExecutorTreeListingTest {

    private val client = FakeFtpClient()
    private val session = FtpSession(client)
    private val commands = FtpCommandExecutor(session)

    @Test
    fun `MLSD 서버에서 빈 하위 폴더는 실패가 아닌 빈 폴더로 수집하고 자신·상위 항목은 걸러낸다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf(
            // 서버에 따라 cdir·pdir 이름이 "."이 아닌 전체 경로로 온다
            "/r" to listOf("type=cdir; /r", "type=pdir; /", "type=dir; empty", "type=file;size=3; a.txt"),
            "/r/empty" to listOf("type=cdir; /r/empty", "type=pdir; /r")
        )

        val tree = (runBlocking { commands.collectRemoteTree("ftp://host/r") } as IoResult.Success).value

        assertEquals(listOf("ftp://host/r/empty" to true, "ftp://host/r/a.txt" to false), tree.map { it.path to it.isDirectory })
        assertEquals(listOf("MLSD /r", "MLSD /r/empty"), client.listRequests)
    }

    @Test
    fun `MLSD 서버에서 하위 폴더 조회가 550이면 일부만 수집하지 않고 실패를 알린다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf("/r" to listOf("type=dir; locked"))

        val result = runBlocking { commands.collectRemoteTree("ftp://host/r") }

        assertEquals(IoResult.ItemFailed("MLSD 실패: 550 No such directory"), result)
        assertTrue("서버 오류 응답이므로 세션은 유지돼야 함", runBlocking { session.isReady() })
    }

    @Test
    fun `MLSD 서버에서 빈 하위 폴더가 있는 폴더를 삭제할 수 있다`() {
        loggedIn()
        client.features = setOf("MLST")
        client.mlsdListings = mapOf(
            "/r" to listOf("type=dir; empty", "type=file;size=3; a.txt"),
            "/r/empty" to emptyList()
        )

        assertEquals(IoResult.Done, runBlocking { commands.removeDirectoryRecursive("ftp://host/r") })

        assertEquals(listOf("/r/a.txt"), client.deleteRequests)
    }

    @Test
    fun `MLST를 알리지 않는 서버는 LIST를 쓰고 오류 응답을 실패로 본다`() {
        loggedIn()
        client.serverBehaviors = mapOf("LIST" to ServerBehavior.Refuse(550, "550 No such directory"))

        val result = runBlocking { commands.collectRemoteTree("ftp://host/r") }

        assertEquals(IoResult.ItemFailed("LIST 실패: 550 No such directory"), result)
        assertEquals(listOf("CWD /r", "LIST"), client.listRequests)
    }

    @Test
    fun `LIST 서버에서 디렉터리 이동이 거부되면 LIST를 보내지 않고 실패로 본다`() {
        loggedIn()
        client.serverBehaviors = mapOf("CWD" to ServerBehavior.Refuse(550, "550 No such directory"))

        val result = runBlocking { commands.collectRemoteTree("ftp://host/r") }

        assertEquals(IoResult.ItemFailed("CWD 실패: 550 No such directory"), result)
        assertEquals(listOf("CWD /r"), client.listRequests)
    }

    /** 세션을 로그인 완료 상태로 만든다. */
    private fun loggedIn() = runBlocking { session.markLoggedIn(session.closeRequestMark()) }
}
