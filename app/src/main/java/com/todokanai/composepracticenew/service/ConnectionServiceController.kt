package com.todokanai.composepracticenew.service

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 원격 연결을 유지하는 ForegroundService의 시작 계약. 연결 상태를 관찰하는 MyApplication만 이 인터페이스로 서비스를 시작한다.
 * 멈추는 쪽은 없다 — 서비스가 연결되지 않은 상태를 보고 startForeground 뒤에 스스로 멈춘다(startForeground 전에 멈추면 앱이 종료되므로).
 */
interface ConnectionServiceController {
    /**
     * 연결 유지 서비스를 시작한다. 이미 실행 중이면 알림 레이블만 갱신된다.
     * 시스템이 시작을 거부하면 보호 없이 연결을 남기지 않도록 연결을 끊김으로 종료한다(서비스가 보호를 줄 수 없게 됐을 때와 같은 처리).
     * @param serverLabel 알림에 표시할 서버 이름
     */
    fun start(serverLabel: String)
}

/**
 * FtpForegroundService로 [ConnectionServiceController]를 구현한다.
 * @param context 서비스 시작 Intent 발송에 사용하는 애플리케이션 Context
 * @param protectionLoss 시작이 거부됐을 때 연결을 끝내고 알리는 처리
 */
@Singleton
class FtpServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val protectionLoss: ConnectionProtectionLoss
) : ConnectionServiceController {

    override fun start(serverLabel: String) {
        val intent = Intent(context, FtpForegroundService::class.java).apply {
            putExtra(FtpForegroundService.EXTRA_SERVER_LABEL, serverLabel)
        }
        if (!context.startForegroundServiceUnlessRefused(intent)) protectionLoss.handle()
    }
}
