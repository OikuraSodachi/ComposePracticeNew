package com.todokanai.composepracticenew.tools

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.myobjects.OperationConstants.CHANNEL_ID
import com.todokanai.composepracticenew.myobjects.OperationConstants.EXTRA_ACTION_KEY
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 파일 작업 진행/완료 알림을 발송하는 싱글톤.
 * 작업 종류별 표시 방식은 [FileOperationType]에 대한 exhaustive `when`으로 매핑해 새 작업 추가 시 누락이 컴파일 단계에서 드러난다.
 * @param context 알림 생성·발송에 사용하는 애플리케이션 Context
 */
@Singleton
class MyNotification @Inject constructor(@ApplicationContext private val context: Context) : FileOperationNotifier {
    private val channelId = CHANNEL_ID
    private val icon = android.R.drawable.stat_sys_upload
    private val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun progressNoti(state: ProgressState, instanceId: Int) {
        val type = state.operationType
        when (type) {
            FileOperationType.DELETE -> showCountProgress(type, state, instanceId)
            FileOperationType.COPY,
            FileOperationType.MOVE,
            FileOperationType.ZIP,
            FileOperationType.UNZIP,
            FileOperationType.DOWNLOAD,
            FileOperationType.UPLOAD,
            null -> showPercentProgress(type, state, instanceId)
        }
    }

    /** 처리한 항목 수 / 전체 항목 수로 진행 알림을 표시한다. 항목 수 정보가 없으면 표시하지 않는다. */
    private fun showCountProgress(type: FileOperationType, state: ProgressState, notifId: Int) {
        val idx = state.currentIndex ?: return
        val total = state.listSize ?: return
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle(titleFor(type))
            .setContentText("$idx / $total")
            .setOngoing(true)
            .setProgress(100, 100 * idx / total, false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(buildClickIntent(type.id))
        notifyIfPermitted(notifId, builder)
    }

    /** 퍼센트 진행률로 진행 알림을 표시한다. 진행률 정보가 없으면 표시하지 않는다. */
    private fun showPercentProgress(type: FileOperationType?, state: ProgressState, notifId: Int) {
        val progress = state.progress ?: return
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle(titleFor(type))
            .setOngoing(progress < 100)
            .setContentText("$progress %")
            .setProgress(100, progress, false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(buildClickIntent(type?.id ?: 0))
        notifyIfPermitted(notifId, builder)
    }

    /** 작업 종류별 진행 알림 제목을 반환한다. */
    private fun titleFor(type: FileOperationType?): String = when (type) {
        FileOperationType.COPY     -> "Copying"
        FileOperationType.MOVE     -> "Moving"
        FileOperationType.DELETE   -> "Deleting"
        FileOperationType.ZIP      -> "Zipping"
        FileOperationType.UNZIP    -> "Unzipping"
        FileOperationType.DOWNLOAD -> "Downloading"
        FileOperationType.UPLOAD   -> "Uploading"
        null                       -> "Processing"
    }

    /** 알림 클릭 시 앱을 열고 [operationTypeId] 작업의 진행률 다이얼로그를 복원하는 PendingIntent를 만든다. */
    private fun buildClickIntent(operationTypeId: Int): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ACTION_KEY, operationTypeId)
        } ?: return null
        return PendingIntent.getActivity(
            context, operationTypeId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 알림 권한이 있을 때만 [builder]의 알림을 [notifId]로 발송한다. */
    private fun notifyIfPermitted(notifId: Int, builder: NotificationCompat.Builder) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        NotificationManagerCompat.from(context).notify(notifId, builder.build())
    }

    /**
     * ForegroundService용 지속 알림을 생성한다.
     * @param channelId 알림 채널 id
     * @param title 알림 제목
     * @param body 알림 본문
     * @return 생성된 Notification
     */
    fun buildForegroundNotification(channelId: String, title: String, body: String): Notification =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .build()

    override fun completedNotification(title: String, message: String, operationType: FileOperationType?, notifId: Int) {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setOngoing(false)
            .setContentText(message)
            .setProgress(0, 0, false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        notifyIfPermitted(notifId, builder)
    }

    /** 지정한 ID의 알림을 취소한다. */
    override fun cancelNotification(notifId: Int) {
        notificationManager.cancel(notifId)
    }
}
