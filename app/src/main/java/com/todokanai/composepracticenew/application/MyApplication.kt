package com.todokanai.composepracticenew.application

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.di.ApplicationScope
import com.todokanai.composepracticenew.myobjects.OperationConstants
import com.todokanai.composepracticenew.service.ConnectionServiceController
import com.todokanai.composepracticenew.service.ConnectionServicePolicy
import com.todokanai.composepracticenew.service.FileOperationForegroundService
import com.todokanai.composepracticenew.service.FileOperationServiceController
import com.todokanai.composepracticenew.service.FtpForegroundService
import com.todokanai.composepracticenew.ui.thumbnail.addThumbnailComponents
import com.todokanai.composepracticenew.usecase.ProgressUseCase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MyApplication : Application(), ImageLoaderFactory {

    @Inject lateinit var progressUseCase: ProgressUseCase
    @Inject lateinit var fileOperationServiceController: FileOperationServiceController
    /** 연결 유지 서비스를 언제 시작할지 원격 연결 상태에서 정하는 규칙. */
    @Inject lateinit var connectionServicePolicy: ConnectionServicePolicy
    /** 연결 유지 서비스를 시작하는 컨트롤러. */
    @Inject lateinit var connectionServiceController: ConnectionServiceController
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        observeFileOperations()
        observeRemoteConnection()
    }

    // 앱 전체가 쓰는 Coil ImageLoader — 파일 목록 썸네일 요청 모델(Thumbnail)의 처리기를 등록한다
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { addThumbnailComponents() }
            .build()

    // progressMap이 비어있지 않을 때 서비스 시작, 비워지면 종료 — appScope로 앱 생명주기 동안 유지
    private fun observeFileOperations() {
        appScope.launch {
            progressUseCase.progressMap
                .map { it.isNotEmpty() }
                .distinctUntilChanged()
                .collect { hasActive ->
                    if (hasActive) fileOperationServiceController.start()
                    else fileOperationServiceController.stop()
                }
        }
    }

    // 연결되거나 연결한 서버가 바뀔 때마다 연결 유지 서비스를 시작(알림의 서버 이름 갱신) — 어느 경로로 연결하든 서비스가 연결 상태를 따른다.
    // 멈춤은 서비스가 연결되지 않은 상태를 보고 스스로 한다(startForeground 전에 여기서 멈추면 앱이 종료된다)
    private fun observeRemoteConnection() {
        appScope.launch {
            connectionServicePolicy.startRequests
                .collect { serverName -> connectionServiceController.start(serverName) }
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                FtpForegroundService.CHANNEL_ID,
                getString(R.string.ftp_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                FileOperationForegroundService.CHANNEL_ID,
                getString(R.string.file_operation_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                OperationConstants.CHANNEL_ID,
                getString(R.string.file_manager_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }
}
