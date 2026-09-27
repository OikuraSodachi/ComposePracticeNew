package com.todokanai.composepracticenew.model

/**
 * 파일 목록 UI 표시용 모델.
 * @param path 파일 절대 경로
 * @param isDirectory 디렉터리 여부
 * @param name 파일명
 * @param size 파일 크기(사람이 읽을 수 있는 문자열)
 * @param lastModified 마지막 수정 시각 문자열
 * @param sizeBytes 파일 크기(bytes)
 */
data class FileHolderItem(
    val path: String,
    val isDirectory: Boolean,
    val name: String,
    val size: String,
    val lastModified: String,
    val sizeBytes: Long = 0L
)
