package com.todokanai.composepracticenew.tools

import com.todokanai.composepracticenew.myobjects.OperationConstants

/**
 * 완료된 파일 작업 종류에 따라 알림 메시지를 분기하는 유틸리티.
 * @param myNoti 완료 알림 전송에 사용하는 MyNotification 인스턴스
 */
class CompletedNotiSorter(private val myNoti: MyNotification) {

    fun callback(actionKey: Int) = myNoti.completedNotification(
        title = "",
        message = sorter(actionKey)
    )

    private val messagesByKey: Map<Int, () -> String> = mapOf(
        OperationConstants.ACTION_KEY_DELETE to ::deleteComplete,
        OperationConstants.ACTION_KEY_UNZIP  to ::unzipComplete,
        OperationConstants.ACTION_KEY_COPY   to ::copyComplete,
        OperationConstants.ACTION_KEY_MOVE   to ::moveComplete,
        OperationConstants.ACTION_KEY_ZIP    to ::zipComplete
    )

    private fun sorter(actionKey: Int): String = messagesByKey[actionKey]?.invoke() ?: "Invalid Action Key!!"

    private fun deleteComplete() = "Deleted"
    private fun copyComplete() = "Copied"
    private fun moveComplete() = "Moved"
    private fun zipComplete() = "Zipped"
    private fun unzipComplete() = "Unzipped"
}
