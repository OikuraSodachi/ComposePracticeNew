package com.todokanai.composepracticenew.repository

/** 파일 작업 진행·완료·취소 시 시스템 알림을 발송하는 인터페이스. */
interface FileOperationNotifier {
    fun copyProgressNoti(progress: Int, notifId: Int)
    fun moveProgressNoti(progress: Int, notifId: Int)
    fun deleteProgressNoti(progress: Int, total: Int, notifId: Int)
    fun unzipProgressNoti(progress: Int, notifId: Int)
    fun zipProgressNoti(progress: Int, notifId: Int)
    fun downloadProgressNoti(progress: Int, notifId: Int)
    fun uploadProgressNoti(progress: Int, notifId: Int)
    fun completedNotification(title: String, message: String, actionKey: Int? = null, notifId: Int = actionKey ?: 0)
    fun cancelNotification(notifId: Int)
}
