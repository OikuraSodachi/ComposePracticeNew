package com.todokanai.composepracticenew.tools

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_COPY
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_DELETE
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_DOWNLOAD
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_MOVE
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_UNZIP
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_UPLOAD
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_ZIP
import com.todokanai.composepracticenew.myobjects.OperationConstants.CHANNEL_ID
import com.todokanai.composepracticenew.myobjects.OperationConstants.EXTRA_ACTION_KEY
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import javax.inject.Singleton

/** 파일 작업 진행/완료 알림을 발송하는 싱글톤. */
@Singleton
class MyNotification @Inject constructor(@ApplicationContext private val context: Context) : FileOperationNotifier {
    private val channelId = CHANNEL_ID
    private val icon = android.R.drawable.stat_sys_upload
    private val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotification(title: String, message: String) {
        val isNotiEnabled = notificationManager.areNotificationsEnabled()
        if (isNotiEnabled) {
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            with(NotificationManagerCompat.from(context)) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    return
                }
                notify(0, builder.build())
            }
        }
    }

    fun createOngoingNotification(title: String, message: String) {
        val isNotiEnabled = notificationManager.areNotificationsEnabled()
        if (isNotiEnabled) {
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setOngoing(true)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            with(NotificationManagerCompat.from(context)) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    return
                }
                notify(0, builder.build())
            }
        }
    }

    override fun progressNoti(state: ProgressState, instanceId: Int) {
        when (state.actionKey) {
            ACTION_KEY_DELETE -> {
                val idx = state.currentIndex ?: return
                val total = state.listSize ?: return

                val builder = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(icon)
                    .setContentTitle("Deleting")
                    .setContentText("$idx / $total")
                    .setOngoing(true)
                    .setProgress(100, 100 * idx / total, false)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(buildClickIntent(ACTION_KEY_DELETE))
                with(NotificationManagerCompat.from(context)) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
                    notify(instanceId, builder.build())
                }
            }
            else -> {
                val progress = state.progress ?: return
                showProgressNoti(titleFor(state.actionKey), "$progress %", progress, state.actionKey ?: 0, instanceId)
            }
        }
    }

    private fun titleFor(actionKey: Int?): String = when (actionKey) {
        ACTION_KEY_COPY -> "Copying"
        ACTION_KEY_MOVE -> "Moving"
        ACTION_KEY_UNZIP -> "Unzipping"
        ACTION_KEY_ZIP -> "Zipping"
        ACTION_KEY_DOWNLOAD -> "Downloading"
        ACTION_KEY_UPLOAD -> "Uploading"
        else -> "Processing"
    }

    private fun showProgressNoti(title: String, message: String, progress: Int, actionKey: Int, notifId: Int) {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setOngoing(progress < 100)
            .setContentText(message)
            .setProgress(100, progress, false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(buildClickIntent(actionKey))
        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            notify(notifId, builder.build())
        }
    }

    private fun buildClickIntent(actionKey: Int): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ACTION_KEY, actionKey)
        } ?: return null
        return PendingIntent.getActivity(
            context, actionKey, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun completedNotification(title: String, message: String, actionKey: Int?, notifId: Int) {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setOngoing(false)
            .setContentText(message)
            .setProgress(0, 0, false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            notify(notifId, builder.build())
        }
    }

    /** 지정한 ID의 알림을 취소한다. */
    override fun cancelNotification(notifId: Int) {
        notificationManager.cancel(notifId)
    }
}
