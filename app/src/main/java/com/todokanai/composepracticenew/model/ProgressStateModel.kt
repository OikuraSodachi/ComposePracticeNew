package com.todokanai.composepracticenew.model

import androidx.compose.runtime.Immutable
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.tools.independent.readableFileSize_td

/**
 * app 모듈에서 사용하는 Progress UI 상태. ProgressState를 app 레이어에서 매핑해 사용한다.
 * @param progress 진행도(0~100 퍼센트)
 * @param totalSize 전체 파일 크기(사람이 읽을 수 있는 문자열)
 * @param currentSize 작업 진행된 크기(사람이 읽을 수 있는 문자열)
 * @param listSize 전체 목록의 갯수
 * @param currentIndex 진행된 갯수
 * @param currentFileName 현재 작업중인 파일명
 * @param currentFileSize 현재 작업중인 파일의 전체 용량
 * @param actionKey 작업 식별 키
 * @param error non-null이면 해당 작업이 실패했음을 의미하며 오류 메시지를 담는다
 */
@Immutable
data class ProgressStateModel(
    val progress: Int = 0,
    val totalSize: String? = null,
    val currentSize: String? = null,
    val listSize: Int? = null,
    val currentIndex: Int? = null,
    val currentFileName: String? = null,
    val currentFileSize: String? = null,
    val actionKey: Int? = null,
    val error: String? = null
)

/** ProgressState → ProgressStateModel 변환. bytes 값을 사람이 읽을 수 있는 문자열로 변환한다. */
fun ProgressState.toModel(): ProgressStateModel = ProgressStateModel(
    progress = this.progress ?: 0,
    totalSize = this.totalBytes?.let { readableFileSize_td(it) },
    currentSize = this.writtenBytes?.let { readableFileSize_td(it) },
    listSize = this.listSize,
    currentIndex = this.currentIndex,
    currentFileName = this.currentFileName,
    currentFileSize = this.currentFileBytes?.let { readableFileSize_td(it) },
    actionKey = this.actionKey,
    error = this.error
)
