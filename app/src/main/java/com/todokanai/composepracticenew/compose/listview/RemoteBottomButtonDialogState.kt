package com.todokanai.composepracticenew.compose.listview

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.todokanai.composepracticenew.compose.dialog.ScreenDialogState
import com.todokanai.composepracticenew.compose.dialog.rememberScreenDialogState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** RemoteBottomButtonListView의 이름 변경·삭제 다이얼로그 open/close 상태를 보유한다. 대상은 coordinator의 선택 목록이라 플래그만 보관한다. */
class RemoteBottomButtonDialogState : ScreenDialogState() {
    /** 선택한 항목의 이름 변경·삭제 다이얼로그 열림 상태. */
    val entryActions = EntryActionDialogs(::beginOpening)

    override fun dismissAll() = entryActions.dismissAll()
}

/**
 * [RemoteBottomButtonDialogState]를 화면(back stack 항목) 단위로 보관하는 ViewModel. 다이얼로그의 대상인 선택(coordinator ViewModel)과 함께 구성 변경에는 유지되고 프로세스 종료 시에는 사라진다.
 */
@HiltViewModel
class RemoteBottomButtonDialogViewModel @Inject constructor() : ViewModel() {
    /** 이 화면의 다이얼로그 열림 상태. */
    val state = RemoteBottomButtonDialogState()
}

/** 현재 화면의 [RemoteBottomButtonDialogState]를 반환한다. 유지·닫힘 규칙은 [rememberScreenDialogState]를 따른다. */
@Composable
fun rememberRemoteBottomButtonDialogState(): RemoteBottomButtonDialogState =
    rememberScreenDialogState(hiltViewModel<RemoteBottomButtonDialogViewModel>().state)
