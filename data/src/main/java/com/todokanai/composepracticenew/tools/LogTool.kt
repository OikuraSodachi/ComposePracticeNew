package com.todokanai.composepracticenew.tools

import android.content.Context
import android.widget.Toast
import com.todokanai.composepracticenew.tools.independent.HandlerModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** Toast 및 디버그 로그 출력 유틸리티. */
@Singleton
class LogTool @Inject constructor(@ApplicationContext private val context: Context) {
    private val hModel = HandlerModel()

    fun makeShortToast(message: String) {
        hModel.myHandler({ Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }, 0)
    }

    suspend fun printDispatcherInfo() {
        val thread = coroutineContext
        withContext(Dispatchers.Main) {
            println("coroutineContext: $thread")
        }
    }
}
