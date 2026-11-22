package com.todokanai.composepracticenew.operation

/**
 * 앱 수명을 지키려고 실패로 바꾼 구현 오류를 기록하는 인터페이스. 기록만 하며 복구하지 않는다.
 * 사용자에게는 일반 실패 문구만 보이므로, 원인을 찾을 단서는 이 기록에만 남는다.
 */
fun interface UnexpectedFailureRecorder {
    /**
     * [error]를 기록한다.
     * @param error 실패로 바꾼 구현 오류
     */
    fun record(error: Throwable)
}
