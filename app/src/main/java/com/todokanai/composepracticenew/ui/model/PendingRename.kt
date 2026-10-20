package com.todokanai.composepracticenew.ui.model

import androidx.compose.runtime.Immutable
import com.todokanai.composepracticenew.model.RenameConflict

/**
 * 같은 이름의 항목이 있어 덮어쓰기 확인을 기다리는 이름 변경.
 * @param target 이름을 바꿀 항목
 * @param newName 새 이름
 * @param conflict 기존 항목과 겹치는 방식 — [RenameConflict.FILE] 또는 [RenameConflict.FOLDER]
 */
@Immutable
data class PendingRename(
    val target: FileHolderItem,
    val newName: String,
    val conflict: RenameConflict
)
