package com.todokanai.composepracticenew.ui.model

import androidx.compose.runtime.Immutable
import com.todokanai.composepracticenew.model.RenameOverwrite

/**
 * 같은 이름의 항목이 있어 덮어쓰기 확인을 기다리는 이름 변경.
 * @param target 이름을 바꿀 항목
 * @param newName 새 이름
 * @param overwrite 확인하면 저장소에서 일어나는 일 — 이름 변경을 실행하는 UseCase가 정한다(#447)
 */
@Immutable
data class PendingRename(
    val target: FileHolderItem,
    val newName: String,
    val overwrite: RenameOverwrite
)
