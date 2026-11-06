package com.todokanai.composepracticenew.service

import android.app.Notification
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.todokanai.composepracticenew.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 진행 중인 IO 작업(로컬 복사·이동·삭제·압축, 원격 다운로드·업로드 등)이 있는 동안 프로세스를 보호하는 ForegroundService. 작업 자체는 ApplicationScope 코루틴에서 실행되며 이 서비스는 시스템 종료 방지 전담이다.
 * 작업은 시작하기 전에 보호를 요청하고([FileOperationProtection]), 이 서비스는 요청마다 포그라운드가 됐는지 거부됐는지를 알린다 — 거부된 작업은 시작하지 않는다.
 * 진행 중인 작업이 없어지면 스스로 멈춘다. 시스템의 실행 시간 한도로 멈추면 이미 시작한 로컬 작업은 보호 없이 계속되며, 그 뒤 작업의 완료는 보장하지 않는다(지원 범위 밖).
 * 원격 작업은 같은 한도로 연결 유지 서비스가 연결을 끊김으로 종료하므로, 끝나지 않은 작업은 연결 끊김 실패로 끝난다([FtpForegroundService]).
 */
@AndroidEntryPoint
class FileOperationForegroundService : SelfStoppingForegroundService() {

    /** 서비스가 멈출 때를 정하는 진행 상태 규칙. */
    @Inject lateinit var policy: FileOperationServicePolicy

    /** 시작 요청의 결과를 받는 작업 보호. */
    @Inject lateinit var protection: FileOperationProtection

    /** 포그라운드 알림을 시작하고, 그 시작 요청이 보호를 받았다고 알린다. */
    override fun enterForeground(intent: Intent?) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildForegroundNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        protection.report(requestId(intent), granted = true)
    }

    override suspend fun awaitStop() = policy.awaitStop()

    /** 그 시작 요청이 보호를 받지 못했다고 알린다 — 요청한 작업은 시작하지 않는다. */
    override fun onStartRefused(intent: Intent?) = protection.report(requestId(intent), granted = false)

    private fun requestId(intent: Intent?): Int = intent?.getIntExtra(EXTRA_REQUEST_ID, NO_REQUEST_ID) ?: NO_REQUEST_ID

    private fun buildForegroundNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.file_operation_notification_text))
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()

    companion object {
        const val NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "file_operation"
        /** 시작 요청의 id를 담는 Intent extra. */
        const val EXTRA_REQUEST_ID = "extra_request_id"
        /** id가 없는 시작 요청. 발급하는 id는 1부터라 기다리는 요청과 겹치지 않는다. */
        private const val NO_REQUEST_ID = 0
    }
}
