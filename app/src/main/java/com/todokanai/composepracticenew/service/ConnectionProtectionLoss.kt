package com.todokanai.composepracticenew.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 연결 유지 서비스가 보호를 줄 수 없게 됐을 때의 처리. 보호 없이 연결을 남기지 않도록 연결을 끊김으로 종료하고, 실제로 끝낸 연결이 있을 때만 앱 밖에 있는 사용자에게 알림으로 알린다.
 * 보호를 줄 수 없는 경우는 실행 시간 한도와 시작 거부([FtpForegroundService]의 startForeground, [FtpServiceController]의 startForegroundService)이며 모두 이 처리 하나를 따른다.
 * @param context 서비스가 멈춘 뒤에도 알림을 내는 애플리케이션 Context
 * @param policy 연결을 끝내는 연결 상태 규칙
 * @param appScope 서비스가 멈춘 뒤에도 연결 종료를 끝까지 실행하는 앱 수명 스코프
 */
class ConnectionProtectionLoss @Inject constructor(
    @ApplicationContext private val context: Context,
    private val policy: ConnectionServicePolicy,
    @ApplicationScope private val appScope: CoroutineScope
) {

    /** 연결을 끊김으로 종료하고, 끝낸 연결이 있으면 알린다. 종료를 기다리지 않고 바로 돌아온다. */
    fun handle() {
        appScope.launch { if (policy.endProtection()) notifyConnectionEnded() }
    }

    /** 보호를 줄 수 없어 연결을 종료했다고 알린다. 알림 권한이 없으면 알리지 않는다. */
    private fun notifyConnectionEnded() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val notification = NotificationCompat.Builder(context, FtpForegroundService.CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.ftp_protection_lost_notice))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        /** 연결을 종료했다는 알림의 id. 서비스가 멈춰 사라지는 포그라운드 알림과 겹치지 않는다. */
        const val NOTIFICATION_ID = 1003
    }
}
