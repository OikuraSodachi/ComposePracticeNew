package com.todokanai.composepracticenew.data.progress

import com.todokanai.composepracticenew.model.KnownSizeProgress
import com.todokanai.composepracticenew.model.ProgressState
import java.io.InputStream
import java.io.OutputStream

/**
 * 여러 파일에 걸친 바이트 단위 작업(로컬 복사·이동·압축·압축해제, FTP 디렉터리 전송)의 누적 진행률을 계산하고,
 * 퍼센트가 바뀔 때만 보고한다. 로컬 작업과 FTP 전송이 같은 계산 규칙을 공유하며, 차이는 생성 옵션으로만 표현한다.
 * 전체 크기를 모르면([TransferTotal.Partial]) 퍼센트를 지어내지 않는다 — 진행률 없이(null) 누적 바이트와 파일 순번, 크기를 아는 부분의 진행을
 * [throttle]의 시간 간격마다 보고하고, 100%는 마지막 파일 완료 때만 보고한다.
 * @param total 작업 전체의 크기
 * @param totalFileCount 작업 대상 파일 개수
 * @param onProgress 진행 상태를 수신하는 콜백
 * @param holdBelow100 true이면 바이트 기반 보고를 100 미만까지만 하고, 100%는 [onFileCompleted]에서 마지막 파일 완료 시에만 보고한다(서버 ACK 전 조기 100% 방지용)
 * @param reportProgressFloat true이면 보고에 progressFloat(0.0~1.0)을 포함한다
 * @param throttle 전체 크기를 모를 때 보고 간격을 정하는 ProgressThrottle
 */
