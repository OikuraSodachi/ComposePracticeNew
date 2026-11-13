package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.tools.NotificationProcessToken
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [NotificationProcessToken]이 이 프로세스에서 만든 알림 intent의 값만 받아들이는지 검증하는 단위 테스트(#300). */
class NotificationProcessTokenTest {

    @Test
    fun `이 프로세스의 값을 담은 intent만 이 프로세스의 것으로 판정한다`() {
        assertTrue(NotificationProcessToken.isFromThisProcess(NotificationProcessToken.value))
    }

    @Test
    fun `값이 없거나 다른 프로세스의 값이면 이 프로세스의 것이 아니다`() {
        assertFalse(NotificationProcessToken.isFromThisProcess(null))
        assertFalse(NotificationProcessToken.isFromThisProcess("other-process"))
    }
}
