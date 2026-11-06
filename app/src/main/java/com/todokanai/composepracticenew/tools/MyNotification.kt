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
import com.todokanai.composepracticenew.myobjects.OperationConstants.EXTRA_INSTANCE_ID
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.ui.model.operationLabelRes
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
    private fun showCountProgress(type: FileOperationType, state: ProgressState, instanceId: Int) {
        val idx = state.currentIndex ?: return
        val total = state.listSize ?: return
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle(titleFor(type))
            .setContentText("$idx / $total")
            .setOngoing(true)
            .setProgress(100, 100 * idx / total, false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(buildClickIntent(type.id, instanceId))
        notifyIfPermitted(instanceId, builder)
    }

    /** 퍼센트 진행률로 진행 알림을 표시한다. 전체 크기를 몰라 진행률이 없으면 불확정 막대로 진행 중임만 표시한다. */
    private fun showPercentProgress(type: FileOperationType?, state: ProgressState, instanceId: Int) {
        val progress = state.progress
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle(titleFor(type))
            .setOngoing(progress == null || progress < 100)
            .setContentText(progress?.let { "$it %" })
            .setProgress(100, progress ?: 0, progress == null)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(buildClickIntent(type?.id ?: 0, instanceId))
        notifyIfPermitted(instanceId, builder)
    }

    /** 작업 종류별 진행 알림 제목을 반환한다. 진행 다이얼로그와 같은 문구를 쓴다. */
    private fun titleFor(type: FileOperationType?): String = context.getString(operationLabelRes(type))

    /**
     * 알림 클릭 시 앱을 열고 [instanceId] 작업의 진행률 다이얼로그를 띄우는 PendingIntent를 만든다.
     * requestCode를 작업 인스턴스 ID로 두어, 같은 종류의 작업이 여럿이어도 알림마다 자기 작업을 가리키는 intent를 갖게 한다.
     */
    private fun buildClickIntent(operationTypeId: Int, instanceId: Int): PendingIntent? {
        val intent = launchIntent()?.apply {
            putExtra(EXTRA_ACTION_KEY, operationTypeId)
            putExtra(EXTRA_INSTANCE_ID, instanceId)
        } ?: return null
        return activityPendingIntent(instanceId, intent)
    }

    /**
     * 완료 알림 클릭 시 앱만 여는 PendingIntent를 만든다. 끝난 작업은 진행 상태가 지워져 띄울 진행률 다이얼로그가 없으므로 작업을 가리키지 않는다.
     * requestCode는 진행 알림과 같은 작업 인스턴스 ID다 — 완료 알림이 그 작업의 진행 알림 자리를 대신하므로, 진행 알림의 intent가 남지 않게 덮어쓴다.
     */
    private fun buildCompletedClickIntent(instanceId: Int): PendingIntent? =
        launchIntent()?.let { activityPendingIntent(instanceId, it) }

    /** 앱의 시작 Activity를 여는 intent. 앱이 이미 열려 있으면 새로 쌓지 않고 그 Activity로 돌아간다. */
    private fun launchIntent(): Intent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    /** [intent]로 Activity를 여는 PendingIntent를 [requestCode]로 만든다. 같은 requestCode의 이전 intent는 덮어쓴다. */
    private fun activityPendingIntent(requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /**
     * 알림 권한이 있을 때만 [builder]의 알림을 작업 [instanceId]의 알림으로 발송한다.
     * 알림 정체성은 (tag, id) 쌍이므로 [OPERATION_TAG]를 붙여, 같은 정수를 쓰는 포그라운드 서비스 알림(태그 없음)을 덮어쓰지 않게 한다.
     */
    private fun notifyIfPermitted(instanceId: Int, builder: NotificationCompat.Builder) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        NotificationManagerCompat.from(context).notify(OPERATION_TAG, instanceId, builder.build())
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

    /** 누르면 앱을 열고 알림을 지운다 — 모든 알림이 탭에 반응하도록 한다. */
    override fun completedNotification(message: String, instanceId: Int) {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setOngoing(false)
            .setContentText(message)
            .setProgress(0, 0, false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(buildCompletedClickIntent(instanceId))
            .setAutoCancel(true)
        notifyIfPermitted(instanceId, builder)
    }

    /** 작업 [instanceId]의 알림을 취소한다. */
    override fun cancelNotification(instanceId: Int) {
        notificationManager.cancel(OPERATION_TAG, instanceId)
    }

    private companion object {
        /** 파일 작업 알림의 태그. 작업 인스턴스 ID를 알림 id로 그대로 쓰는 알림들을 태그 없는 다른 알림과 구별한다. */
        const val OPERATION_TAG = "file_operation"
    }
}
