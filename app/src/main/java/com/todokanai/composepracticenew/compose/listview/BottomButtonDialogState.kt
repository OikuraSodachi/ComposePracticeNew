package com.todokanai.composepracticenew.compose.listview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.todokanai.composepracticenew.compose.dialog.ScreenDialogState
import com.todokanai.composepracticenew.compose.dialog.rememberScreenDialogState
import com.todokanai.composepracticenew.viewmodel.BottomButtonDialogViewModel

/** BottomButtonListView의 입력·확인용 다이얼로그 open/close 상태를 보유한다. 파일 충돌 상태와 정보 다이얼로그(계산을 수반함)는 ViewModel이 보유한다. */
class BottomButtonDialogState : ScreenDialogState() {
    /** 열린 압축 다이얼로그가 압축 파일을 만들 폴더. null이면 닫혀 있다. 열 때 정해, 검증과 실행이 같은 폴더를 쓴다. */
    var compressTarget by mutableStateOf<String?>(null); private set
    /** 선택한 항목의 이름 변경·삭제 다이얼로그 열림 상태. */
    val entryActions = EntryActionDialogs(::beginOpening)

    /** [folder]에 압축 파일을 만드는 다이얼로그를 연다. */
    fun openCompress(folder: String) { beginOpening(); compressTarget = folder }
    fun dismissCompress() { compressTarget = null }

    override fun dismissAll() {
        compressTarget = null
        entryActions.dismissAll()
    }
}

/** 현재 화면의 [BottomButtonDialogState]를 반환한다. 유지·닫힘 규칙은 [rememberScreenDialogState]를 따른다. */
@Composable
fun rememberBottomButtonDialogState(): BottomButtonDialogState = rememberScreenDialogState(hiltViewModel<BottomButtonDialogViewModel>().state)
