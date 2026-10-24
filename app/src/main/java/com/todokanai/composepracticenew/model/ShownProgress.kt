package com.todokanai.composepracticenew.model

import androidx.compose.runtime.Immutable

/**
 * 진행률 다이얼로그에 띄울 작업 하나의 UI 상태.
 * @param instanceId 작업 인스턴스 ID. 다이얼로그를 닫거나 취소할 때 대상 작업을 가리킨다
 * @param progress 그 작업의 진행 상태
 */
@Immutable
data class ShownProgress(
    val instanceId: Int,
    val progress: ProgressStateModel
)
