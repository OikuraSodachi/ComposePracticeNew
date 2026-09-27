package com.todokanai.composepracticenew.model

/**
 * 파일 작업의 Progress 상태 관찰을 위한 class
 * @param progress 진행도(percent)
 * @param progressFloat 진행도(Float)
 * @param totalBytes 전체 파일 크기(bytes)
 * @param writtenBytes 작업 진행된 크기(bytes)
 * @param listSize 전체 목록의 갯수
 * @param currentIndex 진행된 갯수
 * @param currentFileName 현재 작업중인 파일명
 * @param currentFileBytes 현재 작업중인 파일의 전체 용량(bytes)
 * @param actionKey 그 외의 정보?
 * @param error non-null이면 해당 작업이 실패했음을 의미하며 오류 메시지를 담는다.
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
    val actionKey: Int? = null,
    val error: String? = null
)
