package com.todokanai.composepracticenew.compose.frag

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

/** OptionFrag의 새 폴더·정렬 다이얼로그 열림 상태를 보유한다. */
class OptionDialogState : ScreenDialogState() {
    /** 열린 새 폴더 다이얼로그가 폴더를 만들 대상 폴더. null이면 닫혀 있다. 열 때 정해, 검증과 실행이 같은 폴더를 쓴다. */
    var newFolderTarget by mutableStateOf<String?>(null); private set
    /** 정렬 다이얼로그 열림 여부. */
    var sortDialog by mutableStateOf(false); private set

    /** [target] 폴더에 새 폴더를 만드는 다이얼로그를 연다. */
    fun openNewFolder(target: String) { beginOpening(); newFolderTarget = target }
    fun dismissNewFolder() { newFolderTarget = null }

    fun openSort()    { beginOpening(); sortDialog = true }
    fun dismissSort() { sortDialog = false }

    override fun dismissAll() {
        newFolderTarget = null
        sortDialog = false
    }
}

/**
 * [OptionDialogState]를 화면(back stack 항목) 단위로 보관하는 ViewModel. 구성 변경에는 유지되고 프로세스 종료 시에는 사라진다.
 */
@HiltViewModel
class OptionDialogViewModel @Inject constructor() : ViewModel() {
    /** 이 화면의 다이얼로그 열림 상태. */
    val state = OptionDialogState()
}

/** 현재 화면의 [OptionDialogState]를 반환한다. 유지·닫힘 규칙은 [rememberScreenDialogState]를 따른다. */
@Composable
fun rememberOptionDialogState(): OptionDialogState = rememberScreenDialogState(hiltViewModel<OptionDialogViewModel>().state)
