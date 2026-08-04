package com.todokanai.composepracticenew.model

/** 스토리지 목록 UI 표시용 모델. */
data class StorageHolderItem(
    val absolutePath: String,
    val used: String,
    val total: String,
    val progress: Float
)
