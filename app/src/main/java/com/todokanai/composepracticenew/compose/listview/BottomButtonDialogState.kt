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
    var zipDialog          by mutableStateOf(false)
    var renameDialog       by mutableStateOf(false)
    var infoDialog         by mutableStateOf(false)
    var deleteDialog       by mutableStateOf(false)
    var showConflictDialog by mutableStateOf(false)
    var conflictFiles      by mutableStateOf<List<FileHolderItem>>(emptyList())
    var pendingSelectMode  by mutableStateOf(AppConstants.DEFAULT_MODE)
}

/** BottomButtonDialogState를 컴포지션에 기억한다. */
@Composable
fun rememberBottomButtonDialogState() = remember { BottomButtonDialogState() }
