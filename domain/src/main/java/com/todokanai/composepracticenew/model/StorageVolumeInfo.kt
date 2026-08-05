package com.todokanai.composepracticenew.model

/** 로컬 스토리지 볼륨의 원시 공간 정보를 담는 도메인 모델. */
data class StorageVolumeInfo(
    val path: String,
    val totalSpace: Long,
    val freeSpace: Long
)
