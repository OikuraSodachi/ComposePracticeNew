package com.todokanai.composepracticenew.service

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FileOperationForegroundService의 시작·종료를 담당하는 앱 레이어 헬퍼.
 * progressMap 관찰자가 직접 Context와 Intent를 다루지 않도록 이 클래스를 경유한다.
 */
@Singleton
class FileOperationServiceController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** FileOperationForegroundService를 시작한다. 이미 실행 중이면 onStartCommand만 재호출된다. */
    fun start() {
        context.startForegroundService(Intent(context, FileOperationForegroundService::class.java))
    }

    /** FileOperationForegroundService를 종료한다. */
    fun stop() {
        context.stopService(Intent(context, FileOperationForegroundService::class.java))
    }
}
