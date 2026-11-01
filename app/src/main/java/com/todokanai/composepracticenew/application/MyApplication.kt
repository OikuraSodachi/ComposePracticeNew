package com.todokanai.composepracticenew.application

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.di.ApplicationScope
import com.todokanai.composepracticenew.repository.ProgressRepository
import com.todokanai.composepracticenew.myobjects.OperationConstants
import com.todokanai.composepracticenew.service.FileOperationForegroundService
import com.todokanai.composepracticenew.service.FileOperationServiceController
import com.todokanai.composepracticenew.service.FtpForegroundService
import com.todokanai.composepracticenew.ui.thumbnail.VideoThumbnailFetcher
import com.todokanai.composepracticenew.ui.thumbnail.VideoThumbnailKeyer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MyApplication : Application(), ImageLoaderFactory {

    @Inject lateinit var progressRepo: ProgressRepository
    @Inject lateinit var fileOperationServiceController: FileOperationServiceController
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        observeFileOperations()
    }

    // 앱 전체가 쓰는 Coil ImageLoader — 동영상 썸네일 모델(VideoThumbnail)을 불러오고 메모리 캐시에 담을 수 있도록 등록한다
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(VideoThumbnailFetcher.Factory())
                add(VideoThumbnailKeyer())
            }
            .build()

    // progressMap이 비어있지 않을 때 서비스 시작, 비워지면 종료 — appScope로 앱 생명주기 동안 유지
    private fun observeFileOperations() {
        appScope.launch {
            progressRepo.progressMap
                .map { it.isNotEmpty() }
                .distinctUntilChanged()
                .collect { hasActive ->
                    if (hasActive) fileOperationServiceController.start()
                    else fileOperationServiceController.stop()
                }
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
