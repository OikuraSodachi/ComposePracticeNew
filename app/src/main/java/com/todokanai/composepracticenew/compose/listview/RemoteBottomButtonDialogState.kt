package com.todokanai.composepracticenew.compose.listview

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.todokanai.composepracticenew.compose.dialog.ScreenDialogState
import com.todokanai.composepracticenew.compose.dialog.rememberScreenDialogState
import com.todokanai.composepracticenew.viewmodel.RemoteBottomButtonDialogViewModel

/** RemoteBottomButtonListView의 이름 변경·삭제 다이얼로그 open/close 상태를 보유한다. 대상은 coordinator의 선택 목록이라 플래그만 보관한다. */
class RemoteBottomButtonDialogState : ScreenDialogState() {
    /** 선택한 항목의 이름 변경·삭제 다이얼로그 열림 상태. */
    val entryActions = EntryActionDialogs(::beginOpening)

    override fun dismissAll() = entryActions.dismissAll()
}

/** 현재 화면의 [RemoteBottomButtonDialogState]를 반환한다. 유지·닫힘 규칙은 [rememberScreenDialogState]를 따른다. */
@Composable
fun rememberRemoteBottomButtonDialogState(): RemoteBottomButtonDialogState =
    rememberScreenDialogState(hiltViewModel<RemoteBottomButtonDialogViewModel>().state)
