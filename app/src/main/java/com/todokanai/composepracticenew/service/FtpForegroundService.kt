package com.todokanai.composepracticenew.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.todokanai.composepracticenew.R

/**
 * 원격 연결이 유지되는 동안 상태 알림을 표시해 시스템이 프로세스를 종료하지 않도록 보호하는 ForegroundService.
 * 이 서비스는 프로세스 보호 전담이다 — 시작·중지는 연결 상태를 관찰하는 MyApplication이 정하고, 연결을 맺거나 끊지 않는다.
 * 그래서 서버 전환 중 잠깐 연결되지 않은 상태가 되어 서비스가 멈춰도 새 연결은 영향을 받지 않는다.
 */
class FtpForegroundService : Service() {

    /**
     * START_NOT_STICKY 반환 — 프로세스 종료 시 시스템이 서비스를 재시작하지 않는다.
     * intent의 EXTRA_SERVER_LABEL을 읽어 Foreground 알림을 시작한다. 이미 실행 중이면 알림의 서버 이름만 바뀐다.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra(EXTRA_SERVER_LABEL).orEmpty()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildForegroundNotification(label), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = null

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
