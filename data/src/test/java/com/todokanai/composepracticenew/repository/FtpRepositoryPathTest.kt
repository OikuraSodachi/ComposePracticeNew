package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.ftp.FakeFtpClient
import com.todokanai.composepracticenew.data.ftp.FtpCommandExecutor
import com.todokanai.composepracticenew.data.ftp.FtpConnector
import com.todokanai.composepracticenew.data.ftp.FtpKeepAlive
import com.todokanai.composepracticenew.data.ftp.FtpSession
import com.todokanai.composepracticenew.data.ftp.FtpTransferrer
import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * FTP 저장소가 "디렉터리 + 이름"으로 받은 목적지를 그 디렉터리 아래(이름 변경은 같은 디렉터리 안)의 경로로 만들어
 * 서버·로컬 파일에 요청하는지 검증하는 단위 테스트(#228).
 */
class FtpRepositoryPathTest {

    /** 받은 파일을 저장하고 올릴 파일을 두는 로컬 임시 디렉터리. */
    private val dir: File = Files.createTempDirectory("ftp-repository-path-test").toFile()
    /** 요청된 원격 경로를 기록하는 가짜 클라이언트. */
    private val client = FakeFtpClient().apply { streamContent = "data".toByteArray() }
    /** 로그인을 마친 테스트 세션. */
    private val session = FtpSession(client).also { runBlocking { it.markLoggedIn(it.closeRequestMark(), home = null, serverName = "server") } }
    /** FTP 저장소와 전송기가 함께 쓰는 명령 실행기. */
    private val commands = FtpCommandExecutor(session)
    /** 검증 대상. */
    private val repo = FtpRepositoryImpl(session, FtpConnector(session, FtpKeepAlive(session, session, TestScope())), commands, FtpTransferrer(session, commands))

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `이름 변경은 같은 디렉터리 안의 새 이름으로 요청한다`() {
        assertEquals(IoResult.Done, runBlocking { repo.rename("ftp://host/dir/a.txt", "b.txt") })

        assertEquals(listOf("/dir/a.txt" to "/dir/b.txt"), client.renameRequests)
    }

    @Test
    fun `새 폴더는 부모 디렉터리 아래 이름으로 요청한다`() {
        assertEquals(IoResult.Done, runBlocking { repo.makeDirectory("ftp://host/dir", "new") })

        assertEquals(listOf("/dir/new"), client.makeDirectoryRequests)
    }

    @Test
    fun `업로드는 대상 디렉터리 아래 같은 이름으로 요청한다`() {
        val local = File(dir, "a.txt").apply { writeText("data") }

        assertEquals(IoResult.Done, runBlocking { repo.upload(local.path, "ftp://host/dir", "a.txt") {} })

        assertEquals(listOf("/dir/a.txt"), client.storeRequests)
    }

    @Test
    fun `다운로드는 로컬 대상 디렉터리 아래 같은 이름의 파일로 받는다`() {
        assertEquals(IoResult.Done, runBlocking { repo.download("ftp://host/r/a.txt", dir.path, "a.txt") {} })

        assertEquals(listOf("/r/a.txt"), client.retrieveRequests)
        assertEquals("data", File(dir, "a.txt").readText())
    }
}
