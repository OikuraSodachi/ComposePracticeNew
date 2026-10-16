package com.todokanai.composepracticenew.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.todokanai.composepracticenew.R

/** 로컬 IO 작업(복사·이동·삭제·압축 등)이 진행 중인 동안 프로세스를 보호하는 ForegroundService. 작업 자체는 ApplicationScope 코루틴에서 실행되며 이 서비스는 시스템 종료 방지 전담이다. */
class FileOperationForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildForegroundNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun buildForegroundNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.file_operation_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

    companion object {
        const val NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "file_operation"
    }
}
