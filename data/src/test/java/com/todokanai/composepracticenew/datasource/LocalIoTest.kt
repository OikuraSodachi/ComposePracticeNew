package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.FileSystemRefusal
import com.todokanai.composepracticenew.model.IoResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.NoSuchFileException

/** [localIo]가 입출력 실패만 결과로 바꾸고, 작업이 돌려준 결과와 그 밖의 예외는 그대로 두며, [ioFailureReasonOf]가 원인 종류를 정하는지 검증하는 단위 테스트. */
class LocalIoTest {

    @Test
    fun `입출력 실패는 예외 메시지를 담은 항목 실패가 된다`() {
        val result = localIo<Unit> { throw IOException("디스크 오류") }

        assertEquals(IoResult.ItemFailed(FailureReason.IoFailed("디스크 오류")), result)
    }

    @Test
    fun `이유를 비워 둔 nio 예외는 경로 뒤에 예외 종류로 정한 이유를 붙인다`() {
        assertEquals(FailureReason.FileSystemRefused("/a/b.txt", FileSystemRefusal.NotFound), ioFailureReasonOf(NoSuchFileException("/a/b.txt")))
        assertEquals(FailureReason.FileSystemRefused("/a", FileSystemRefusal.AlreadyExists), ioFailureReasonOf(FileAlreadyExistsException("/a")))
        assertEquals(FailureReason.FileSystemRefused("/a -> /b", FileSystemRefusal.AccessDenied), ioFailureReasonOf(AccessDeniedException("/a", "/b", null)))
    }

    @Test
    fun `앱이 정한 실패는 메시지가 아니라 담은 원인이 된다`() {
        val result = localIo<Unit> { throw LocalFailureException(FailureReason.UnreadableArchive("a.zip")) }

        assertEquals(IoResult.ItemFailed(FailureReason.UnreadableArchive("a.zip")), result)
    }

    @Test
    fun `이유가 있는 nio 예외는 메시지를 그대로 쓴다`() {
        assertEquals(FailureReason.IoFailed("/a: No space left on device"), ioFailureReasonOf(FileSystemException("/a", null, "No space left on device")))
    }

    @Test
    fun `메시지 함수를 주면 입출력 실패를 그 메시지로 바꾼다`() {
        val result = localIo<Unit>(describe = { e -> FailureReason.TransferFailed(e.message) }) { throw IOException("디스크 오류") }

        assertEquals(IoResult.ItemFailed(FailureReason.TransferFailed("디스크 오류")), result)
    }

    @Test
    fun `작업이 돌려준 결과는 그대로 돌려준다`() {
        assertEquals(IoResult.ItemFailed(FailureReason.NoFreeSpace), localIo<Unit> { IoResult.ItemFailed(FailureReason.NoFreeSpace) })
        assertEquals(IoResult.Done, localIo { IoResult.Done })
    }

    @Test
    fun `입출력 실패가 아닌 예외는 결과로 바꾸지 않고 전파한다`() {
        assertThrows(IllegalStateException::class.java) { localIo<Unit> { throw IllegalStateException("구현 오류") } }
    }
}
