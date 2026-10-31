package com.todokanai.composepracticenew.data.progress

import com.todokanai.composepracticenew.model.KnownSizeProgress

/**
 * [ByteProgressTracker]가 처리한 바이트를 진행 값으로 바꾸는 계산 방식. 작업 전체의 크기([TransferTotal])에 따라 구현이 갈리며,
 * 파일 순번·100% 보고·보고 형식은 트래커가 맡는다.
 */
internal interface ProgressCounting {

    /** 다음 파일 처리를 시작한다. */
    fun onFileStarted()

    /**
     * 처리된 바이트를 센다. 누적 바이트는 트래커가 한 곳에서 세어 넘긴다 — 계산 방식마다 따로 세지 않도록.
     * @param bytes 이번에 처리된 바이트 수
     * @param writtenBytes 이번 바이트까지 더한 작업 전체의 누적 바이트 수
     * @param fileBytes 현재 파일의 알려진 크기. 모르면 null
     * @return 보고할 진행 값. 보고할 때가 아니면 null
     */
    fun onBytes(bytes: Long, writtenBytes: Long, fileBytes: Long?): ProgressReading?

    /**
     * 현재 파일의 처리 완료(서버 ACK 등)를 센다.
     * @param fileBytes 완료된 파일의 알려진 크기. 모르면 null
     * @return 완료로 진행이 바뀌어 바로 보고할 진행 값. 없으면 null
     */
    fun onFileCompleted(fileBytes: Long?): ProgressReading?

    /**
     * 작업을 마쳤을 때의 전체 바이트 수를 구한다.
     * @param writtenBytes 작업 전체의 누적 바이트 수
     * @return 작업 전체의 바이트 수
     */
    fun completedBytes(writtenBytes: Long): Long
}

/**
 * 진행 보고 한 번에 담을 계산 결과.
 * @param progress 진행도(0~100 퍼센트). 모르면 null
 * @param progressFloat 진행도(0.0~1.0). 모르면 null
 * @param totalBytes 작업 전체의 바이트 수. 모르면 null
 * @param knownSize 크기를 모르는 파일이 섞였을 때 크기를 아는 부분의 진행. 그 밖에는 null
 */
internal data class ProgressReading(
    val progress: Int?,
    val progressFloat: Float?,
    val totalBytes: Long?,
    val knownSize: KnownSizeProgress? = null
)
