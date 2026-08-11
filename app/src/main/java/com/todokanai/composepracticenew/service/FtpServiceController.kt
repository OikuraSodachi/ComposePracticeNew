package com.todokanai.composepracticenew.service

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FtpForegroundService의 시작·종료를 담당하는 앱 레이어 헬퍼.
 * ViewModel이 직접 Context와 Intent를 다루지 않도록 이 클래스를 경유한다.
 */
@Singleton
class FtpServiceController @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * FtpForegroundService를 Foreground Service로 시작한다.
     * 서비스가 이미 실행 중이면 알림 레이블만 갱신된다.
     * @param serverLabel 알림에 표시할 서버 이름
     */
    fun start(serverLabel: String) {
        val intent = Intent(context, FtpForegroundService::class.java).apply {
            putExtra(FtpForegroundService.EXTRA_SERVER_LABEL, serverLabel)
        }
        context.startForegroundService(intent)
    }

    /**
     * FtpForegroundService에 중단 명령을 전송한다.
     * 실제 FTP 해제는 서비스 내부 disconnectAndStop()이 담당한다.
     */
    fun stop() {
        context.stopService(Intent(context, FtpForegroundService::class.java))
    }
}
