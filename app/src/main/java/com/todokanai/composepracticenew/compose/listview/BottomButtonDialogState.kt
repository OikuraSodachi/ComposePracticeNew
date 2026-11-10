package com.todokanai.composepracticenew.compose.listview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** BottomButtonListView의 입력·확인용 다이얼로그 open/close 상태를 보유한다. 파일 충돌 상태와 정보 다이얼로그(계산을 수반함)는 ViewModel이 보유한다. */
class BottomButtonDialogState {
    /** 열린 압축 다이얼로그가 압축 파일을 만들 폴더. null이면 닫혀 있다. 열 때 정해, 검증과 실행이 같은 폴더를 쓴다. */
    var compressTarget     by mutableStateOf<String?>(null); private set
    var renameDialog       by mutableStateOf(false); private set
    var deleteDialog       by mutableStateOf(false); private set

    /** [folder]에 압축 파일을 만드는 다이얼로그를 연다. */
    fun openCompress(folder: String) { compressTarget = folder }
    fun dismissCompress() { compressTarget = null }

    fun openRename()    { renameDialog = true }
    fun dismissRename() { renameDialog = false }

    fun openDelete()    { deleteDialog = true }
    fun dismissDelete() { deleteDialog = false }
}

/** BottomButtonDialogState를 컴포지션에 기억한다. */
@Composable
// BottomButtonDialogState 인스턴스를 컴포지션 생존 동안 1회만 생성 — 키 없음(입력과 무관하게 동일 인스턴스 유지)
fun rememberBottomButtonDialogState() = remember { BottomButtonDialogState() }
