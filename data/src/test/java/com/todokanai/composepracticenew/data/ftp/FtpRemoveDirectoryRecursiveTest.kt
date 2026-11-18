package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.RemoteStep
import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [FtpRemoteDeletion.removeDirectoryRecursive]가 하위 항목 삭제에 실패하면 그 항목을 품은 상위 디렉터리에 RMD를 보내지 않고,
 * 실패 메시지에는 원인 항목만 남기며, 실패와 무관한 형제 항목은 끝까지 삭제하는지 검증하는 단위 테스트.
 */
class FtpRemoveDirectoryRecursiveTest {

    private val client = FakeFtpClient()
    private val session = FtpSession(client)
    private val commands = FtpOperationParts(session)

    @Test
    fun `하위 파일 삭제가 실패하면 상위 디렉터리 RMD는 보내지 않고 원인 파일만 알리며 형제는 삭제한다`() {
        runBlocking { session.markLoggedIn(home = null) }
        client.features = setOf("MLST")
        client.mlsdListings = mapOf(
            "/r" to listOf("type=dir; sub", "type=dir; other"),
            "/r/sub" to listOf("type=file;size=1; x.txt"),
            "/r/other" to listOf("type=file;size=1; y.txt")
        )
        client.onDeleteFile = { path -> path != "/r/sub/x.txt" }

        val result = runBlocking { commands.deletion.delete(listOf(directoryTarget("ftp://host/r"))) {} }

        assertEquals(IoResult.PartiallyFailed(listOf(FailedItem("r/sub/x.txt", FailureReason.ServerRefused(RemoteStep.DELETE_FILE, "550 Permission denied")))), result)
        assertEquals(listOf("/r/other/y.txt", "/r/sub/x.txt"), client.deleteRequests.sorted())
        assertEquals("실패를 품은 /r/sub와 /r에는 RMD를 보내지 않아야 함", listOf("/r/other"), client.removeDirectoryRequests)
    }

    @Test
    fun `MLSD 목록에 보인 항목을 모두 지웠는데 RMD가 거부되면 보이지 않는 항목이 남았을 수 있음을 함께 알린다`() {
        runBlocking { session.markLoggedIn(home = null) }
        client.features = setOf("MLST")
        client.mlsdListings = mapOf("/r" to listOf("type=file;size=1; a.txt"))
        client.serverBehaviors = mapOf("RMD" to ServerBehavior.Refuse(550, "550 Directory not empty"))

        val result = runBlocking { commands.deletion.delete(listOf(directoryTarget("ftp://host/r"))) {}.singleItemResult() }

        assertEquals(IoResult.ItemFailed(FailureReason.ServerRefused(RemoteStep.REMOVE_FOLDER, "550 Directory not empty", mayHaveHiddenEntries = true)), result)
        assertEquals(listOf("/r/a.txt"), client.deleteRequests)
    }

    @Test
    fun `LIST로 조회한 세션에서 RMD가 거부되면 보이지 않는 항목이 남았을 수 있음을 함께 알린다`() {
        runBlocking { session.markLoggedIn(home = null) }
        client.remoteFiles = emptyList()
        client.serverBehaviors = mapOf("RMD" to ServerBehavior.Refuse(550, "550 Directory not empty"))

        val result = runBlocking { commands.deletion.delete(listOf(directoryTarget("ftp://host/r"))) {}.singleItemResult() }

        assertEquals(IoResult.ItemFailed(FailureReason.ServerRefused(RemoteStep.REMOVE_FOLDER, "550 Directory not empty", mayHaveHiddenEntries = true)), result)
    }
}
