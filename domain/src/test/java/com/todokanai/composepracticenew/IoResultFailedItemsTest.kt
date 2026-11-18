package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.failedItemsAt
import org.junit.Assert.assertEquals
import org.junit.Test

/** [failedItemsAt]이 실패를 항목 목록으로 옮기는지 검증하는 단위 테스트. */
class IoResultFailedItemsTest {

    /** 저장소가 알린 원인. */
    private val refused = FailureReason.IoFailed("550")

    @Test
    fun `항목 실패와 연결 끊김은 그 경로의 실패 하나가 된다`() {
        assertEquals(listOf(FailedItem("dir", refused)), IoResult.ItemFailed(refused).failedItemsAt("dir"))
        assertEquals(listOf(FailedItem("dir", refused)), IoResult.ConnectionLost(refused).failedItemsAt("dir"))
    }

    @Test
    fun `부분 실패는 항목마다 그 경로 아래로 옮긴다`() {
        val partial = IoResult.PartiallyFailed(listOf(FailedItem("", null), FailedItem("sub/x.txt", refused)))

        assertEquals(listOf(FailedItem("dir", null), FailedItem("dir/sub/x.txt", refused)), partial.failedItemsAt("dir"))
    }
}
