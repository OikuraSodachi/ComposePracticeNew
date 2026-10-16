package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ProgressState
import java.io.InputStream
import java.io.OutputStream

/**
 * 여러 파일에 걸친 바이트 단위 작업(복사·이동·압축·압축해제)의 누적 진행률을 계산하고, 퍼센트가 바뀔 때만 보고한다.
 * @param totalBytes 작업 대상 전체 바이트 수(0 나눗셈 방지를 위해 1 이상)
 * @param totalFileCount 작업 대상 파일 개수
 * @param onProgress 진행 상태를 수신하는 콜백
 */
class ByteProgressTracker internal constructor(
    private val totalBytes: Long,
    private val totalFileCount: Int,
    private val onProgress: (ProgressState) -> Unit
) {
    private var writtenBytes = 0L
    private var prevProgress = -1
    private var fileIndex = 0

    /** 다음 파일 처리를 시작한다. */
    fun startFile() { fileIndex++ }

    /**
     * 바이트 복사 없이 처리 완료된 파일 수를 누적한다(동일 파티션 rename 이동 등).
     * @param count 처리 완료된 파일 수
     */
    fun skipFiles(count: Int) { fileIndex += count }

    /**
     * 처리된 바이트를 누적하고 진행률이 바뀌면 보고한다.
     * @param bytes 이번에 처리된 바이트 수
     * @param fileName 현재 파일명
     * @param fileBytes 현재 파일의 전체 크기 — null이면 보고에서 생략
     * @param clampTo100 true이면 진행률을 0..100으로 제한한다(항목 크기 정보가 부정확할 수 있는 아카이브용)
     */
    fun addBytes(bytes: Long, fileName: String, fileBytes: Long?, clampTo100: Boolean = false) {
        writtenBytes += bytes
        val raw = (writtenBytes * 100 / totalBytes).toInt()
        val progress = if (clampTo100) raw.coerceIn(0, 100) else raw
        if (progress != prevProgress) {
            prevProgress = progress
            onProgress(ProgressState(
                progress = progress,
                totalBytes = totalBytes,
                writtenBytes = writtenBytes,
                listSize = totalFileCount,
                currentIndex = fileIndex,
                currentFileName = fileName,
                currentFileBytes = fileBytes
            ))
        }
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

    /** 작업 완료 시 100% 진행 상태를 보고한다. */
    fun reportComplete() {
        onProgress(ProgressState(
            progress = 100,
            totalBytes = totalBytes,
            writtenBytes = totalBytes,
            listSize = totalFileCount,
            currentIndex = totalFileCount
        ))
    }
}
