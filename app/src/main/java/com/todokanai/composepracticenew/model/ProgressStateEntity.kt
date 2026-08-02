package com.todokanai.composepracticenew.model

import com.todokanai.composepracticenew.model.ProgressState

/** app 모듈에서 사용하는 Progress UI 상태. ProgressState를 app 레이어에서 매핑해 사용한다. */
data class ProgressStateEntity(
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

/** ProgressState → ProgressStateEntity 변환. */
fun ProgressState.toEntity(): ProgressStateEntity = ProgressStateEntity(
    progress = this.progress ?: 0,
    totalSize = this.totalSize,
    currentSize = this.currentSize,
    listSize = this.listSize,
    currentIndex = this.currentIndex,
    currentFileName = this.currentFileName,
    currentFileSize = this.currentFileSize,
    actionKey = this.actionKey,
    error = this.error
)
