package com.todokanai.composepracticenew.compose.frag

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.todokanai.composepracticenew.ui.model.RemoteStorageItem

/**
 * StorageFrag의 원격 스토리지 추가·편집·삭제 다이얼로그 open/close 상태를 보유한다.
 *
 * 편집·삭제 대상은 항목 자체가 아닌 id로 보관하고, 표시 시점에 목록에서 다시 찾는다(구성 변경 후 복원을 위해).
 *
 * @param showAddDialog 추가 다이얼로그 표시 여부. 구성 변경 후 복원할 때 사용
 * @param editingId 편집 중인 항목의 id. 구성 변경 후 복원할 때 사용
 * @param deletingId 삭제 확인 대기 중인 항목의 id. 구성 변경 후 복원할 때 사용
 */
class StorageDialogState(
    showAddDialog: Boolean = false,
    editingId: Long? = null,
    deletingId: Long? = null
) {
    var showAddDialog by mutableStateOf(showAddDialog); private set
    var editingId     by mutableStateOf(editingId); private set
    var deletingId    by mutableStateOf(deletingId); private set

    fun openAdd()    { showAddDialog = true }
    fun dismissAdd() { showAddDialog = false }

    /** 편집할 항목을 설정하고 편집 다이얼로그를 표시한다. */
    fun openEdit(item: RemoteStorageItem) { editingId = item.id }
    fun dismissEdit() { editingId = null }

    /** 삭제할 항목을 설정하고 삭제 확인 다이얼로그를 표시한다. */
    fun openDelete(item: RemoteStorageItem) { deletingId = item.id }
    fun dismissDelete() { deletingId = null }

    companion object {
        /** 세 다이얼로그의 표시 상태를 저장한다 — 구성 변경 시 열려 있던 다이얼로그를 유지하기 위함. */
        val Saver: Saver<StorageDialogState, Any> = listSaver(
            save = { listOf(it.showAddDialog, it.editingId, it.deletingId) },
            restore = {
                StorageDialogState(
                    showAddDialog = it[0] as Boolean,
                    editingId = it[1] as Long?,
                    deletingId = it[2] as Long?
                )
            }
        )
    }
}

/**
 * StorageDialogState를 컴포지션에 기억한다. 구성 변경 시 열려 있던 다이얼로그 상태를 복원한다.
 */
@Composable
fun rememberStorageDialogState() = rememberSaveable(saver = StorageDialogState.Saver) { StorageDialogState() }
