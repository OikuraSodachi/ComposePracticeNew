package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.ProgressState

/** 파일 작업 진행·완료·취소 시 시스템 알림을 발송하는 인터페이스. */
interface FileOperationNotifier {
    /** [state]에 담긴 작업 종류와 진행도로 진행 알림을 업데이트한다. */
    fun progressNoti(state: ProgressState, instanceId: Int)
    fun completedNotification(title: String, message: String, operationType: FileOperationType? = null, notifId: Int = operationType?.id ?: 0)
    fun cancelNotification(notifId: Int)
}
