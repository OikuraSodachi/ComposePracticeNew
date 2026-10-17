package com.todokanai.composepracticenew.compose.frag

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.todokanai.composepracticenew.ui.model.RemoteStorageItem

/** StorageFrag의 원격 스토리지 추가·편집·삭제 다이얼로그 open/close 상태를 보유한다. */
class StorageDialogState {
    var showAddDialog by mutableStateOf(false); private set
    var editingItem   by mutableStateOf<RemoteStorageItem?>(null); private set
    var deletingItem  by mutableStateOf<RemoteStorageItem?>(null); private set

    fun openAdd()    { showAddDialog = true }
    fun dismissAdd() { showAddDialog = false }

    /** 편집할 항목을 설정하고 편집 다이얼로그를 표시한다. */
    fun openEdit(item: RemoteStorageItem) { editingItem = item }
    fun dismissEdit() { editingItem = null }

    /** 삭제할 항목을 설정하고 삭제 확인 다이얼로그를 표시한다. */
    fun openDelete(item: RemoteStorageItem) { deletingItem = item }
    fun dismissDelete() { deletingItem = null }
}

/**
 * StorageDialogState를 컴포지션에 기억한다.
 */
@Composable
// StorageDialogState 인스턴스를 컴포지션 생존 동안 1회만 생성 — 키 없음(입력과 무관하게 동일 인스턴스 유지)
fun rememberStorageDialogState() = remember { StorageDialogState() }
