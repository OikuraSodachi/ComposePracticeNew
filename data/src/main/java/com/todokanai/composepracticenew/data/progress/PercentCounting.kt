package com.todokanai.composepracticenew.data.progress

/**
 * 모든 파일의 크기를 아는 작업의 계산 방식. 누적 바이트를 전체 크기에 대한 퍼센트로 바꾸고, 퍼센트가 바뀌었거나 [throttle]의 시간 간격이 지났을 때 보고한다 —
 * 큰 파일에서 같은 퍼센트가 오래 이어져도 누적 바이트 표시가 멈춘 것처럼 보이지 않도록.
 * 100%는 끝났다는 뜻이므로 진행 중에는 [MAX_PERCENT_BEFORE_COMPLETION]까지만 낸다 — 100%는 [ByteProgressTracker]가 작업을 마칠 때 보고한다.
 * 마지막 바이트를 처리한 뒤에도 남은 일(서버 ACK, 쓰기 마무리)이 있고, 처리 중 크기가 바뀌어 누적 바이트가 전체 크기를 넘을 수도 있기 때문이다.
 * 전체 크기가 0이면(모두 빈 파일) 나눌 기준이 없어 진행 중에는 퍼센트를 내지 않는다.
 * @param totalBytes 작업 대상 전체 바이트 수(0 이상)
 * @param throttle 같은 퍼센트에서 다시 보고하기까지의 간격을 정하는 ProgressThrottle
 */
internal class PercentCounting(
    private val totalBytes: Long,
    private val throttle: ProgressThrottle
) : ProgressCounting {

    override fun startReading(): ProgressReading = ProgressReading(0, totalBytes)

    override fun onFileStarted() {}

    override fun onBytes(bytes: Long, writtenBytes: Long, fileBytes: Long?): ProgressReading? {
        if (totalBytes == 0L) return null
        val progress = percentBeforeCompletion(writtenBytes)
        if (!throttle.shouldReport(progress)) return null
        return ProgressReading(progress, totalBytes)
    }

    override fun onFileCompleted(fileBytes: Long?): ProgressReading? = null

    override fun completedBytes(writtenBytes: Long): Long = totalBytes

    /**
     * 진행 중인 작업의 퍼센트를 구한다.
     * @param writtenBytes 작업 전체의 누적 바이트 수
     * @return 0~[MAX_PERCENT_BEFORE_COMPLETION] 퍼센트
     */
    private fun percentBeforeCompletion(writtenBytes: Long): Int =
        (writtenBytes * 100 / totalBytes).toInt().coerceIn(0, MAX_PERCENT_BEFORE_COMPLETION)

    private companion object {
        /** 작업을 마치기 전에 보고하는 퍼센트의 최댓값. */
        const val MAX_PERCENT_BEFORE_COMPLETION = 99
    }
}
