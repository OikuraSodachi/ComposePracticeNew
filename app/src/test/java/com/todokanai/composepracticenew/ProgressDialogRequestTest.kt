package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.compose.activity.MISSING_EXTRA
import com.todokanai.composepracticenew.compose.activity.ProgressDialogRequest
import com.todokanai.composepracticenew.compose.activity.progressDialogRequestOf
import com.todokanai.composepracticenew.tools.NotificationProcessToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 진행 알림 intent에서 꺼낸 값으로 진행률 다이얼로그를 띄울 작업을 정하는지 검증하는 단위 테스트.
 */
class ProgressDialogRequestTest {

    /** 이 프로세스가 만든 intent의 값. */
    private val thisProcess = NotificationProcessToken.value

    @Test
    fun `이 프로세스가 만든 intent이면 담긴 작업을 돌려준다`() {
        assertEquals(
            ProgressDialogRequest(operationTypeId = 3, instanceId = 7),
            progressDialogRequestOf(processToken = thisProcess, operationTypeId = 3, instanceId = 7)
        )
    }

    @Test
    fun `다른 프로세스가 만든 intent는 무시한다`() {
        assertNull(progressDialogRequestOf(processToken = "other-process", operationTypeId = 3, instanceId = 7))
    }

    @Test
    fun `프로세스 값이 없는 intent는 무시한다`() {
        assertNull(progressDialogRequestOf(processToken = null, operationTypeId = 3, instanceId = 7))
    }

    @Test
    fun `작업 종류나 인스턴스 ID가 빠지면 무시한다`() {
        assertNull(progressDialogRequestOf(processToken = thisProcess, operationTypeId = MISSING_EXTRA, instanceId = 7))
        assertNull(progressDialogRequestOf(processToken = thisProcess, operationTypeId = 3, instanceId = MISSING_EXTRA))
    }
}
