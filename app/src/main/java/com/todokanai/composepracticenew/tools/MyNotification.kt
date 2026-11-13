package com.todokanai.composepracticenew.tools

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.service.notification.StatusBarNotification
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.bundleOf
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.myobjects.OperationConstants.CHANNEL_ID
import com.todokanai.composepracticenew.myobjects.OperationConstants.EXTRA_ACTION_KEY
import com.todokanai.composepracticenew.myobjects.OperationConstants.EXTRA_INSTANCE_ID
import com.todokanai.composepracticenew.myobjects.OperationConstants.EXTRA_PROCESS_TOKEN
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.ui.model.operationLabelRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 파일 작업 진행/완료 알림을 발송하는 싱글톤.
 * 작업 종류별 표시 방식은 [FileOperationType]에 대한 exhaustive `when`으로 매핑해 새 작업 추가 시 누락이 컴파일 단계에서 드러난다.
 * 작업의 알림 id와 PendingIntent requestCode는 작업 인스턴스 ID가 아니라 [OperationNotificationIds]가 붙인 알림 id다.
 * @param context 알림 생성·발송에 사용하는 애플리케이션 Context
 */
@Singleton
class MyNotification @Inject constructor(@ApplicationContext private val context: Context) : FileOperationNotifier {
    private val channelId = CHANNEL_ID
    private val icon = android.R.drawable.stat_sys_upload
    private val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** 작업별 알림 id. 이전 프로세스가 남긴 파일 작업 알림의 id를 피해 붙인다. */
    private val ids = OperationNotificationIds { activeOperationNotificationIds() }

    override fun progressNoti(state: ProgressState, instanceId: Int) {
        val type = state.operationType
        when (type) {
            FileOperationType.DELETE -> showCountProgress(type, state, instanceId)
            FileOperationType.COPY,
            FileOperationType.MOVE,
            FileOperationType.COMPRESS,
            FileOperationType.UNZIP,
            FileOperationType.DOWNLOAD,
            FileOperationType.UPLOAD,
            null -> showPercentProgress(type, state, instanceId)
        }
    }

    /**
     * 진행 알림을 지우고 답을 기다리는 알림을 새로 띄운다. 누르면 앱만 연다 — 질문은 화면이 보이면 화면 호스트가 띄우고, 진행률 다이얼로그를 함께 열면 질문과 겹친다.
     * 덮어쓰지 않고 지운 뒤 새로 띄우는 것은 [completedNotification]과 같은 이유다(직전 진행 갱신이 몰렸으면 갱신이 버려질 수 있음).
     * 답한 뒤의 진행 보고가 같은 알림을 진행 알림으로 되돌린다.
     */
    override fun awaitingAnswerNoti(operationType: FileOperationType, instanceId: Int) {
        cancelProgress(instanceId)
        val notificationId = ids.idFor(instanceId)
        val builder = progressBuilder(operationType)
            .setContentText(context.getString(R.string.noti_write_conflict_awaiting))
            .setOngoing(true)
            .setContentIntent(buildAppOnlyClickIntent(notificationId))
        notifyIfPermitted(PROGRESS_TAG, notificationId, builder)
    }

    /** 처리한 항목 수 / 전체 항목 수로 진행 알림을 표시한다. 항목 수 정보가 없으면 표시하지 않는다. */
    private fun showCountProgress(type: FileOperationType, state: ProgressState, instanceId: Int) {
        val idx = state.currentIndex ?: return
        val total = state.listSize ?: return
        val notificationId = ids.idFor(instanceId)
        val builder = progressBuilder(type)
            .setContentText("$idx / $total")
            .setOngoing(true)
            .setProgress(100, 100 * idx / total, false)
            .setContentIntent(buildClickIntent(type.id, instanceId, notificationId))
        notifyIfPermitted(PROGRESS_TAG, notificationId, builder)
    }

