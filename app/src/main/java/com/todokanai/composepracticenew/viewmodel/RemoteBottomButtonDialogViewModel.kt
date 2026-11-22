package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import com.todokanai.composepracticenew.compose.listview.RemoteBottomButtonDialogState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * [RemoteBottomButtonDialogState]를 화면(back stack 항목) 단위로 보관하는 ViewModel. 다이얼로그의 대상인 선택(coordinator ViewModel)과 함께 구성 변경에는 유지되고 프로세스 종료 시에는 사라진다.
 */
@HiltViewModel
class RemoteBottomButtonDialogViewModel @Inject constructor() : ViewModel() {
    /** 이 화면의 다이얼로그 열림 상태. */
    val state = RemoteBottomButtonDialogState()
}
