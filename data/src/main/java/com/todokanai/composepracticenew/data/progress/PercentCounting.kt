package com.todokanai.composepracticenew.data.progress

/**
 * 모든 파일의 크기를 아는 작업의 계산 방식. 누적 바이트를 전체 크기에 대한 퍼센트로 바꾸고, 퍼센트가 바뀔 때만 보고한다.
 * @param totalBytes 작업 대상 전체 바이트 수(0 나눗셈 방지를 위해 1 이상)
 * @param holdBelow100 true이면 100 미만까지만 보고한다 — 100%는 [ByteProgressTracker]가 마지막 파일 완료 때 보고한다(서버 ACK 전 조기 100% 방지용)
 */
internal class PercentCounting(
    private val totalBytes: Long,
    private val holdBelow100: Boolean
) : ProgressCounting {

    /** 마지막으로 보고한 퍼센트 — 같은 퍼센트의 중복 보고를 막는다. 초기값 -1은 "아직 보고 없음". */
    private var prevProgress = -1

    override fun onFileStarted() {}

    override fun onBytes(bytes: Long, writtenBytes: Long, fileBytes: Long?, clampTo100: Boolean): ProgressReading? {
        val raw = (writtenBytes * 100 / totalBytes).toInt()
        val progress = if (clampTo100) raw.coerceIn(0, 100) else raw
        if (progress == prevProgress || (holdBelow100 && progress >= 100)) return null
        prevProgress = progress
        return ProgressReading(progress, writtenBytes.toFloat() / totalBytes, totalBytes)
    }

    override fun onFileCompleted(fileBytes: Long?) {}

    override fun completedBytes(writtenBytes: Long): Long = totalBytes
}
