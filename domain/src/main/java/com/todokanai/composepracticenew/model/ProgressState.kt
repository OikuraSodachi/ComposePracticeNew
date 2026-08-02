package com.todokanai.composepracticenew.model

/** 파일 작업의 Progress 상태 관찰을 위한 class **/
data class ProgressState(
    /** 진행도(percent) **/
    val progress: Int? = null,
    /** 진행도(Float) **/
    val progressFloat: Float? = null,
    /** 전체 파일 크기 **/
    val totalSize: String? = null,
    /** 작업 진행된 크기 **/
    val currentSize: String? = null,
    /** 전체 목록의 갯수 **/
    val listSize: Int? = null,
    /** 진행된 갯수 **/
    val currentIndex: Int? = null,
    /** 그 외의 정보? **/
    val actionKey: Int? = null,
    /** non-null이면 해당 작업이 실패했음을 의미하며 오류 메시지를 담는다. **/
    val error: String? = null
)
