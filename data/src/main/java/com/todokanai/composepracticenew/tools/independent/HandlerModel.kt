package com.todokanai.composepracticenew.tools.independent

import android.os.Handler
import android.os.Looper

/** CoroutineScope에서 Main Thread 접근을 위한 유틸리티. */
class HandlerModel {
    fun myHandler(input: () -> Unit, delayMills: Long) {
        Handler(Looper.getMainLooper()).postDelayed({ input() }, delayMills)
    }
}
