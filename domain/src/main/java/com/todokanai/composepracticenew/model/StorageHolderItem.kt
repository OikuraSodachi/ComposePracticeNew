package com.todokanai.composepracticenew.model

import java.io.File

/** 스토리지 목록 UI 표시용 모델. */
data class StorageHolderItem(
    val storage: File,
    val absolutePath: String,
    val used: String,
    val total: String,
    val progress: Float
)
