package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.IoResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.nio.file.NoSuchFileException

/** [localIo]가 입출력 실패만 결과로 바꾸고, 작업이 돌려준 결과와 그 밖의 예외는 그대로 두는지 검증하는 단위 테스트. */
class LocalIoTest {

    @Test
    fun `입출력 실패는 예외 메시지를 담은 항목 실패가 된다`() {
        val result = localIo<Unit> { throw NoSuchFileException("/a/b.txt") }

        assertEquals(IoResult.ItemFailed("/a/b.txt"), result)
    }

    @Test
    fun `메시지 함수를 주면 입출력 실패를 그 메시지로 바꾼다`() {
        val result = localIo<Unit>(describe = { e -> "이동 실패: b.txt — ${e.message}" }) { throw IOException("디스크 오류") }

        assertEquals(IoResult.ItemFailed("이동 실패: b.txt — 디스크 오류"), result)
    }

    @Test
    fun `작업이 돌려준 결과는 그대로 돌려준다`() {
        assertEquals(IoResult.ItemFailed("디스크 공간 부족"), localIo<Unit> { IoResult.ItemFailed("디스크 공간 부족") })
        assertEquals(IoResult.Done, localIo { IoResult.Done })
    }

    @Test
    fun `입출력 실패가 아닌 예외는 결과로 바꾸지 않고 전파한다`() {
        assertThrows(IllegalStateException::class.java) { localIo<Unit> { throw IllegalStateException("구현 오류") } }
    }
}
