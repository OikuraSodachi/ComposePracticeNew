package com.todokanai.composepracticenew.service

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 원격 연결을 유지하는 ForegroundService의 시작·종료 계약. ViewModel은 이 인터페이스로만 의존한다. */
interface ConnectionServiceController {
    /**
     * 연결 유지 서비스를 시작한다. 이미 실행 중이면 알림 레이블만 갱신된다.
     * @param serverLabel 알림에 표시할 서버 이름
     */
    fun start(serverLabel: String)

    /** 연결 유지 서비스를 중단한다. 실제 연결 해제는 서비스 종료 시 수행된다. */
    fun stop()
}

/**
 * FtpForegroundService로 [ConnectionServiceController]를 구현한다.
 * @param context 서비스 시작·종료 Intent 발송에 사용하는 애플리케이션 Context
 */
@Singleton
class FtpServiceController @Inject constructor(
    @ApplicationContext private val context: Context
) : ConnectionServiceController {

    override fun start(serverLabel: String) {
        val intent = Intent(context, FtpForegroundService::class.java).apply {
            putExtra(FtpForegroundService.EXTRA_SERVER_LABEL, serverLabel)
        }
        context.startForegroundService(intent)
    }

    override fun stop() {
        context.stopService(Intent(context, FtpForegroundService::class.java))
    }
}
