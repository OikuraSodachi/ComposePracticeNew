package com.todokanai.composepracticenew.compose.listview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** BottomButtonListView의 입력·확인용 다이얼로그 open/close 상태를 보유한다. 파일 충돌 상태는 ViewModel이 보유한다. */
class BottomButtonDialogState {
    var zipDialog          by mutableStateOf(false); private set
    var renameDialog       by mutableStateOf(false); private set
    var infoDialog         by mutableStateOf(false); private set
    var deleteDialog       by mutableStateOf(false); private set

    fun openZip()    { zipDialog = true }
    fun dismissZip() { zipDialog = false }

    fun openRename()    { renameDialog = true }
    fun dismissRename() { renameDialog = false }

    fun openInfo()    { infoDialog = true }
    fun dismissInfo() { infoDialog = false }

    fun openDelete()    { deleteDialog = true }
    fun dismissDelete() { deleteDialog = false }
}

/** BottomButtonDialogState를 컴포지션에 기억한다. */
@Composable
// BottomButtonDialogState 인스턴스를 컴포지션 생존 동안 1회만 생성 — 키 없음(입력과 무관하게 동일 인스턴스 유지)
fun rememberBottomButtonDialogState() = remember { BottomButtonDialogState() }
