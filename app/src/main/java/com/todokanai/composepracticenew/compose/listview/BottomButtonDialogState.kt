package com.todokanai.composepracticenew.compose.listview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.todokanai.composepracticenew.compose.dialog.ScreenDialogState
import com.todokanai.composepracticenew.compose.dialog.rememberScreenDialogState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** BottomButtonListView의 입력·확인용 다이얼로그 open/close 상태를 보유한다. 파일 충돌 상태와 정보 다이얼로그(계산을 수반함)는 ViewModel이 보유한다. */
class BottomButtonDialogState : ScreenDialogState() {
    /** 열린 압축 다이얼로그가 압축 파일을 만들 폴더. null이면 닫혀 있다. 열 때 정해, 검증과 실행이 같은 폴더를 쓴다. */
    var compressTarget     by mutableStateOf<String?>(null); private set
    var renameDialog       by mutableStateOf(false); private set
    var deleteDialog       by mutableStateOf(false); private set

    /** [folder]에 압축 파일을 만드는 다이얼로그를 연다. */
    fun openCompress(folder: String) { beginOpening(); compressTarget = folder }
    fun dismissCompress() { compressTarget = null }

    fun openRename()    { beginOpening(); renameDialog = true }
    fun dismissRename() { renameDialog = false }

    fun openDelete()    { beginOpening(); deleteDialog = true }
    fun dismissDelete() { deleteDialog = false }

    override fun dismissAll() {
        compressTarget = null
        renameDialog = false
        deleteDialog = false
    }
}

/**
 * [BottomButtonDialogState]를 화면(back stack 항목) 단위로 보관하는 ViewModel. 다이얼로그의 대상인 선택(coordinator ViewModel)과 함께 구성 변경에는 유지되고 프로세스 종료 시에는 사라진다.
 */
@HiltViewModel
class BottomButtonDialogViewModel @Inject constructor() : ViewModel() {
    /** 이 화면의 다이얼로그 열림 상태. */
    val state = BottomButtonDialogState()
}

/** 현재 화면의 [BottomButtonDialogState]를 반환한다. 유지·닫힘 규칙은 [rememberScreenDialogState]를 따른다. */
@Composable
fun rememberBottomButtonDialogState(): BottomButtonDialogState = rememberScreenDialogState(hiltViewModel<BottomButtonDialogViewModel>().state)
