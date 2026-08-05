package com.todokanai.composepracticenew.model

/** 파일 목록 UI 표시용 모델. */
data class FileHolderItem(
    val path: String,
    val isDirectory: Boolean,
    val name: String,
    val size: String,
    val lastModified: String,
    val sizeBytes: Long = 0L
)
