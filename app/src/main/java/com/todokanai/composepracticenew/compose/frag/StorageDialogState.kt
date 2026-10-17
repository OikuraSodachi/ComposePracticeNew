package com.todokanai.composepracticenew.compose.frag

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.todokanai.composepracticenew.ui.model.RemoteStorageItem

/**
 * StorageFrag의 원격 스토리지 추가·편집·삭제 다이얼로그 open/close 상태를 보유한다.
 *
 * @param deletingId 삭제 확인 대기 중인 항목의 id. 구성 변경 후 복원할 때 사용
 */
class StorageDialogState(deletingId: Long? = null) {
    var showAddDialog by mutableStateOf(false); private set
    var editingItem   by mutableStateOf<RemoteStorageItem?>(null); private set
    var deletingId    by mutableStateOf(deletingId); private set

    fun openAdd()    { showAddDialog = true }
    fun dismissAdd() { showAddDialog = false }

    /** 편집할 항목을 설정하고 편집 다이얼로그를 표시한다. */
    fun openEdit(item: RemoteStorageItem) { editingItem = item }
    fun dismissEdit() { editingItem = null }

    /** 삭제할 항목을 설정하고 삭제 확인 다이얼로그를 표시한다. */
    fun openDelete(item: RemoteStorageItem) { deletingId = item.id }
    fun dismissDelete() { deletingId = null }

    companion object {
        /** 삭제 확인 대기 상태(deletingId)만 저장한다 — 구성 변경 시 확인 다이얼로그를 유지하기 위함. */
        val Saver: Saver<StorageDialogState, Long> = Saver(
            save = { it.deletingId },
            restore = { StorageDialogState(deletingId = it) }
        )
    }
}

/**
 * StorageDialogState를 컴포지션에 기억한다. 구성 변경 시 삭제 확인 대기 상태를 복원한다.
 */
@Composable
fun rememberStorageDialogState() = rememberSaveable(saver = StorageDialogState.Saver) { StorageDialogState() }
