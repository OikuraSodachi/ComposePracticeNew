package com.todokanai.composepracticenew.service

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 파일 작업 보호 서비스의 시작 계약. 보호를 요청하는 [FileOperationProtection]만 이 인터페이스로 서비스를 시작한다.
 * 멈추는 쪽은 없다 — 서비스가 진행 중인 작업이 없어진 것을 보고 startForeground 뒤에 스스로 멈춘다(startForeground 전에 멈추면 앱이 종료되므로).
 */
interface FileOperationServiceController {
    /**
     * 파일 작업 보호 서비스에 시작 요청을 보낸다. 이미 실행 중이면 onStartCommand만 재호출된다.
     * 받아들여진 요청의 결과(포그라운드가 됐는지)는 서비스가 [FileOperationProtection.report]로 알린다.
     * @param requestId 서비스가 결과를 알릴 때 쓰는 시작 요청의 id
     * @return 시작 요청이 받아들여졌으면 true, 시스템이 거부했으면(Android 12+ 백그라운드 시작 제한) false
     */
    fun start(requestId: Int): Boolean
}

/**
 * FileOperationForegroundService로 [FileOperationServiceController]를 구현한다.
 * @param context 서비스 시작 Intent 발송에 사용하는 애플리케이션 Context
 */
@Singleton
class FileOperationServiceControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : FileOperationServiceController {

    override fun start(requestId: Int): Boolean {
        val intent = Intent(context, FileOperationForegroundService::class.java).apply {
            putExtra(FileOperationForegroundService.EXTRA_REQUEST_ID, requestId)
        }
        return context.startForegroundServiceUnlessRefused(intent)
    }
}
