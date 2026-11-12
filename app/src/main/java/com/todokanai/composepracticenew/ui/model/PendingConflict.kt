package com.todokanai.composepracticenew.ui.model

import androidx.compose.runtime.Immutable
import com.todokanai.composepracticenew.model.NameCollision

/**
 * 사용자의 덮어쓰기/건너뛰기 선택을 기다리는 파일 충돌 상태.
 * @param targets 작업 대상 전체 항목
 * @param conflicts 목적지에 같은 이름이 이미 존재하는 항목
 * @param collisions 함께 보내는 항목끼리 목적지에서 같은 항목이 될 수 있는 이름 묶음. 건너뛰기로 뺄 수 없으며 알리기만 한다
 */
@Immutable
data class PendingConflict(
    val targets: List<FileHolderItem>,
    val conflicts: List<FileHolderItem>,
    val collisions: List<NameCollisionGroup> = emptyList(),
)

/**
 * 같은 폴더에 함께 보내는 이름 중 목적지에서 같은 항목이 될 수 있는 이름 묶음(표시용).
 * @param folderLabel 이름들이 놓일 폴더의 표시용 경로. 처음 선택한 항목끼리면 null
 * @param names 같은 항목이 될 수 있는 이름
 */
data class NameCollisionGroup(val folderLabel: String?, val names: List<String>) {
    companion object {
        /** domain [NameCollision]을 표시용 묶음으로 바꾼다. */
        fun from(collision: NameCollision) = NameCollisionGroup(collision.folderLabel, collision.names)
    }
}

/** 파일 충돌 다이얼로그에서 사용자가 고른 처리 방법. */
enum class ConflictResolution {
    /** 충돌 항목을 포함해 모두 실행한다. */
    OVERWRITE,
    /** 충돌 항목을 제외하고 실행한다. */
    SKIP,
    /** 실행하지 않고 닫는다. */
    CANCEL
}
