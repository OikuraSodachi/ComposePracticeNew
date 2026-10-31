package com.todokanai.composepracticenew.data.progress

import com.todokanai.composepracticenew.model.KnownSizeProgress

/**
 * 크기를 모르는 파일이 섞여 전체 크기를 모르는 작업의 계산 방식. 퍼센트를 지어내지 않고, 크기를 아는 파일과 모르는 파일의 몫을 나눠
 * [throttle]의 시간 간격마다 보고한다.
 * 크기를 아는 파일의 몫은 처리 완료(서버 ACK)를 받았을 때만 확정한다 — 받는 중인 파일의 몫은 확정 몫과 따로 두고 다음 파일을 시작할 때 버리므로,
 * 실패한 파일은 확정 몫에 들어가지 않는다. 완료된 파일은 알려진 크기 전부로 센다.
 * 크기를 아는 부분의 합과 파일마다의 크기는 목록 조회 시점의 값이다. 그 뒤 크기가 바뀐 파일은 받는 중에는 그 파일의 몫이 실제와 어긋날 수 있다
 * (줄어든 파일은 알려진 크기에 닿지 않고, 커진 파일은 알려진 크기를 넘는 바이트를 세지 않는다).
 * @param total 크기를 아는 부분의 합과 크기를 모르는 파일 수
 * @param throttle 보고 간격을 정하는 ProgressThrottle
 */
internal class KnownSizeCounting(
    private val total: TransferTotal.Partial,
    private val throttle: ProgressThrottle
) : ProgressCounting {

    /** 처리 완료를 받은 크기를 아는 파일들의 알려진 크기 합. */
    private var completedKnownBytes = 0L
    /** 받는 중인 크기를 아는 파일의 몫(알려진 크기까지). 확정 전이므로 완료 응답이 없으면 다음 파일에서 버린다. */
    private var currentFileKnownBytes = 0L
    /** 크기를 모르는 파일들에서 처리한 바이트 수. */
    private var unknownWrittenBytes = 0L

    override fun onFileStarted() {
        currentFileKnownBytes = 0
    }

    override fun onBytes(bytes: Long, writtenBytes: Long, fileBytes: Long?): ProgressReading? {
        if (fileBytes == null) {
            unknownWrittenBytes += bytes
        } else {
            currentFileKnownBytes = (currentFileKnownBytes + bytes).coerceAtMost(fileBytes)
        }
        if (!throttle.shouldReport(null)) return null
        return reading()
    }

    /** 크기를 아는 파일의 확정은 파일마다 한 번이므로 [throttle]을 거치지 않고 바로 보고한다 — 다음 청크를 기다리면 확정이 늦게 보인다. */
    override fun onFileCompleted(fileBytes: Long?): ProgressReading? {
        currentFileKnownBytes = 0
        if (fileBytes == null) return null
        completedKnownBytes += fileBytes
        return reading()
    }

    /** 합계를 몰랐으므로 실제로 처리한 누적 바이트가 전체다. */
    override fun completedBytes(writtenBytes: Long): Long = writtenBytes

    /** 퍼센트 없이 크기를 아는 부분의 진행만 담은 진행 값을 만든다. */
    private fun reading() = ProgressReading(progress = null, progressFloat = null, totalBytes = null, knownSize = snapshot())

    /** 지금까지 센 몫으로 크기를 아는 부분의 진행을 만든다. */
    private fun snapshot() = KnownSizeProgress(
        knownTotalBytes = total.knownBytes,
        knownWrittenBytes = completedKnownBytes + currentFileKnownBytes,
        knownCompletedBytes = completedKnownBytes,
        unknownFileCount = total.unknownFileCount,
        unknownWrittenBytes = unknownWrittenBytes
    )
}
