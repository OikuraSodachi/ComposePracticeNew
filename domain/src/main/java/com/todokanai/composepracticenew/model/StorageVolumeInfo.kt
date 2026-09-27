package com.todokanai.composepracticenew.model

/**
 * 로컬 스토리지 볼륨의 원시 공간 정보를 담는 도메인 모델.
 * @param path 스토리지 절대 경로
 * @param totalSpace 전체 용량(bytes)
 * @param freeSpace 사용 가능한 용량(bytes)
 */
data class StorageVolumeInfo(
    val path: String,
    val totalSpace: Long,
    val freeSpace: Long
)
