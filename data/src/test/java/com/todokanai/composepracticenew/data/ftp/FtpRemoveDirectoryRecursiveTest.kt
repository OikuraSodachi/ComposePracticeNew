package com.todokanai.composepracticenew.data.ftp

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [FtpCommandExecutor.removeDirectoryRecursive]가 하위 항목 삭제에 실패하면 그 항목을 품은 상위 디렉터리에 RMD를 보내지 않고,
 * 실패 메시지에는 원인 항목만 남기며, 실패와 무관한 형제 항목은 끝까지 삭제하는지 검증하는 단위 테스트.
 */
class FtpRemoveDirectoryRecursiveTest {

    private val client = FakeFtpClient()
    private val session = FtpSession(client)
    private val commands = FtpCommandExecutor(session)

    @Test
    fun `하위 파일 삭제가 실패하면 상위 디렉터리 RMD는 보내지 않고 원인 파일만 알리며 형제는 삭제한다`() {
        runBlocking { session.markLoggedIn() }
        client.features = setOf("MLST")
        client.mlsdListings = mapOf(
            "/r" to listOf("type=dir; sub", "type=dir; other"),
            "/r/sub" to listOf("type=file;size=1; x.txt"),
            "/r/other" to listOf("type=file;size=1; y.txt")
        )
        client.onDeleteFile = { path -> path != "/r/sub/x.txt" }

        val error = assertThrows(Exception::class.java) {
            runBlocking { commands.removeDirectoryRecursive("ftp://host/r") }
        }

        assertEquals("하위 항목 삭제 실패: sub/x.txt (DELE 실패: 550 Permission denied)", error.message)
        assertEquals(listOf("/r/other/y.txt", "/r/sub/x.txt"), client.deleteRequests.sorted())
        assertEquals("실패를 품은 /r/sub와 /r에는 RMD를 보내지 않아야 함", listOf("/r/other"), client.removeDirectoryRequests)
    }
}
