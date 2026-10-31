package com.todokanai.composepracticenew.model

/**
 * 크기를 모르는 파일이 섞여 전체 진행률을 알 수 없는 작업에서, 크기를 아는 파일과 모르는 파일을 나눈 진행.
 * @param knownTotalBytes 크기를 아는 파일들의 크기 합
 * @param knownWrittenBytes 크기를 아는 파일들에서 처리한 바이트. 파일마다 알려진 크기까지만 센다 — 목록 조회 뒤 커진 파일이 이 진행을 100% 넘게 만들지 않도록
 * @param unknownFileCount 크기를 모르는 파일 수(1 이상)
 * @param unknownWrittenBytes 크기를 모르는 파일들에서 처리한 바이트
 */
data class KnownSizeProgress(
    val knownTotalBytes: Long,
    val knownWrittenBytes: Long,
    val unknownFileCount: Int,
    val unknownWrittenBytes: Long
)
