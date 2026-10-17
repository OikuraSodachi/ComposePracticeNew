package com.todokanai.composepracticenew.data.progress

/**
 * 청크 단위로 들어오는 진행 상태의 보고 여부를 결정한다. 퍼센트가 바뀌었거나 직전 보고 후 [intervalNanos]가 지났을 때만 보고한다.
 * 퍼센트가 없는(전체 크기 미확인) 경우에는 시간 기준만 적용한다.
 * @param intervalNanos 같은 퍼센트에서 다시 보고하기까지의 최소 간격(나노초)
 * @param now 현재 시각(나노초)을 반환하는 함수
 */
class ProgressThrottle(
    private val intervalNanos: Long = DEFAULT_INTERVAL_NANOS,
    private val now: () -> Long = System::nanoTime
) {
    /** 마지막으로 보고한 퍼센트. 초기값 -1은 "아직 보고 없음". */
    private var prevProgress = -1
    /** 마지막 보고 시각. null이면 아직 보고 없음. */
    private var lastReportAt: Long? = null

    /**
     * 이번 진행 상태를 보고해야 하면 true를 반환하고 보고 기록을 갱신한다.
     * @param progress 현재 퍼센트. null이면 시간 기준만 적용한다
     * @return 보고해야 하면 true
     */
    fun shouldReport(progress: Int?): Boolean {
        val time = now()
        val last = lastReportAt
        val percentChanged = progress != null && progress != prevProgress
        val intervalElapsed = last == null || time - last >= intervalNanos
        if (!percentChanged && !intervalElapsed) return false
        if (progress != null) prevProgress = progress
        lastReportAt = time
        return true
    }

    companion object {
        /** 기본 최소 보고 간격 — 200ms. */
        const val DEFAULT_INTERVAL_NANOS = 200_000_000L
    }
}
