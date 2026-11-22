package com.todokanai.composepracticenew.tools

import android.util.Log
import com.todokanai.composepracticenew.operation.UnexpectedFailureRecorder
import javax.inject.Inject

private const val TAG = "UnexpectedFailure"

/** 실패로 바꾼 구현 오류를 스택과 함께 Logcat에 기록하는 UnexpectedFailureRecorder 구현체. */
class LogUnexpectedFailureRecorder @Inject constructor() : UnexpectedFailureRecorder {
    override fun record(error: Throwable) {
        Log.e(TAG, "작업 중 예상하지 못한 오류", error)
    }
}
