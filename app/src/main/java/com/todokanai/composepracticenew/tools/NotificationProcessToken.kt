package com.todokanai.composepracticenew.tools

import java.util.UUID

/**
 * 알림 클릭 intent가 이 프로세스에서 만든 것인지 가려내는 값.
 * 작업 인스턴스 ID는 프로세스마다 다시 발급되지만 알림과 그 intent는 프로세스보다 오래 남으므로,
 * 작업을 가리키는 intent에 이 값을 함께 담고 받을 때 비교해 이전 프로세스의 intent가 이 프로세스의 다른 작업을 가리키지 않게 한다(#300).
 */
internal object NotificationProcessToken {

    /** 이 프로세스에서 처음 쓸 때 한 번 정하는 임의 값. 다른 프로세스의 값과 겹치지 않는다. */
    val value: String = UUID.randomUUID().toString()

    /**
     * [token]이 이 프로세스에서 만든 intent의 값인지 판정한다.
     * @param token intent에 담긴 값. 없으면 null
     * @return 이 프로세스의 값이면 true
     */
    fun isFromThisProcess(token: String?): Boolean = token == value
}
