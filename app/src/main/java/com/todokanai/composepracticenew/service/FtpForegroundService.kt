package com.todokanai.composepracticenew.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.todokanai.composepracticenew.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 원격 연결이 유지되는 동안 상태 알림을 표시해 시스템이 프로세스를 종료하지 않도록 보호하는 ForegroundService.
 * 이 서비스는 프로세스 보호 전담이다 — 연결되면 MyApplication이 시작하고, 연결 상태가 연결되지 않은 상태가 되면 스스로 멈추며, 연결을 맺거나 끊지 않는다.
 * 그래서 서버 전환 중 잠깐 연결되지 않은 상태가 되어 서비스가 멈춰도 새 연결은 영향을 받지 않는다.
 */
@AndroidEntryPoint
class FtpForegroundService : Service() {

    /** 서비스가 멈출 때를 정하는 연결 상태 규칙. */
    @Inject
    lateinit var policy: ConnectionServicePolicy

    /** 연결 상태 관찰을 실행하는 스코프. 메인 스레드에서 실행해 멈춤이 언제나 같은 스레드의 startForeground 뒤에 오게 한다. */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 가장 최근 시작 요청에 대한 연결 상태 관찰. 시작 요청마다 새로 시작한다. */
    private var stopWatch: Job? = null

    /**
     * START_NOT_STICKY 반환 — 프로세스 종료 시 시스템이 서비스를 재시작하지 않는다.
     * intent의 EXTRA_SERVER_LABEL을 읽어 Foreground 알림을 시작한다. 이미 실행 중이면 알림의 서버 이름만 바뀐다.
     * 그 뒤 연결 상태 관찰을 이 시작 요청 기준으로 다시 시작한다.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra(EXTRA_SERVER_LABEL).orEmpty()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildForegroundNotification(label), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        // startForeground 뒤에 관찰한다 — 그 전에 멈추면 startForegroundService 계약 위반으로 앱이 종료된다
        stopWatch?.cancel()
        stopWatch = serviceScope.launch { stopWhenNotConnected(startId) }
        return START_NOT_STICKY
    }

    /**
     * 연결 상태가 연결되지 않은 상태(종료·전환·끊김)가 되면 서비스를 멈춘다. 이미 그렇다면 바로 멈춘다.
     * [startId]로 멈추므로, 그 뒤에 온 시작 요청(새 연결)이 있으면 멈추지 않고 그 요청의 관찰이 이어받는다.
     * @param startId 이 관찰을 시작한 시작 요청의 id
     */
    private suspend fun stopWhenNotConnected(startId: Int) {
        policy.awaitStop()
        stopSelf(startId)
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
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
    }
}
