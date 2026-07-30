package com.todokanai.composepracticenew.tools.wrapper

import com.todokanai.composepracticenew.tools.MyNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 *  onComplete를 추가하기 위한 class
 *
 *  notification 내용은 여기서 설정(?)
 */
class ActionWrapper(val actionKey: Int, private val myNoti: MyNotification) {

    /** path에 null 입력시 update 미발생
     *
     * message에 null 입력시 알림은 미발생
     */

    fun wrapper(

        action: () -> Unit,
        updateCurrentPath:(File)->Unit,
        pathToRefresh: File?,
        message:String?
    ){
        CoroutineScope(Dispatchers.IO).launch {
            action()
        }.invokeOnCompletion {
            onCompleteCallback(
                pathToRefresh = pathToRefresh,
                message = message,
                updateCurrentPath = updateCurrentPath
            )
        }
    }

    private fun onCompleteCallback(
        pathToRefresh:File?,
        message: String?,
        updateCurrentPath: (File) -> Unit
    ){
        pathToRefresh?.let{
            updateCurrentPath(it)
        }
        message?.let{
            myNoti.completedNotification(
                title = "",
                message = it
            )
        }

    }
}