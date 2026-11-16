package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.failedItemsAt
import com.todokanai.composepracticenew.model.wrappingFailureReason
import org.junit.Assert.assertEquals
import org.junit.Test

/** [wrappingFailureReason]가 실패 종류를 유지한 채 원인만 감싸는지, [failedItemsAt]이 실패를 항목 목록으로 옮기는지 검증하는 단위 테스트. */
class IoResultFailureReasonTest {

    /** 저장소가 알린 원인. */
    private val refused = FailureReason.IoFailed("550")

    @Test
    fun `성공은 그대로 둔다`() {
        assertEquals(IoResult.Done, IoResult.Done.wrappingFailureReason { FailureReason.RenameFailed("b", it) })
    }

    @Test
    fun `항목 실패는 원래 원인을 감싼 항목 실패가 된다`() {
        assertEquals(
            IoResult.ItemFailed(FailureReason.RenameFailed("b", refused)),
            IoResult.ItemFailed(refused).wrappingFailureReason { FailureReason.RenameFailed("b", it) }
        )
    }

    @Test
    fun `원인을 모르는 항목 실패는 원인 없이 감싼다`() {
        assertEquals(
            IoResult.ItemFailed(FailureReason.MakeFolderFailed("new", null)),
            IoResult.ItemFailed(null).wrappingFailureReason { FailureReason.MakeFolderFailed("new", it) }
        )
    }

    @Test
    fun `연결 끊김은 연결 끊김으로 남기고 원인만 감싼다`() {
        assertEquals(
            IoResult.ConnectionLost(FailureReason.MakeFolderFailed("new", refused)),
            IoResult.ConnectionLost(refused).wrappingFailureReason { FailureReason.MakeFolderFailed("new", it) }
        )
    }

    @Test
    fun `부분 실패는 항목별 원인을 담고 있으므로 그대로 둔다`() {
        val partial = IoResult.PartiallyFailed(listOf(FailedItem("a.txt", refused)))

        assertEquals(partial, partial.wrappingFailureReason { FailureReason.RenameFailed("b", it) })
    }

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
