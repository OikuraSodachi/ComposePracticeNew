package com.todokanai.composepracticenew.tools

import android.Manifest
import android.app.NotificationChannel
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
import javax.inject.Singleton

/** 파일 작업 진행/완료 알림을 발송하는 싱글톤. */
@Singleton
class MyNotification @Inject constructor(@ApplicationContext private val context: Context) {
    private val channelId = CHANNEL_ID
    private val icon = android.R.drawable.stat_sys_upload
    private val channel = NotificationChannel(channelId, "My Channel", NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "This is my notification channel"
    }
    private val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotification(title: String, message: String) {
        val isNotiEnabled = notificationManager.areNotificationsEnabled()
        if (isNotiEnabled) {
            notificationManager.createNotificationChannel(channel)
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
            notificationManager.createNotificationChannel(channel)
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

    fun deleteProgressNoti(progress: Int, total: Int, notifId: Int) {
        notificationManager.createNotificationChannel(channel)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle("Deleting")
            .setContentText("$progress / $total")
            .setOngoing(true)
            .setProgress(100, 100 * progress / total, false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(buildClickIntent(ACTION_KEY_DELETE))
        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            notify(notifId, builder.build())
        }
    }

    fun copyProgressNoti(progress: Int, notifId: Int) = progressNoti("Copying", "$progress %", progress, ACTION_KEY_COPY, notifId)
    fun moveProgressNoti(progress: Int, notifId: Int) = progressNoti("Moving", "$progress %", progress, ACTION_KEY_MOVE, notifId)
    fun unzipProgressNoti(progress: Int, notifId: Int) = progressNoti("Unzipping", "$progress %", progress, ACTION_KEY_UNZIP, notifId)
    fun zipProgressNoti(progress: Int, notifId: Int) = progressNoti("Zipping", "$progress %", progress, ACTION_KEY_ZIP, notifId)
    fun downloadProgressNoti(progress: Int, notifId: Int) = progressNoti("Downloading", "$progress %", progress, ACTION_KEY_DOWNLOAD, notifId)
    fun uploadProgressNoti(progress: Int, notifId: Int) = progressNoti("Uploading", "$progress %", progress, ACTION_KEY_UPLOAD, notifId)

    private fun progressNoti(title: String, message: String, progress: Int, actionKey: Int, notifId: Int) {
        notificationManager.createNotificationChannel(channel)
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

    fun completedNotification(title: String, message: String, actionKey: Int? = null, notifId: Int = actionKey ?: 0) {
        notificationManager.createNotificationChannel(channel)
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
    fun cancelNotification(notifId: Int) {
        notificationManager.cancel(notifId)
    }
}
