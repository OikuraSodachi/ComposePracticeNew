package com.todokanai.composepracticenew.model

import androidx.compose.runtime.Immutable
import com.todokanai.composepracticenew.tools.independent.readableFileSize_td

/**
 * app 모듈에서 사용하는 Progress UI 상태. ProgressState를 app 레이어에서 매핑해 사용한다.
 * @param progress 진행도(0~100 퍼센트). 보고 전이거나 전체 크기를 몰라 알 수 없으면 null — 0%로 바꿔 멈춘 것처럼 보이지 않게 한다
 * @param totalSize 전체 파일 크기(사람이 읽을 수 있는 문자열)
 * @param currentSize 작업 진행된 크기(사람이 읽을 수 있는 문자열)
 * @param listSize 전체 목록의 갯수
 * @param currentIndex 진행된 갯수
 * @param currentFileName 현재 작업중인 파일명
 * @param currentFileSize 현재 작업중인 파일의 전체 용량
 * @param operationType 작업 종류
 * @param knownSize 크기를 모르는 파일이 섞여 [progress]가 null일 때 크기를 아는 부분의 진행. 그 밖에는 null
 */
@Immutable
data class ProgressStateModel(
    val progress: Int? = null,
    val totalSize: String? = null,
    val currentSize: String? = null,
    val listSize: Int? = null,
    val currentIndex: Int? = null,
    val currentFileName: String? = null,
    val currentFileSize: String? = null,
    val operationType: FileOperationType? = null,
    val knownSize: KnownSizeProgressModel? = null
)

/**
 * 크기를 아는 파일과 모르는 파일을 나눈 진행의 UI 상태.
 * @param progress 크기를 아는 파일들에 대한 진행도(0~100 퍼센트). 크기를 아는 부분의 합이 0이면(그런 파일이 없거나 모두 빈 파일) null
 * @param writtenSize 크기를 아는 파일들에서 처리한 크기(사람이 읽을 수 있는 문자열)
 * @param totalSize 크기를 아는 파일들의 크기 합(사람이 읽을 수 있는 문자열)
 * @param unknownFileCount 크기를 모르는 파일 수
 * @param unknownWrittenSize 크기를 모르는 파일들에서 처리한 크기(사람이 읽을 수 있는 문자열)
 */
@Immutable
data class KnownSizeProgressModel(
    val progress: Int?,
    val writtenSize: String,
    val totalSize: String,
    val unknownFileCount: Int,
    val unknownWrittenSize: String
)

/** ProgressState → ProgressStateModel 변환. bytes 값을 사람이 읽을 수 있는 문자열로 변환한다. */
fun ProgressState.toModel(): ProgressStateModel = ProgressStateModel(
    progress = this.progress,
    totalSize = this.totalBytes?.let { readableFileSize_td(it) },
    currentSize = this.writtenBytes?.let { readableFileSize_td(it) },
    listSize = this.listSize,
    currentIndex = this.currentIndex,
    currentFileName = this.currentFileName,
    currentFileSize = this.currentFileBytes?.let { readableFileSize_td(it) },
    operationType = this.operationType,
    knownSize = this.knownSize?.toModel()
)

/** KnownSizeProgress → KnownSizeProgressModel 변환. */
private fun KnownSizeProgress.toModel(): KnownSizeProgressModel = KnownSizeProgressModel(
    progress = knownPercentOf(knownWrittenBytes, knownTotalBytes),
    writtenSize = readableFileSize_td(knownWrittenBytes),
    totalSize = readableFileSize_td(knownTotalBytes),
    unknownFileCount = unknownFileCount,
    unknownWrittenSize = readableFileSize_td(unknownWrittenBytes)
)

/**
 * 크기를 아는 부분의 퍼센트를 구한다.
 * @param written 처리한 바이트
 * @param total 크기 합
 * @return 0~100 퍼센트. [total]이 0이면 나눌 기준이 없어 null
 */
private fun knownPercentOf(written: Long, total: Long): Int? =
    if (total > 0) (written * 100 / total).toInt().coerceIn(0, 100) else null
