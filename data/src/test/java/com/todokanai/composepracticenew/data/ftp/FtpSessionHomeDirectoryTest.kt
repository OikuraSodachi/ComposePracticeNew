package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.ConnectResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 세션을 열 때([FtpSession.open]) 로그인 직후의 현재 디렉터리를 세션의 홈과 현재 디렉터리로 기록하는지 검증하는 통합 테스트.
 * 홈은 대상 밖으로 나가는 이동이 거부될 때 다시 시도할 곳이다([FtpCommandRunner.leavingTarget]). PWD 응답 해석은 commons-net 동작이므로 실제 FTPClient로 로컬 테스트 서버에 접속한다.
 */
class FtpSessionHomeDirectoryTest {

    /** 세션 관리자로 테스트 서버에 접속시키는 도구. */
    private val fixture = SessionManagerFixture()

    @After
    fun tearDown() = fixture.close()

    @Test
    fun `로그인 직후의 현재 디렉터리를 세션의 홈과 현재 디렉터리로 기록한다`() {
        val result = fixture.connect(fixture.server(mapOf("PWD" to { it.send("257 \"/home/u\" is the current directory") })))

        assertEquals(ConnectResult.Connected, result)
        assertEquals("/home/u", fixture.clients.single().homeDirectory)
        assertEquals("/home/u", fixture.clients.single().workingDirectory)
    }

    @Test
    fun `서버가 PWD를 거부해도 연결하고 홈은 모르는 채로 둔다`() {
        val result = fixture.connect(fixture.server(mapOf("PWD" to { it.send("502 Command not implemented") })))

        assertEquals(ConnectResult.Connected, result)
        assertNull(fixture.clients.single().homeDirectory)
        assertNull(fixture.clients.single().workingDirectory)
    }

    @Test
    fun `다시 연결하면 새 세션이 새 연결의 홈을 현재 디렉터리로 기록한다`() {
        val server = fixture.server(mapOf("PWD" to { it.send("257 \"/home/${it.connectionIndex}\" is the current directory") }))
        fixture.connect(server)
        runBlocking { fixture.manager.disconnect() }

        val result = fixture.connect(server)

        assertEquals(ConnectResult.Connected, result)
        assertNotSame("다시 연결하면 이전 세션의 클라이언트를 다시 쓰지 않는다", fixture.clients[0], fixture.clients[1])
        assertEquals("/home/1", fixture.clients[1].homeDirectory)
        assertEquals("/home/1", fixture.clients[1].workingDirectory)
    }
}
