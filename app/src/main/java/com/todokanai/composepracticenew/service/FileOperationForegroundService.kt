package com.todokanai.composepracticenew.service

import android.app.Notification
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.ServiceCompat
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.tools.MyNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 로컬 IO 작업(복사·이동·삭제·압축 등)이 진행 중인 동안 프로세스를 보호하는 ForegroundService. 작업 자체는 ApplicationScope 코루틴에서 실행되며 이 서비스는 시스템 종료 방지 전담이다.
 * 진행 중인 작업이 없어지면 스스로 멈춘다. 시스템의 실행 시간 한도로 멈추면 작업은 보호 없이 계속된다.
 */
@AndroidEntryPoint
class FileOperationForegroundService : SelfStoppingForegroundService() {

    /** 포그라운드 알림을 만드는 알림 도우미. */
    @Inject lateinit var myNotification: MyNotification

    /** 서비스가 멈출 때를 정하는 진행 상태 규칙. */
    @Inject lateinit var policy: FileOperationServicePolicy

    override fun enterForeground(intent: Intent?) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildForegroundNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    override suspend fun awaitStop() = policy.awaitStop()

    private fun buildForegroundNotification(): Notification =
        myNotification.buildForegroundNotification(
            channelId = CHANNEL_ID,
            title = getString(R.string.app_name),
            body = getString(R.string.file_operation_notification_text)
        )

    companion object {
        const val NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "file_operation"
    }
}
