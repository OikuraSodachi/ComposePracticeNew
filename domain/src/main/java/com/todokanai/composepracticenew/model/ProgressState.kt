package com.todokanai.composepracticenew.model

/**
 * 파일 작업의 Progress 상태 관찰을 위한 class
 * @param progress 진행도(percent). 보고 전이거나 전체 크기를 몰라 계산할 수 없으면 null
 * @param progressFloat 진행도(Float)
 * @param totalBytes 전체 파일 크기(bytes). 모르면 null
 * @param writtenBytes 작업 진행된 크기(bytes)
 * @param listSize 전체 목록의 갯수
 * @param currentIndex 진행된 갯수
 * @param currentFileName 현재 작업중인 파일명
 * @param currentFileBytes 현재 작업중인 파일의 전체 용량(bytes)
 * @param operationType 진행 중인 작업 종류
 */
data class ProgressState(
    val progress: Int? = null,
    val progressFloat: Float? = null,
    val totalBytes: Long? = null,
    val writtenBytes: Long? = null,
    val listSize: Int? = null,
    val currentIndex: Int? = null,
    val currentFileName: String? = null,
    val currentFileBytes: Long? = null,
    val operationType: FileOperationType? = null
)
