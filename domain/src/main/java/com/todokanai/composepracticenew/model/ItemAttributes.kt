package com.todokanai.composepracticenew.model

/**
 * 저장소에서 지금 읽은 항목 하나의 속성.
 * @param isDirectory 디렉터리 여부
 * @param lastModifiedMillis 마지막 수정 시각(epoch millis)
 */
data class ItemAttributes(
    val isDirectory: Boolean,
    val lastModifiedMillis: Long
)
