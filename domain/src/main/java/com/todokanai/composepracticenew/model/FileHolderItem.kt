package com.todokanai.composepracticenew.model

/**
 * 파일 목록 항목의 도메인 모델. 표시용 문자열 포맷은 app 레이어가 담당한다.
 * @param path 파일 절대 경로
 * @param isDirectory 디렉터리 여부
 * @param name 파일명
 * @param sizeBytes 파일 크기(bytes). 디렉터리(자기 크기가 없음)와 저장소가 크기를 알려주지 않은 파일은 null
 * @param lastModifiedMillis 마지막 수정 시각(epoch millis)
 */
data class FileHolderItem(
    val path: String,
    val isDirectory: Boolean,
    val name: String,
    val sizeBytes: Long?,
    val lastModifiedMillis: Long
)
