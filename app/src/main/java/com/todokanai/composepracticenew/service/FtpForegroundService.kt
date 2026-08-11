package com.todokanai.composepracticenew.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.data.ftp.FtpConnectionState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FTP 연결을 Foreground Service로 유지한다.
 * 연결 중에는 상태 알림을 표시하여 시스템이 프로세스를 종료하지 않도록 보장한다.
 * 바인딩 클라이언트(ViewModel 등)는 FtpBinder를 통해 disconnectAndStop()을 호출할 수 있다.
 */
@AndroidEntryPoint
class FtpForegroundService : Service() {

    @Inject
    lateinit var connectionState: FtpConnectionState

    /** 바인딩 클라이언트에 이 서비스 인스턴스를 노출한다. */
    inner class FtpBinder : Binder() {
        /** 바인딩된 클라이언트가 서비스 메서드를 직접 호출할 수 있도록 인스턴스를 반환한다. */
        fun getService(): FtpForegroundService = this@FtpForegroundService
    }

    private val binder = FtpBinder()
    private lateinit var serviceScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    /**
     * START_STICKY 반환 — 시스템이 서비스를 종료한 경우 인텐트 없이 재시작된다.
     * intent의 EXTRA_SERVER_LABEL을 읽어 Foreground 알림을 시작한다.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra(EXTRA_SERVER_LABEL).orEmpty()
        startForeground(NOTIFICATION_ID, buildForegroundNotification(label))
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    /** serviceScope를 취소하여 진행 중인 코루틴을 모두 중단한다. */
    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * FTP 연결을 해제하고 서비스를 중단한다.
     * disconnect() 완료 후 stopSelf()를 호출하므로 onDestroy()가 자동으로 실행된다.
     */
    fun disconnectAndStop() {
        serviceScope.launch {
            connectionState.disconnect()
            stopSelf()
        }
    }

    private fun buildForegroundNotification(serverLabel: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(serverLabel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

    private fun updateNotification(connected: Boolean) {
        val text = if (connected) "연결됨" else "연결 끊김"
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(connected)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val EXTRA_SERVER_LABEL = "extra_server_label"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "ftp_connection"
    }
}
