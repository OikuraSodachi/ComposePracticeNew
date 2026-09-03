package com.todokanai.composepracticenew.tools

import com.todokanai.composepracticenew.myobjects.OperationConstants

/** 완료된 파일 작업 종류에 따라 알림 메시지를 분기하는 유틸리티. */
class CompletedNotiSorter(private val myNoti: MyNotification) {

    fun callback(actionKey: Int) = myNoti.completedNotification(
        title = "",
        message = sorter(actionKey)
    )

    private fun sorter(actionKey: Int): String {
        when (actionKey) {
            OperationConstants.ACTION_KEY_DELETE -> return deleteComplete()
            OperationConstants.ACTION_KEY_UNZIP -> return unzipComplete()
            OperationConstants.ACTION_KEY_COPY -> return copyComplete()
            OperationConstants.ACTION_KEY_MOVE -> return moveComplete()
            OperationConstants.ACTION_KEY_ZIP -> return zipComplete()
            else -> return "Invalid Action Key!!"
        }
    }

    private fun deleteComplete() = "Deleted"
    private fun copyComplete() = "Copied"
    private fun moveComplete() = "Moved"
    private fun zipComplete() = "Zipped"
    private fun unzipComplete() = "Unzipped"
}
