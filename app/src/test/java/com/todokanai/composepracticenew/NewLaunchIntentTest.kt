package com.todokanai.composepracticenew

import android.content.Intent
import com.todokanai.composepracticenew.compose.activity.isNewLaunchIntent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MainActivity.onCreate가 처음 실행한 intent를 다시 받은 경우(회전·프로세스 복원·최근 앱 실행)를 걸러내,
 * 알림 클릭을 새로 받았을 때만 처리하는지 검증하는 단위 테스트.
 */
class NewLaunchIntentTest {

    /** 알림 클릭 intent에 붙는 flags. MyNotification.buildClickIntent와 같다. */
    private val notificationFlags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP

    @Test
    fun `Activity가 없을 때 알림을 눌러 처음 실행하면 처리한다`() {
        assertTrue(isNewLaunchIntent(hasSavedState = false, intentFlags = notificationFlags))
    }

    @Test
    fun `회전이나 프로세스 복원으로 다시 만들어지면 처리하지 않는다`() {
        assertFalse(isNewLaunchIntent(hasSavedState = true, intentFlags = notificationFlags))
    }

    @Test
    fun `최근 앱에서 다시 실행하면 처리하지 않는다`() {
        val fromHistory = notificationFlags or Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY

        assertFalse(isNewLaunchIntent(hasSavedState = false, intentFlags = fromHistory))
    }
}
