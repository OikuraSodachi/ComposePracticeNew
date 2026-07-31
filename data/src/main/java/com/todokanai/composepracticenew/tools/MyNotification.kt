package com.todokanai.composepracticenew.tools

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.todokanai.composepracticenew.myobjects.Constants.CHANNEL_ID
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

    fun deleteProgressNoti(progress: Int, total: Int) {
        notificationManager.createNotificationChannel(channel)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle("Deleting")
            .setContentText("$progress / $total")
            .setOngoing(true)
            .setProgress(100, 100 * progress / total, false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notify(0, builder.build())
                return
            }
            notify(0, builder.build())
        }
    }

    fun copyProgressNoti(progress: Int) = progressNoti("Copying", "$progress %", progress)
    fun moveProgressNoti(progress: Int) = progressNoti("Moving", "$progress %", progress)
    fun unzipProgressNoti(progress: Int) = progressNoti("Unzipping", "$progress %", progress)
    fun zipProgressNoti(progress: Int) = progressNoti("Zipping", "$progress %", progress)

    private fun progressNoti(title: String, message: String, progress: Int) {
        notificationManager.createNotificationChannel(channel)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setOngoing(progress < 100)
            .setContentText(message)
            .setProgress(100, progress, false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notify(0, builder.build())
            }
            notify(0, builder.build())
        }
    }

    fun completedNotification(title: String, message: String) {
        notificationManager.createNotificationChannel(channel)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setOngoing(false)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notify(0, builder.build())
            }
            notify(0, builder.build())
        }
    }
}
