package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 데이터 스트림을 열지 못했을 때 [FtpTransferrer]가 기다릴 응답이 없는 completePendingCommand를 호출하지 않고 바로 실패를 알리는지 검증하는 단위 테스트.
 * 실제 FTPClient라면 그 호출이 soTimeout까지 세션 잠금을 쥔 채 멈추므로, 가짜 클라이언트로 호출 여부를 기록한다.
 * 파일 하나의 스트림 거부는 [FtpCommandContractTest]가, 이 테스트는 폴더 전송을 검증한다.
 */
class FtpTransferrerStreamOpenFailureTest {

    private val dir: File = Files.createTempDirectory("ftp-transfer-test").toFile()
    private val client = FakeFtpClient()
    private val session = FtpSession(client)
    private val transferrer = FtpTransferrer(session, FtpCommandExecutor(session))

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `폴더 다운로드에서 모든 파일의 스트림을 열지 못해도 완료 확인 없이 모든 파일을 시도한 뒤 실패를 모아 알린다`() {
        runBlocking { session.markLoggedIn(session.closeRequestMark()) }
        client.remoteFiles = listOf("a.txt", "b.txt")

        val result = runBlocking { transferrer.download("ftp://host/r", File(dir, "r").path, isDirectory = true) }

        assertEquals("다운로드 실패: 2개 항목 (a.txt, b.txt)", (result as IoResult.PartiallyFailed).message)
        assertEquals(listOf("/r/a.txt", "/r/b.txt"), client.retrieveRequests)
        assertEquals(0, client.completePendingCalls)
        assertTrue("5xx는 연결 끊김이 아니므로 세션이 유지돼야 함", runBlocking { session.isReady() })
    }
}
