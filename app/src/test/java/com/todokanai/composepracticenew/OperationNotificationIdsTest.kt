package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.tools.OperationNotificationIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 작업 인스턴스 ID가 프로세스마다 다시 발급돼도 알림 id가 알림함에 남은 이전 알림과 겹치지 않는지 검증하는 단위 테스트.
 */
class OperationNotificationIdsTest {

    @Test
    fun `남은 알림이 없으면 1부터 붙인다`() {
        val ids = OperationNotificationIds { emptyList() }

        assertEquals(1, ids.idFor(1))
        assertEquals(2, ids.idFor(2))
    }

    @Test
    fun `이전 프로세스가 남긴 알림보다 큰 id를 붙인다`() {
        val ids = OperationNotificationIds { listOf(3, 7) }

        assertEquals(8, ids.idFor(1))
        assertEquals(9, ids.idFor(2))
    }

    @Test
    fun `같은 작업에는 같은 id를 돌려준다`() {
        val ids = OperationNotificationIds { emptyList() }

        val first = ids.idFor(1)
        ids.idFor(2)

        assertEquals(first, ids.idFor(1))
    }

    @Test
    fun `남은 알림은 처음 id를 붙일 때 한 번만 확인한다`() {
        var reads = 0
        val ids = OperationNotificationIds { reads++; listOf(5) }

        ids.idFor(1)
        ids.idFor(2)

        assertEquals(1, reads)
    }

    @Test
    fun `잊은 작업의 id는 다시 붙이지 않는다`() {
        val ids = OperationNotificationIds { emptyList() }
        val released = ids.idFor(1)
        ids.release(1)

        assertNotEquals(released, ids.idFor(2))
        assertNotEquals(released, ids.idFor(1))
    }
}
