package com.todokanai.composepracticenew.data.progress

import com.todokanai.composepracticenew.model.ProgressState
import java.io.InputStream
import java.io.OutputStream

/**
 * 바이트 단위 파일 작업(로컬 복사·이동·압축·압축해제, FTP 파일·디렉터리 전송)의 누적 진행을 보고한다. 단일 파일도 파일 하나짜리 작업으로 보고,
 * 모든 작업이 생성 옵션 없이 같은 규칙을 따른다. 트래커는 누적 바이트·파일 순번·100% 보고를 맡고, 바이트를 진행 값으로 바꾸는 계산은 [total]에 맞는 [ProgressCounting]이 맡는다 —
 * 전체 크기를 알면 퍼센트가 바뀌었거나 [throttle]의 시간 간격이 지났을 때([PercentCounting]), 모르면 퍼센트를 지어내지 않고 크기를 아는 부분의 진행을 시간 간격마다([KnownSizeCounting]) 보고한다.
 * 100%는 끝났다는 뜻이므로 마지막 파일의 처리 완료([onFileCompleted], 서버 ACK 등)나 작업 완료([reportComplete])에서만 보고한다.
 * @param total 작업 전체의 크기
 * @param totalFileCount 작업 대상 파일 개수
 * @param onProgress 진행 상태를 수신하는 콜백
 * @param throttle 보고 간격을 정하는 ProgressThrottle
 */
class ByteProgressTracker internal constructor(
    total: TransferTotal,
    private val totalFileCount: Int,
    private val onProgress: (ProgressState) -> Unit,
    throttle: ProgressThrottle = ProgressThrottle()
) {
    /**
     * 모든 파일의 크기를 아는 작업의 계산기를 만든다.
     * @param totalBytes 작업 대상 전체 바이트 수(0 이상)
     * @param totalFileCount 작업 대상 파일 개수
     * @param onProgress 진행 상태를 수신하는 콜백
     */
    internal constructor(
        totalBytes: Long,
        totalFileCount: Int,
        onProgress: (ProgressState) -> Unit
    ) : this(TransferTotal.Known(totalBytes), totalFileCount, onProgress)

    /** 작업 전체의 크기에 맞는 계산 방식. */
    private val counting: ProgressCounting = when (total) {
        is TransferTotal.Known -> PercentCounting(total.bytes, throttle)
        is TransferTotal.Partial -> KnownSizeCounting(total, throttle)
    }
    /** 지금까지 처리된 누적 바이트 수. */
    private var writtenBytes = 0L
    /** 현재 처리 중인 파일의 1-based 순번. */
    private var fileIndex = 0

    /** 다음 파일 처리를 시작한다. */
    fun startFile() {
        fileIndex++
        counting.onFileStarted()
    }

    /**
     * 바이트 복사 없이 처리 완료된 파일 수를 누적한다(동일 파티션 rename 이동 등).
     * @param count 처리 완료된 파일 수
     */
    fun skipFiles(count: Int) { fileIndex += count }

    /**
     * 처리된 바이트를 누적하고, 계산 방식이 보고할 때라고 하면 보고한다.
     * @param bytes 이번에 처리된 바이트 수
     * @param fileName 현재 파일명
     * @param fileBytes 현재 파일의 전체 크기 — null이면 크기를 모르는 파일이며 보고에서 생략
     */
    fun addBytes(bytes: Long, fileName: String, fileBytes: Long?) {
        writtenBytes += bytes
        counting.onBytes(bytes, writtenBytes, fileBytes)?.let { onProgress(stateOf(it, fileName, fileBytes)) }
    }

    /**
     * [input]을 버퍼 단위로 [output]에 쓰면서 청크마다 [addBytes]로 진행률을 갱신한다.
     * @param input 읽을 스트림
     * @param output 쓸 스트림
     * @param fileName 현재 파일명
     * @param fileBytes 현재 파일의 전체 크기 — null이면 크기를 모르는 파일
     */
    fun pump(input: InputStream, output: OutputStream, fileName: String, fileBytes: Long?) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var read = input.read(buffer)
        while (read != -1) {
            output.write(buffer, 0, read)
            addBytes(read.toLong(), fileName, fileBytes)
            read = input.read(buffer)
        }
    }

    /**
     * 현재 파일의 처리 완료(서버 ACK 등)를 알린다. 마지막 파일이면 100%를, 아니면 완료로 바뀐 진행이 있을 때 그 진행을 보고한다.
     * @param fileName 완료된 파일명
     * @param fileBytes 완료된 파일의 전체 크기 — null이면 보고에서 생략
     */
    fun onFileCompleted(fileName: String, fileBytes: Long?) {
        val reading = counting.onFileCompleted(fileBytes)
        if (fileIndex == totalFileCount) {
            val completed = counting.completedBytes(writtenBytes)
            onProgress(stateOf(ProgressReading(100, completed), fileName, fileBytes, written = completed))
        } else {
            reading?.let { onProgress(stateOf(it, fileName, fileBytes)) }
        }
    }

    /** 처리할 파일이 하나도 없으면 즉시 100%를 보고한다. */
    fun reportIfEmpty() {
        if (totalFileCount == 0) {
            onProgress(ProgressState(progress = 100, totalBytes = 0, writtenBytes = 0, listSize = 0, currentIndex = 0))
        }
    }

    /** 작업 완료 시 100% 진행 상태를 보고한다. */
    fun reportComplete() {
        val completed = counting.completedBytes(writtenBytes)
        onProgress(ProgressState(
            progress = 100,
            totalBytes = completed,
            writtenBytes = completed,
            listSize = totalFileCount,
            currentIndex = totalFileCount
        ))
    }

    /**
     * 계산 결과와 현재 파일 순번으로 ProgressState를 만든다.
     * @param reading 계산 방식이 낸 진행 값
     * @param fileName 현재 파일명
     * @param fileBytes 현재 파일의 전체 크기
     * @param written 보고할 누적 바이트 수
     */
    private fun stateOf(reading: ProgressReading, fileName: String, fileBytes: Long?, written: Long = writtenBytes) = ProgressState(
        progress = reading.progress,
        totalBytes = reading.totalBytes,
        writtenBytes = written,
        listSize = totalFileCount,
        currentIndex = fileIndex,
        currentFileName = fileName,
        currentFileBytes = fileBytes,
        knownSize = reading.knownSize
    )
}
