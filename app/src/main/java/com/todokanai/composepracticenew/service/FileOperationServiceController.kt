package com.todokanai.composepracticenew.service

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FileOperationForegroundService의 시작을 담당하는 앱 레이어 헬퍼.
 * progressMap 관찰자가 직접 Context와 Intent를 다루지 않도록 이 클래스를 경유한다.
 * 멈추는 쪽은 없다 — 서비스가 진행 중인 작업이 없어진 것을 보고 startForeground 뒤에 스스로 멈춘다(startForeground 전에 멈추면 앱이 종료되므로).
 * @param context 서비스 시작 Intent 발송에 사용하는 애플리케이션 Context
 */
@Singleton
class FileOperationServiceController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** FileOperationForegroundService를 시작한다. 이미 실행 중이면 onStartCommand만 재호출된다. */
    fun start() {
        try {
            context.startForegroundService(Intent(context, FileOperationForegroundService::class.java))
        } catch (e: IllegalStateException) {
            // Android 12+ 백그라운드 시작 제한 — 서비스 보호 없이 작업만 계속 진행
        }
    }
}