    /** 퍼센트 진행률로 진행 알림을 표시한다. 전체 크기를 몰라 진행률이 없으면 불확정 막대로 진행 중임만 표시한다. */
    private fun showPercentProgress(type: FileOperationType?, state: ProgressState, instanceId: Int) {
        val progress = state.progress
        val notificationId = ids.idFor(instanceId)
        val builder = progressBuilder(type)
            .setOngoing(progress == null || progress < 100)
            .setContentText(progress?.let { "$it %" })
            .setProgress(100, progress ?: 0, progress == null)
            .setContentIntent(buildClickIntent(type?.id ?: 0, instanceId, notificationId))
        notifyIfPermitted(PROGRESS_TAG, notificationId, builder)
    }

    /**
     * [type] 작업의 진행 알림(답을 기다리는 알림 포함)에 공통인 설정을 담은 Builder를 만든다.
     * 작업 종류를 알림 extras에 남겨, 이 프로세스가 죽은 뒤 다음 프로세스가 그 알림을 중단 알림으로 바꿀 때([interruptLeftoverProgress]) 제목을 정할 수 있게 한다.
     */
    private fun progressBuilder(type: FileOperationType?): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle(titleFor(type))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addExtras(bundleOf(EXTRA_ACTION_KEY to (type?.id ?: 0)))

    /** 작업 종류별 진행 알림 제목을 반환한다. 진행 다이얼로그와 같은 문구를 쓴다. */
    private fun titleFor(type: FileOperationType?): String = context.getString(operationLabelRes(type))

    /**
     * 알림 클릭 시 앱을 열고 [instanceId] 작업의 진행률 다이얼로그를 띄우는 PendingIntent를 만든다.
     * requestCode를 그 작업의 알림 id로 두어, 같은 종류의 작업이 여럿이어도 알림마다 자기 작업을 가리키는 intent를 갖게 한다.
     * 작업 인스턴스 ID는 이 프로세스에서만 유효하므로 이 프로세스의 값([NotificationProcessToken])을 함께 담는다.
     */
    private fun buildClickIntent(operationTypeId: Int, instanceId: Int, notificationId: Int): PendingIntent? {
        val intent = launchIntent()?.apply {
            putExtra(EXTRA_ACTION_KEY, operationTypeId)
            putExtra(EXTRA_INSTANCE_ID, instanceId)
            putExtra(EXTRA_PROCESS_TOKEN, NotificationProcessToken.value)
        } ?: return null
        return activityPendingIntent(notificationId, intent)
    }

    /**
     * 앱만 여는 PendingIntent를 만든다(완료·중단 알림, 답을 기다리는 알림). 끝난 작업은 진행 상태가 지워져 띄울 진행률 다이얼로그가 없고, 답을 기다리는 작업은 질문이 진행률 다이얼로그와 겹치므로 작업을 가리키지 않는다.
     * requestCode는 진행 알림과 같은 그 작업의 알림 id라 같은 PendingIntent를 덮어쓴다 — 완료 때 진행 알림은 취소되므로 그 작업 extras를 쓰는 알림은 남지 않는다.
     */
    private fun buildAppOnlyClickIntent(notificationId: Int): PendingIntent? =
        launchIntent()?.let { activityPendingIntent(notificationId, it) }

    /** 앱의 시작 Activity를 여는 intent. 앱이 이미 열려 있으면 새로 쌓지 않고 그 Activity로 돌아간다. */
    private fun launchIntent(): Intent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    /** [intent]로 Activity를 여는 PendingIntent를 알림 id [notificationId]를 requestCode로 만든다. 같은 requestCode의 이전 intent는 덮어쓴다. */
    private fun activityPendingIntent(notificationId: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /**
     * 알림 권한이 있을 때만 [builder]의 알림을 알림 id [notificationId]의 [tag] 알림으로 발송한다.
     * 알림 정체성은 (tag, id) 쌍이므로 태그를 붙여, 같은 정수를 쓰는 포그라운드 서비스 알림(태그 없음)을 덮어쓰지 않게 한다.
     */
    private fun notifyIfPermitted(tag: String, notificationId: Int, builder: NotificationCompat.Builder) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        NotificationManagerCompat.from(context).notify(tag, notificationId, builder.build())
    }

    /** 알림함에 남아 있는 이 앱의 파일 작업 알림(진행·완료)의 id들. 이전 프로세스가 남긴 알림도 포함한다. */
    private fun activeOperationNotificationIds(): List<Int> =
        notificationManager.activeNotifications
            .filter { it.tag == PROGRESS_TAG || it.tag == COMPLETED_TAG }
            .map { it.id }

