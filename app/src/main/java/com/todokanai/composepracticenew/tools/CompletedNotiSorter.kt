package com.todokanai.composepracticenew.tools

import com.todokanai.composepracticenew.myobjects.Constants

class CompletedNotiSorter(private val myNoti: MyNotification) {

    fun callback(actionKey: Int) = myNoti.completedNotification(
        title = "",
        message = sorter(actionKey)
    )

    private fun sorter(actionKey: Int): String {
        when (actionKey) {
            Constants.ACTION_KEY_DELETE -> return deleteComplete()
            Constants.ACTION_KEY_UNZIP -> return unzipComplete()
            Constants.ACTION_KEY_COPY -> return copyComplete()
            Constants.ACTION_KEY_MOVE -> return moveComplete()
            Constants.ACTION_KEY_ZIP -> return zipComplete()
            else -> return "Invalid Action Key!!"
        }
    }

    private fun deleteComplete() = "Deleted"
    private fun copyComplete() = "Copied"
    private fun moveComplete() = "Moved"
    private fun zipComplete() = "Zipped"
    private fun unzipComplete() = "Unzipped"
}
