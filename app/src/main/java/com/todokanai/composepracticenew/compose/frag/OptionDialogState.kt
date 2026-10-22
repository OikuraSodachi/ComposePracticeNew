package com.todokanai.composepracticenew.compose.frag

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * OptionFrag의 새 폴더·정렬 다이얼로그 열림 상태를 보유한다. 구성 변경 후에도 유지되도록 [Saver]를 제공한다.
 * @param newFolderDialog 새 폴더 다이얼로그 초기 열림 여부
 * @param sortDialog 정렬 다이얼로그 초기 열림 여부
 */
class OptionDialogState(newFolderDialog: Boolean = false, sortDialog: Boolean = false) {
    /** 새 폴더 다이얼로그 열림 여부. */
    var newFolderDialog by mutableStateOf(newFolderDialog); private set
    /** 정렬 다이얼로그 열림 여부. */
    var sortDialog by mutableStateOf(sortDialog); private set

    fun openNewFolder()    { newFolderDialog = true }
    fun dismissNewFolder() { newFolderDialog = false }

    fun openSort()    { sortDialog = true }
    fun dismissSort() { sortDialog = false }

    companion object {
        /** 두 열림 여부를 Bundle에 저장·복원하는 Saver. */
        val Saver: Saver<OptionDialogState, *> = listSaver(
            save = { listOf(it.newFolderDialog, it.sortDialog) },
            restore = { OptionDialogState(newFolderDialog = it[0], sortDialog = it[1]) }
        )
    }
}

/** OptionDialogState를 컴포지션에 기억하고 구성 변경 시 복원한다. */
@Composable
// rememberSaveable + Saver — 키 없음: 컴포지션 동안 같은 인스턴스를 유지하고, 회전 등 구성 변경 후에도 열린 다이얼로그를 다시 연다
fun rememberOptionDialogState(): OptionDialogState = rememberSaveable(saver = OptionDialogState.Saver) { OptionDialogState() }
