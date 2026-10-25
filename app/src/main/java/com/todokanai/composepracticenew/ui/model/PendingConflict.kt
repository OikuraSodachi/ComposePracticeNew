package com.todokanai.composepracticenew.ui.model

import androidx.compose.runtime.Immutable

/**
 * 사용자의 덮어쓰기/건너뛰기 선택을 기다리는 파일 충돌 상태.
 * @param targets 작업 대상 전체 항목
 * @param conflicts 목적지에 같은 이름이 이미 존재하는 항목
 */
@Immutable
data class PendingConflict(
    val targets: List<FileHolderItem>,
    val conflicts: List<FileHolderItem>
)

/** 파일 충돌 다이얼로그에서 사용자가 고른 처리 방법. */
enum class ConflictResolution {
    /** 충돌 항목을 포함해 모두 실행한다. */
    OVERWRITE,
    /** 충돌 항목을 제외하고 실행한다. */
    SKIP,
    /** 실행하지 않고 닫는다. */
    CANCEL
}
