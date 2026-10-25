package com.todokanai.composepracticenew.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.usecase.RemoteConnectionStateUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** FTP 연결을 Foreground Service로 유지한다. 연결 중에는 상태 알림을 표시하여 시스템이 프로세스를 종료하지 않도록 보장한다. */
@AndroidEntryPoint
class FtpForegroundService : Service() {

    @Inject
    lateinit var connectionStateUseCase: RemoteConnectionStateUseCase

    private lateinit var serviceScope: CoroutineScope

    /** 연결이 끊기면 서비스를 멈추는 관찰 Job. 첫 시작 요청에서 한 번만 시작한다. */
    private var lossWatch: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "서비스 생성")
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    /**
     * START_NOT_STICKY 반환 — 프로세스 종료 시 시스템이 서비스를 재시작하지 않는다.
     * intent의 EXTRA_SERVER_LABEL을 읽어 Foreground 알림을 시작한 뒤, 연결 끊김 관찰을 시작한다.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra(EXTRA_SERVER_LABEL).orEmpty()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildForegroundNotification(label), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        // startForeground 뒤에 시작한다 — 그 전에 stopSelf하면 startForegroundService 계약 위반으로 앱이 종료된다
        if (lossWatch == null) lossWatch = serviceScope.launch { stopWhenConnectionLost() }
        return START_NOT_STICKY
    }

    /**
     * 연결된 세션이 끊기면 서비스를 멈춘다. 유지할 연결이 없어졌으므로 어느 화면에 있든, 앱 화면이 없어도 멈춘다.
     * 앱이 요청한 종료·서버 전환은 끊김이 아니므로 멈추지 않는다 — 서버 전환 중에 멈추면 onDestroy의 연결 해제가 새 연결을 끊는다.
     */
    private suspend fun stopWhenConnectionLost() {
        connectionStateUseCase.connectionState.first { it == RemoteConnectionState.LOST }
        Log.w(TAG, "연결 끊김 감지 — 서비스 중지")
        withContext(Dispatchers.Main) { stopSelf() }
    }

    override fun onBind(intent: Intent): IBinder? = null

    /** FTP 연결을 해제한 뒤 serviceScope를 취소한다. disconnect()는 best-effort로 실행되며, 프로세스 종료 타이밍에 따라 완료가 보장되지 않을 수 있다. */
    override fun onDestroy() {
        Log.d(TAG, "서비스 종료 — disconnect 호출")
        CoroutineScope(Dispatchers.IO).launch { connectionStateUseCase.disconnect() }
        serviceScope.cancel()
        super.onDestroy()
    }

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
        private const val TAG = "FtpForegroundService"
    }
}