class ByteProgressTracker internal constructor(
    private val total: TransferTotal,
    private val totalFileCount: Int,
    private val onProgress: (ProgressState) -> Unit,
    private val holdBelow100: Boolean = false,
    private val reportProgressFloat: Boolean = false,
    private val throttle: ProgressThrottle = ProgressThrottle()
) {
    /**
     * 모든 파일의 크기를 아는 작업의 계산기를 만든다.
     * @param totalBytes 작업 대상 전체 바이트 수(0 나눗셈 방지를 위해 1 이상)
     * @param totalFileCount 작업 대상 파일 개수
     * @param onProgress 진행 상태를 수신하는 콜백
     * @param holdBelow100 [ByteProgressTracker]의 holdBelow100과 같다
     * @param reportProgressFloat [ByteProgressTracker]의 reportProgressFloat과 같다
     */
    internal constructor(
        totalBytes: Long,
        totalFileCount: Int,
        onProgress: (ProgressState) -> Unit,
        holdBelow100: Boolean = false,
        reportProgressFloat: Boolean = false
    ) : this(TransferTotal.Known(totalBytes), totalFileCount, onProgress, holdBelow100, reportProgressFloat)

    /** 지금까지 처리된 누적 바이트 수. */
    private var writtenBytes = 0L
    /** 마지막으로 보고한 퍼센트 — 같은 퍼센트의 중복 보고를 막는다. 초기값 -1은 "아직 보고 없음". */
    private var prevProgress = -1
    /** 현재 처리 중인 파일의 1-based 순번. */
    private var fileIndex = 0
    /** 현재 파일에서 처리한 바이트 수 — 크기를 아는 파일의 몫을 알려진 크기까지만 세는 데 쓴다. */
    private var currentFileWrittenBytes = 0L
    /** 크기를 아는 파일들에서 처리한 바이트 수(파일마다 알려진 크기까지). [TransferTotal.Partial]에서만 쓴다. */
    private var knownWrittenBytes = 0L
    /** 크기를 모르는 파일들에서 처리한 바이트 수. [TransferTotal.Partial]에서만 쓴다. */
    private var unknownWrittenBytes = 0L

    /** 다음 파일 처리를 시작한다. */
    fun startFile() {
        fileIndex++
        currentFileWrittenBytes = 0
    }

    /**
     * 바이트 복사 없이 처리 완료된 파일 수를 누적한다(동일 파티션 rename 이동 등).
     * @param count 처리 완료된 파일 수
     */
    fun skipFiles(count: Int) { fileIndex += count }

    /**
     * 처리된 바이트를 누적하고 진행률이 바뀌면 보고한다. [holdBelow100]이면 100 이상은 보고하지 않는다.
     * 전체 크기를 모르면 진행률 없이 시간 간격마다 보고한다.
     * @param bytes 이번에 처리된 바이트 수
     * @param fileName 현재 파일명
     * @param fileBytes 현재 파일의 전체 크기 — null이면 크기를 모르는 파일이며 보고에서 생략
     * @param clampTo100 true이면 진행률을 0..100으로 제한한다(항목 크기 정보가 부정확할 수 있는 아카이브용)
     */
    fun addBytes(bytes: Long, fileName: String, fileBytes: Long?, clampTo100: Boolean = false) {
        writtenBytes += bytes
        when (total) {
            is TransferTotal.Known -> reportPercent(total.bytes, fileName, fileBytes, clampTo100)
            is TransferTotal.Partial -> {
                countByKnownSize(bytes, fileBytes)
                reportWithoutPercent(total, fileName, fileBytes)
            }
        }
        currentFileWrittenBytes += bytes
    }

    /**
     * 전체 크기에 대한 퍼센트가 바뀌었으면 보고한다. [holdBelow100]이면 100 이상은 보고하지 않는다.
     * @param totalBytes 작업 대상 전체 바이트 수
     * @param fileName 현재 파일명
     * @param fileBytes 현재 파일의 전체 크기
     * @param clampTo100 true이면 진행률을 0..100으로 제한한다
     */
    private fun reportPercent(totalBytes: Long, fileName: String, fileBytes: Long?, clampTo100: Boolean) {
        val raw = (writtenBytes * 100 / totalBytes).toInt()
        val progress = if (clampTo100) raw.coerceIn(0, 100) else raw
        if (progress != prevProgress && !(holdBelow100 && progress >= 100)) {
            prevProgress = progress
            onProgress(stateOf(progress, writtenBytes.toFloat() / totalBytes, writtenBytes, totalBytes, fileName, fileBytes))
        }
    }

    /**
     * 이번 바이트를 크기를 아는 파일의 몫과 모르는 파일의 몫으로 나눠 센다. 크기를 아는 파일은 알려진 크기까지만 센다.
     * @param bytes 이번에 처리된 바이트 수
     * @param fileBytes 현재 파일의 알려진 크기. 모르면 null
     */
    private fun countByKnownSize(bytes: Long, fileBytes: Long?) {
        if (fileBytes == null) {
            unknownWrittenBytes += bytes
        } else {
            knownWrittenBytes += bytes.coerceAtMost((fileBytes - currentFileWrittenBytes).coerceAtLeast(0))
        }
    }

    /**
     * 전체 크기를 모를 때 진행률 없이 누적 바이트와 파일 순번, 크기를 아는 부분의 진행을 보고한다.
     * 같은 퍼센트라는 기준이 없으므로 [throttle]의 시간 간격만 따른다.
     * @param total 크기를 아는 부분의 합과 크기를 모르는 파일 수
     * @param fileName 현재 파일명
     * @param fileBytes 현재 파일의 전체 크기 — null이면 보고에서 생략
     */
    private fun reportWithoutPercent(total: TransferTotal.Partial, fileName: String, fileBytes: Long?) {
        if (!throttle.shouldReport(null)) return
        val knownSize = KnownSizeProgress(total.knownBytes, knownWrittenBytes, total.unknownFileCount, unknownWrittenBytes)
        onProgress(stateOf(null, null, writtenBytes, null, fileName, fileBytes).copy(knownSize = knownSize))
    }

    /**
     * [input]을 버퍼 단위로 [output]에 쓰면서 청크마다 [addBytes]로 진행률을 갱신한다.
     * @param input 읽을 스트림
     * @param output 쓸 스트림
     * @param fileName 현재 파일명
     * @param fileBytes 현재 파일의 전체 크기
     */
    fun pump(input: InputStream, output: OutputStream, fileName: String, fileBytes: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var read = input.read(buffer)
        while (read != -1) {
            output.write(buffer, 0, read)
            addBytes(read.toLong(), fileName, fileBytes)
            read = input.read(buffer)
        }
    }

    /**
     * 현재 파일의 처리 완료(서버 ACK 등)를 알린다. 마지막 파일이면 100%를 보고한다.
     * @param fileName 완료된 파일명
     * @param fileBytes 완료된 파일의 전체 크기 — null이면 보고에서 생략
     */
    fun onFileCompleted(fileName: String, fileBytes: Long?) {
        if (fileIndex == totalFileCount) {
            val completed = completedBytes()
            onProgress(stateOf(100, 1f, completed, completed, fileName, fileBytes))
        }
    }

    /** 처리할 파일이 하나도 없으면 즉시 100%를 보고한다. */
    fun reportIfEmpty() {
        if (totalFileCount == 0) {
            onProgress(ProgressState(progress = 100, progressFloat = 1f, totalBytes = 1, writtenBytes = 1, listSize = 0, currentIndex = 0))
        }
    }

    /** 작업 완료 시 100% 진행 상태를 보고한다. */
    fun reportComplete() {
        val completed = completedBytes()
        onProgress(ProgressState(
            progress = 100,
            progressFloat = if (reportProgressFloat) 1f else null,
            totalBytes = completed,
            writtenBytes = completed,
            listSize = totalFileCount,
            currentIndex = totalFileCount
        ))
    }

    /** 작업을 마쳤을 때의 전체 바이트 수. 합계를 몰랐으면 실제로 처리한 누적 바이트다. */
    private fun completedBytes(): Long = when (total) {
        is TransferTotal.Known -> total.bytes
        is TransferTotal.Partial -> writtenBytes
    }

    /** 현재 누적 상태로 ProgressState를 만든다. progressFloat은 [reportProgressFloat]일 때만 포함한다. */
    private fun stateOf(progress: Int?, progressFloat: Float?, written: Long, total: Long?, fileName: String, fileBytes: Long?) = ProgressState(
        progress = progress,
        progressFloat = if (reportProgressFloat) progressFloat else null,
        totalBytes = total,
        writtenBytes = written,
        listSize = totalFileCount,
        currentIndex = fileIndex,
        currentFileName = fileName,
        currentFileBytes = fileBytes
    )
}
