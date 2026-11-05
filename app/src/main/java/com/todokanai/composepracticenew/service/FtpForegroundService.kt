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
 * 원격 연결이 유지되는 동안 상태 알림을 표시해 시스템이 프로세스를 종료하지 않도록 보호하는 ForegroundService.
 * 이 서비스는 프로세스 보호 전담이다 — 연결되면 MyApplication이 시작하고, 연결 상태가 연결되지 않은 상태가 되면 스스로 멈추며, 연결을 맺거나 끊지 않는다.
 * 그래서 서버 전환 중 잠깐 연결되지 않은 상태가 되어 서비스가 멈춰도 새 연결은 영향을 받지 않는다.
 * 예외는 보호를 줄 수 없게 됐을 때(시스템의 실행 시간 한도, startForeground 거부)다 — 보호 없이 연결을 남기지 않도록 연결을 끊김으로 종료한다([ConnectionProtectionLoss]).
 */
@AndroidEntryPoint
class FtpForegroundService : SelfStoppingForegroundService() {

    /** 서비스가 멈출 때를 정하는 연결 상태 규칙. */
    @Inject
    lateinit var policy: ConnectionServicePolicy

    /** 보호를 줄 수 없게 됐을 때 연결을 끝내고 알리는 처리. */
    @Inject
    lateinit var protectionLoss: ConnectionProtectionLoss

    /** intent의 EXTRA_SERVER_LABEL로 Foreground 알림을 시작한다. 이미 실행 중이면 알림의 서버 이름만 바뀐다. */
    override fun enterForeground(intent: Intent?) {
        val label = intent?.getStringExtra(EXTRA_SERVER_LABEL).orEmpty()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildForegroundNotification(label), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    override suspend fun awaitStop() = policy.awaitStop()

    override fun onTimeLimitReached() = protectionLoss.handle()

    override fun onStartRefused(intent: Intent?) = protectionLoss.handle()

    private fun buildForegroundNotification(serverLabel: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(serverLabel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

    companion object {
        const val EXTRA_SERVER_LABEL = "extra_server_label"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "ftp_connection"
    }
}
