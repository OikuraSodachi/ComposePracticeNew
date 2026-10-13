package com.todokanai.composepracticenew.compose.listview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.todokanai.composepracticenew.myobjects.AppConstants
import com.todokanai.composepracticenew.ui.model.FileHolderItem

/** BottomButtonListView의 다이얼로그 open/close 및 충돌 파일 상태를 보유한다. */
class BottomButtonDialogState {
    var zipDialog          by mutableStateOf(false); private set
    var renameDialog       by mutableStateOf(false); private set
    var infoDialog         by mutableStateOf(false); private set
    var deleteDialog       by mutableStateOf(false); private set
    var showConflictDialog by mutableStateOf(false); private set
    var conflictFiles      by mutableStateOf<List<FileHolderItem>>(emptyList()); private set
    var pendingSelectMode  by mutableStateOf(AppConstants.DEFAULT_MODE); private set

    fun openZip()    { zipDialog = true }
    fun dismissZip() { zipDialog = false }

    fun openRename()    { renameDialog = true }
    fun dismissRename() { renameDialog = false }

    fun openInfo()    { infoDialog = true }
    fun dismissInfo() { infoDialog = false }

    fun openDelete()    { deleteDialog = true }
    fun dismissDelete() { deleteDialog = false }

    /** conflictFiles·pendingSelectMode를 설정하고 충돌 다이얼로그를 표시한다. */
    fun openConflict(files: List<FileHolderItem>, selectMode: Int) {
        conflictFiles = files
        pendingSelectMode = selectMode
        showConflictDialog = true
    }
    fun dismissConflict() { showConflictDialog = false }
}

/** BottomButtonDialogState를 컴포지션에 기억한다. */
@Composable
// BottomButtonDialogState 인스턴스를 컴포지션 생존 동안 1회만 생성 — 키 없음(입력과 무관하게 동일 인스턴스 유지)
fun rememberBottomButtonDialogState() = remember { BottomButtonDialogState() }
