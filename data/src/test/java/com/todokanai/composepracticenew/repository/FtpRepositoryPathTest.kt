package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.datasource.UnexpectedLeftoverDiscarder
import com.todokanai.composepracticenew.data.ftp.ScriptedFtpServer
import com.todokanai.composepracticenew.data.ftp.SessionManagerFixture
import com.todokanai.composepracticenew.datasource.UnexpectedConflictResolver
import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.RemoteSession
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

/**
 * FTP 저장소가 "디렉터리 + 이름"으로 받은 목적지를 그 디렉터리 아래(이름 변경은 같은 디렉터리 안)의 경로로 만들어
 * 서버·로컬 파일에 요청하는지 검증하는 테스트(#228). 이름 변경·새 폴더는 탐색 세션, 전송은 작업 세션으로 보낸다(#436).
 */
class FtpRepositoryPathTest {

    /** 받은 파일을 저장하고 올릴 파일을 두는 로컬 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("ftp-repository-path-test").toFile()
    /** 세션 관리자로 테스트 서버에 접속시키는 도구. */
    private val fixture = SessionManagerFixture()
    /** 서버가 받은 (명령, 인자). 받은 순서대로 쌓는다. */
    private val requests = CopyOnWriteArrayList<Pair<String, String>>()
    /** 경로 인자를 기록하며 받아들이는 서버. */
    private val server = fixture.server(
        mapOf(
            "RNFR" to { it.record("RNFR"); it.send("350 Ready for RNTO") },
            "RNTO" to { it.record("RNTO"); it.send("250 Renamed") },
            "MKD" to { it.record("MKD"); it.send("257 Created") },
            "STOR" to { exchange ->
                exchange.record("STOR")
                exchange.data.accept().use { socket ->
                    exchange.send("150 Ok to send data")
                    socket.getInputStream().readBytes()
                }
                exchange.send("226 Transfer complete")
            },
            "RETR" to { exchange ->
                exchange.record("RETR")
                exchange.data.accept().use { socket ->
                    exchange.send("150 Opening data connection")
                    socket.getOutputStream().write("data".toByteArray())
                }
                exchange.send("226 Transfer complete")
            },
        )
    )
    /** 검증 대상. */
    private val repo = FtpRepositoryImpl(fixture.manager, OperationNotices(), UnexpectedLeftoverDiscarder)
    /** 연결한 탐색 세션의 표지. */
    private val session: RemoteSession = run {
        assertEquals(ConnectResult.Connected, fixture.connect(server))
        repo.current()!!
    }

    @After
    fun tearDown() {
        fixture.close()
        dir.deleteRecursively()
    }

    @Test
    fun `이름 변경은 같은 디렉터리 안의 새 이름으로 요청한다`() {
        assertEquals(IoResult.Done, runBlocking { repo.rename(session, "ftp://host/dir/a.txt", "b.txt") })

        assertEquals(listOf("RNFR" to "/dir/a.txt", "RNTO" to "/dir/b.txt"), requests)
    }

    @Test
    fun `새 폴더는 부모 디렉터리 아래 이름으로 요청한다`() {
        assertEquals(IoResult.Done, runBlocking { repo.makeDirectory(session, "ftp://host/dir", "new") })

        assertEquals(listOf("MKD" to "/dir/new"), requests)
    }

    @Test
    fun `업로드는 대상 디렉터리 아래 같은 이름으로 요청한다`() {
        val local = File(dir, "a.txt").apply { writeText("data") }

        assertEquals(IoResult.Done, runBlocking { repo.upload(session, listOf(FileHolderItem(path = local.path, name = "a.txt", isDirectory = false, sizeBytes = null, lastModifiedMillis = 0L)), "ftp://host/dir") {} })

        assertEquals(listOf("STOR" to "/dir/a.txt"), requests)
    }

    @Test
    fun `다운로드는 로컬 대상 디렉터리 아래 같은 이름의 파일로 받는다`() {
        assertEquals(IoResult.Done, runBlocking { repo.download(session, listOf(FileHolderItem(path = "ftp://host/r/a.txt", name = "a.txt", isDirectory = false, sizeBytes = null, lastModifiedMillis = 0L)), dir.path, UnexpectedConflictResolver) {} })

        assertEquals(listOf("RETR" to "/r/a.txt"), requests)
        assertEquals("data", File(dir, "a.txt").readText())
    }

    /**
     * 받은 명령과 그 인자를 [requests]에 기록한다.
     * @param command 받은 명령 이름
     */
    private fun ScriptedFtpServer.Exchange.record(command: String) {
        requests += command to argument
    }
}
