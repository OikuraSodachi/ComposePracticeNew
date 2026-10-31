package com.todokanai.composepracticenew.compose.listview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.todokanai.composepracticenew.compose.BottomButtons
import com.todokanai.composepracticenew.compose.ConfirmButtons
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.LocalSelectMode
import com.todokanai.composepracticenew.ui.model.SelectMode

/**
 * 로컬 화면의 selectMode에 따라 적절한 하단 버튼 UI를 렌더링한다.
 *
 * @param modifier 레이아웃 수정자
 * @param selectMode 현재 로컬 선택 모드
 * @param selectedList 현재 선택된 파일 목록
 * @param dialogState 다이얼로그 열기 트리거에 사용하는 상태 홀더
 * @param onConfirmWithConflictCheck 로컬 작업 실행을 요청하는 콜백 — 충돌 판단은 ViewModel이 수행한다
 * @param onSelectOperation 선택을 유지한 채 로컬 작업의 목적지 확인 단계로 전환하는 콜백
 * @param onResetSelection 선택 모드·선택 목록을 초기화하는 콜백 — 확인 단계의 취소도 선택을 비운다
 * @param onShowInfo 현재 선택 항목의 정보 다이얼로그를 여는 콜백
 * @param onConfirmDownload 다운로드 확인 콜백 — 선택 초기화는 실제 실행 시점에 coordinator가 담당한다
 * @param onEnterUploadMode 업로드 모드 진입 콜백
 */
@Composable
fun ModeAwareButtons(
    modifier: Modifier,
    selectMode: LocalSelectMode,
    selectedList: List<FileHolderItem>,
    dialogState: BottomButtonDialogState,
    onConfirmWithConflictCheck: (SelectMode.LocalOperation) -> Unit,
    onSelectOperation: (SelectMode.LocalOperation) -> Unit,
    onResetSelection: () -> Unit,
    onShowInfo: () -> Unit,
    onConfirmDownload: () -> Unit,
    onEnterUploadMode: () -> Unit,
) {
    when (selectMode) {
        SelectMode.Default -> Unit
        SelectMode.MultiSelect -> {
            BottomButtons(
                modifier = modifier,
                move = { onSelectOperation(SelectMode.LocalOperation.Move) },
                copy = { onSelectOperation(SelectMode.LocalOperation.Copy) },
                delete = { dialogState.openDelete() },
                zip = { dialogState.openZip() },
                unzip = { onSelectOperation(SelectMode.LocalOperation.Unzip) },
                unzipHere = { onSelectOperation(SelectMode.LocalOperation.UnzipHere) },
                rename = { dialogState.openRename() },
                info = onShowInfo,
                upload = {
                    onEnterUploadMode()
                    onResetSelection()
                },
                selectedList = selectedList
            )
        }
        is SelectMode.LocalOperation -> ConfirmButtons(
            modifier = modifier,
            confirm = { onConfirmWithConflictCheck(selectMode) },
            cancel = onResetSelection,
            mode = selectMode
        )
        SelectMode.Download -> ConfirmButtons(
            modifier = modifier,
            confirm = onConfirmDownload,
            cancel = onResetSelection,
            mode = SelectMode.Download
        )
    }
}
