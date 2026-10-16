package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.ProgressState

/**
 * 디렉터리 단위 FTP 전송(다운로드·업로드)의 누적 진행률을 계산·보고한다.
 * 서버 ACK 전 100% 조기 보고를 막기 위해 바이트 기반 보고는 100 미만까지만 하고,
 * 100%는 마지막 파일의 전송 완료 확인 시점에만 보고한다.
 * @param totalBytes 전송 대상 전체 바이트 수(0 나눗셈 방지를 위해 1 이상)
 * @param totalCount 전송 대상 파일 개수
 * @param onProgress 진행 상태를 수신하는 콜백
 */
internal class DirectoryTransferProgress(
    private val totalBytes: Long,
    private val totalCount: Int,
    private val onProgress: (ProgressState) -> Unit
) {
    private var writtenBytes = 0L
    private var prevProgress = -1
    private var fileIndex = 0

    /** 다음 파일 전송을 시작한다. */
    fun startFile() { fileIndex++ }

    /**
     * 전송된 바이트를 누적하고, 진행률이 바뀌었고 100 미만이면 보고한다.
     * @param bytes 이번에 전송된 바이트 수
     * @param fileName 현재 파일명
     * @param fileBytes 현재 파일의 전체 크기
     */
    fun onBytes(bytes: Int, fileName: String, fileBytes: Long) {
        writtenBytes += bytes
        val progress = (writtenBytes * 100 / totalBytes).toInt()
        if (progress != prevProgress && progress < 100) {
            onProgress(stateOf(progress, writtenBytes.toFloat() / totalBytes, writtenBytes, fileName, fileBytes))
            prevProgress = progress
        }
    }

    /**
     * 현재 파일의 전송 완료(서버 ACK)를 알린다. 마지막 파일이면 100%를 보고한다.
     * @param fileName 완료된 파일명
     * @param fileBytes 완료된 파일의 전체 크기
     */
    fun onFileCompleted(fileName: String, fileBytes: Long) {
        if (fileIndex == totalCount) onProgress(stateOf(100, 1f, totalBytes, fileName, fileBytes))
    }

    /** 전송할 파일이 하나도 없으면 즉시 100%를 보고한다. */
    fun reportIfEmpty() {
        if (totalCount == 0) {
            onProgress(ProgressState(progress = 100, progressFloat = 1f, totalBytes = 1, writtenBytes = 1, listSize = 0, currentIndex = 0))
        }
    }

    private fun stateOf(progress: Int, progressFloat: Float, written: Long, fileName: String, fileBytes: Long) = ProgressState(
        progress = progress,
        progressFloat = progressFloat,
        totalBytes = totalBytes,
        writtenBytes = written,
        listSize = totalCount,
        currentIndex = fileIndex,
        currentFileName = fileName,
        currentFileBytes = fileBytes
    )
}
