package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.withFailureMessage
import org.junit.Assert.assertEquals
import org.junit.Test

/** [withFailureMessage]가 실패 메시지만 바꾸고, 연결 끊김의 종류는 유지하는지 검증하는 단위 테스트. */
class IoResultFailureMessageTest {

    @Test
    fun `성공은 그대로 둔다`() {
        assertEquals(IoResult.Done, IoResult.Done.withFailureMessage("실패"))
    }

    @Test
    fun `연결 끊김은 연결 끊김으로 남기고 메시지만 바꾼다`() {
        assertEquals(IoResult.ConnectionLost("폴더 생성 실패"), IoResult.ConnectionLost("끊김").withFailureMessage("폴더 생성 실패"))
    }

    @Test
    fun `항목 실패와 부분 실패는 메시지 하나로 요약한 항목 실패가 된다`() {
        val partial = IoResult.PartiallyFailed("하위 항목 삭제 실패", listOf(FailedItem("a.txt", "550")))

        assertEquals(IoResult.ItemFailed("삭제 실패"), IoResult.ItemFailed("550").withFailureMessage("삭제 실패"))
        assertEquals(IoResult.ItemFailed("삭제 실패"), partial.withFailureMessage("삭제 실패"))
    }
}