    /**
     * 진행 알림을 취소하고 완료 알림을 새 알림으로 띄운다. 누르면 앱을 열고 알림을 지운다 — 모든 알림이 탭에 반응하도록 한다.
     * 진행 알림을 덮어쓰지 않는 것은, 시스템이 앱별 알림 갱신 빈도 한도를 넘은 갱신을 말없이 버리기 때문이다(진행이 끝난 진행 막대만 예외).
     * 진행 보고가 몰린 직후 완료를 갱신으로 보내면 버려져 진행 알림이 남을 수 있다. 취소와 새 알림은 이 한도를 받지 않고,
     * 취소는 아직 게시 전인 같은 알림의 갱신이 게시된 뒤에 적용된다.
     */
    override fun completedNotification(message: String, instanceId: Int) {
        cancelProgress(instanceId)
        val notificationId = ids.idFor(instanceId)
        notifyIfPermitted(COMPLETED_TAG, notificationId, finishedBuilder(notificationId).setContentText(message))
        ids.release(instanceId)
    }

    /** 작업 [instanceId]의 진행 알림을 취소한다. */
    override fun cancelNotification(instanceId: Int) {
        cancelProgress(instanceId)
        ids.release(instanceId)
    }

    /**
     * 이전 프로세스가 남긴 진행 알림을 중단 알림으로 바꾼다. 프로세스가 시작될 때, 이 프로세스가 진행 알림을 띄우기 전에 한 번 부른다 —
     * 그때 남아 있는 진행 알림은 모두 이미 죽은 프로세스의 작업이다(#300).
     * 그 작업을 실행하던 프로세스가 없어 알림을 지울 곳이 없으므로, 끝나지 않은 작업이 진행 중으로 보이거나 밀어서 지울 수 없는 채로 남지 않고 중단됐다고 알린다.
     * 같은 알림 id를 쓰므로 그 id의 클릭 intent도 앱만 여는 intent로 바뀐다. 프로세스가 죽은 뒤 다시 시작되기 전까지 남은 진행 알림은 정리하지 못한다(지원 범위 밖).
     */
    fun interruptLeftoverProgress() {
        notificationManager.activeNotifications
            .filter { it.tag == PROGRESS_TAG }
            .forEach { leftover ->
                notificationManager.cancel(PROGRESS_TAG, leftover.id)
                val builder = finishedBuilder(leftover.id)
                    .setContentTitle(titleFor(operationTypeOf(leftover)))
                    .setContentText(context.getString(R.string.noti_operation_interrupted))
                notifyIfPermitted(COMPLETED_TAG, leftover.id, builder)
            }
    }

    /** 끝난 작업(완료·중단)의 알림에 공통인 설정을 담은 Builder를 만든다. 밀어서 지울 수 있고, 누르면 앱을 열고 알림을 지운다. */
    private fun finishedBuilder(notificationId: Int): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(buildAppOnlyClickIntent(notificationId))
            .setAutoCancel(true)

    /** [notification] 진행 알림이 extras에 남긴 작업 종류. 남기지 않은 알림(작업 종류가 없거나 이전 버전이 띄운 알림)이면 null이다. */
    private fun operationTypeOf(notification: StatusBarNotification): FileOperationType? =
        FileOperationType.fromId(notification.notification.extras.getInt(EXTRA_ACTION_KEY, 0))

    /** 작업 [instanceId]의 진행 알림을 취소한다. */
    private fun cancelProgress(instanceId: Int) {
        notificationManager.cancel(PROGRESS_TAG, ids.idFor(instanceId))
    }

    private companion object {
        /** 파일 작업 진행 알림의 태그. 작업별 알림 id를 쓰는 알림들을 태그 없는 다른 알림과 구별한다. */
        const val PROGRESS_TAG = "file_operation"

        /** 끝난 파일 작업(완료·중단) 알림의 태그. 진행 알림과 다른 알림이 되도록 [PROGRESS_TAG]와 구별한다. */
        const val COMPLETED_TAG = "file_operation_completed"
    }
}
