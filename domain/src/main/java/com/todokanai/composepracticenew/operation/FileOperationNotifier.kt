package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.ProgressState

/**
 * 파일 작업 진행·완료·취소 시 시스템 알림을 발송하는 인터페이스.
 * 알림은 작업 인스턴스 ID로만 가리킨다. 한 작업은 알림 하나를 갖고, 그 ID를 어떤 알림 식별자로 쓸지는 구현이 정한다 —
 * 호출부가 알림 식별자를 고르지 않으므로 다른 알림과 식별자가 겹치는 것을 막는 책임도 구현 한 곳에 있다.
 */
interface FileOperationNotifier {
    /** [state]에 담긴 작업 종류와 진행도로 [instanceId] 작업의 진행 알림을 업데이트한다. */
    fun progressNoti(state: ProgressState, instanceId: Int)

    /** [instanceId] 작업의 알림을 [message]를 담은 완료 알림으로 띄운다. 진행 알림이 있었으면 지우고 완료 알림으로 대신한다. 그 작업에 대한 마지막 호출이다. */
    fun completedNotification(message: String, instanceId: Int)

    /** 완료하지 못한 [instanceId] 작업의 알림을 지운다. 그 작업에 대한 마지막 호출이다. */
    fun cancelNotification(instanceId: Int)
}
