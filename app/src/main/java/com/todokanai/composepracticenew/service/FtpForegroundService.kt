package com.todokanai.composepracticenew.service

import android.Manifest
import android.app.Notification
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 원격 연결이 유지되는 동안 상태 알림을 표시해 시스템이 프로세스를 종료하지 않도록 보호하는 ForegroundService.
 * 이 서비스는 프로세스 보호 전담이다 — 연결되면 MyApplication이 시작하고, 연결 상태가 연결되지 않은 상태가 되면 스스로 멈추며, 연결을 맺거나 끊지 않는다.
 * 그래서 서버 전환 중 잠깐 연결되지 않은 상태가 되어 서비스가 멈춰도 새 연결은 영향을 받지 않는다.
 * 예외는 시스템의 실행 시간 한도로 보호가 끝났을 때다 — 보호 없이 연결을 남기지 않도록 연결을 끊김으로 종료한다.
 */
@AndroidEntryPoint
class FtpForegroundService : SelfStoppingForegroundService() {

    /** 서비스가 멈출 때와 보호가 끝났을 때의 처리를 정하는 연결 상태 규칙. */
    @Inject
    lateinit var policy: ConnectionServicePolicy

    /** 서비스가 멈춘 뒤에도 연결 종료를 끝까지 실행하는 앱 수명 스코프. */
    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    /** intent의 EXTRA_SERVER_LABEL로 Foreground 알림을 시작한다. 이미 실행 중이면 알림의 서버 이름만 바뀐다. */
    override fun enterForeground(intent: Intent?) {
        val label = intent?.getStringExtra(EXTRA_SERVER_LABEL).orEmpty()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildForegroundNotification(label), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    override suspend fun awaitStop() = policy.awaitStop()

    /** 보호 없이 연결을 남기지 않도록 연결을 끊김으로 종료하고, 앱 밖에 있는 사용자에게 알림으로 알린다. */
    override fun onTimeLimitReached() {
        appScope.launch { policy.endProtection() }
        notifyTimeLimitReached()
    }

    private fun buildForegroundNotification(serverLabel: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(serverLabel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

    /** 실행 시간 한도로 연결을 종료했다고 알린다. 알림 권한이 없으면 알리지 않는다. */
    private fun notifyTimeLimitReached() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.ftp_time_limit_notice))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(TIME_LIMIT_NOTIFICATION_ID, notification)
    }

    companion object {
        const val EXTRA_SERVER_LABEL = "extra_server_label"
        const val NOTIFICATION_ID = 1001
        /** 실행 시간 한도로 연결을 종료했다는 알림의 id. 서비스가 멈춰 사라지는 포그라운드 알림과 겹치지 않는다. */
        private const val TIME_LIMIT_NOTIFICATION_ID = 1003
        const val CHANNEL_ID = "ftp_connection"
    }
}
