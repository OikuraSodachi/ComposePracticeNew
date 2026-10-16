package com.todokanai.composepracticenew.compose.listview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.todokanai.composepracticenew.compose.BottomButtons
import com.todokanai.composepracticenew.compose.ConfirmButtons
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.SelectMode

/**
 * selectMode에 따라 적절한 하단 버튼 UI를 렌더링한다.
 *
 * @param modifier 레이아웃 수정자
 * @param selectMode 현재 선택 모드
 * @param selectedList 현재 선택된 파일 목록
 * @param dialogState 다이얼로그 열기 트리거에 사용하는 상태 홀더
 * @param onConfirmWithConflictCheck 확인 모드 작업 실행을 요청하는 콜백 — 충돌 판단은 ViewModel이 수행한다
 * @param onSelectModeChange 선택 모드 변경 요청 콜백
 * @param onResetSelection 선택 모드·선택 목록을 초기화하는 콜백
 * @param onConfirmDownload 다운로드 확인 콜백
 * @param onEnterUploadMode 업로드 모드 진입 콜백
 */
@Composable
fun ModeAwareButtons(
    modifier: Modifier,
    selectMode: SelectMode,
    selectedList: List<FileHolderItem>,
    dialogState: BottomButtonDialogState,
    onConfirmWithConflictCheck: (SelectMode.Confirm) -> Unit,
    onSelectModeChange: (SelectMode) -> Unit,
    onResetSelection: () -> Unit,
    onConfirmDownload: () -> Unit,
    onEnterUploadMode: () -> Unit,
) {
    when (selectMode) {
        SelectMode.Default -> Unit
        SelectMode.MultiSelect -> {
            BottomButtons(
                modifier = modifier,
                move = { onSelectModeChange(SelectMode.Confirm.Move) },
                copy = { onSelectModeChange(SelectMode.Confirm.Copy) },
                delete = { dialogState.openDelete() },
                zip = { dialogState.openZip() },
                unzip = { onSelectModeChange(SelectMode.Confirm.Unzip) },
                unzipHere = { onSelectModeChange(SelectMode.Confirm.UnzipHere) },
                rename = { dialogState.openRename() },
                info = { dialogState.openInfo() },
                upload = {
                    onEnterUploadMode()
                    onResetSelection()
                },
                selectedList = selectedList
            )
        }
        is SelectMode.Confirm -> LocalConfirmButtons(
            modifier = modifier,
            mode = selectMode,
            onConfirmWithConflictCheck = onConfirmWithConflictCheck,
            onCancel = { onSelectModeChange(SelectMode.Default) },
            onResetSelection = onResetSelection,
            onConfirmDownload = onConfirmDownload
        )
    }
}

/** 로컬 화면의 확인 모드별 확인 버튼을 렌더링한다. 업로드 확인은 원격 화면 전용이므로 표시하지 않는다. */
@Composable
private fun LocalConfirmButtons(
    modifier: Modifier,
    mode: SelectMode.Confirm,
    onConfirmWithConflictCheck: (SelectMode.Confirm) -> Unit,
    onCancel: () -> Unit,
    onResetSelection: () -> Unit,
    onConfirmDownload: () -> Unit,
) {
    when (mode) {
        SelectMode.Confirm.Copy,
        SelectMode.Confirm.Move,
        SelectMode.Confirm.Unzip,
        SelectMode.Confirm.UnzipHere -> ConfirmButtons(
            modifier = modifier,
            confirm = { onConfirmWithConflictCheck(mode) },
            cancel = onCancel,
            mode = mode
        )
        SelectMode.Confirm.Download -> ConfirmButtons(
            modifier = modifier,
            confirm = {
                onConfirmDownload()
                onResetSelection()
            },
            cancel = onCancel,
            mode = mode
        )
        SelectMode.Confirm.Upload -> Unit
    }
}
