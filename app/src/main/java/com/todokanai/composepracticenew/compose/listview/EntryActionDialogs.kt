package com.todokanai.composepracticenew.compose.listview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 선택한 항목의 이름 변경·삭제 다이얼로그 열림 상태. 로컬·원격 목록 화면의 다이얼로그 상태가 함께 갖는다(#465). 대상은 coordinator의 선택 목록이라 플래그만 보관한다.
 * @param beginOpening 다이얼로그를 열 때 부르는 함수 — 이 상태를 가진 ScreenDialogState의 새 openId를 발급한다
 */
class EntryActionDialogs(private val beginOpening: () -> Unit) {
    /** 이름 변경 다이얼로그 열림 여부. 대상은 선택 목록의 단일 항목이다. */
    var renameDialog by mutableStateOf(false); private set
    /** 삭제 다이얼로그 열림 여부. */
    var deleteDialog by mutableStateOf(false); private set

    fun openRename()    { beginOpening(); renameDialog = true }
    fun dismissRename() { renameDialog = false }

    fun openDelete()    { beginOpening(); deleteDialog = true }
    fun dismissDelete() { deleteDialog = false }

    /** 이름 변경·삭제 다이얼로그를 모두 닫는다. */
    fun dismissAll() {
        renameDialog = false
        deleteDialog = false
    }
}
