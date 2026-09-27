package com.todokanai.composepracticenew.model

/**
 * 스토리지 목록 UI 표시용 모델.
 * @param absolutePath 스토리지 절대 경로
 * @param used 사용 중인 용량(사람이 읽을 수 있는 문자열)
 * @param total 전체 용량(사람이 읽을 수 있는 문자열)
 * @param progress 사용률(0.0~1.0)
 */
data class StorageHolderItem(
    val absolutePath: String,
    val used: String,
    val total: String,
    val progress: Float
)
