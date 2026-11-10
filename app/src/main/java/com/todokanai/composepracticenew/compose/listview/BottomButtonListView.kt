package com.todokanai.composepracticenew.compose.listview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.todokanai.composepracticenew.ui.model.ConflictResolution
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.LocalSelectMode
import com.todokanai.composepracticenew.ui.model.PendingConflict
import com.todokanai.composepracticenew.ui.model.PendingRename
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.viewmodel.SubmittedSelection

/**
 * 로컬 화면의 하단 파일 작업 버튼(ModeAwareButtons)과 관련 다이얼로그(BottomButtonDialogHost)를 조합한다.
 * 확인을 누르면 작업은 확정이다. 작업 대상은 [onSubmitSelection]으로 선택을 꺼내며 받으므로 선택은 그때 비워지고, 충돌 검사와 실행은 ViewModel 쪽이 화면과 무관하게 끝까지 진행한다.
 * @param onSelectOperation 선택을 유지한 채 로컬 작업의 목적지 확인 단계로 전환하는 콜백
 * @param onResetSelection 기본 모드로 되돌리고 선택 목록을 비우는 콜백 — 취소·뒤로가기용
 * @param onSubmitSelection 선택을 작업 대상으로 꺼내고 기본 모드로 되돌리는 콜백. 꺼낼 선택이 없으면 null
 * @param selectedList 선택 목록을 반환하는 함수 — 이 컴포저블에서 읽어 선택 변경 시 리컴포지션을 하단 버튼 영역으로 한정한다
 * @param info 선택 항목 정보 다이얼로그의 표시 값과 콜백 묶음
 * @param transfer 다운로드 확인·업로드 모드 진입 콜백 묶음
 * @param compress 압축 다이얼로그의 콜백 묶음
 * @param canUnzip 항목을 압축 해제할 수 있는지 판별하는 함수 — 압축 해제 버튼 표시 여부를 정한다
 * @param rename 이름 변경 다이얼로그의 콜백 묶음
 * @param pendingRename 덮어쓰기 확인을 기다리는 이름 변경. null이면 확인 다이얼로그를 표시하지 않음
 */
@Composable
fun BottomButtonListView(
    modifier: Modifier,
    selectMode: LocalSelectMode,
    onSelectOperation: (SelectMode.LocalOperation) -> Unit,
    onResetSelection: () -> Unit,
    onSubmitSelection: () -> SubmittedSelection?,
    selectedList: () -> List<FileHolderItem>,
    info: SelectionInfoActions,
    pendingConflict: PendingConflict?,
    transfer: LocalTransferActions,
    onRequestConfirm: (SubmittedSelection, SelectMode.LocalOperation) -> Unit,
    onResolveConflict: (ConflictResolution) -> Unit,
    compress: CompressActions,
    canUnzip: (FileHolderItem) -> Boolean,
    rename: RenameActions,
    pendingRename: PendingRename?,
    onDelete: (SubmittedSelection) -> Unit,
) {
    val dialogState = rememberBottomButtonDialogState()
    val currentSelectedList = selectedList()

    // rememberUpdatedState — 람다를 재생성하지 않고 실행 시점에 최신 선택 목록을 읽도록 보장
    val latestSelectedList by rememberUpdatedState(currentSelectedList)

    /** 선택을 꺼내 작업을 확정해 넘긴다. 선택은 꺼낼 때 비워지고, 충돌 검사와 실행은 ViewModel 쪽에서 끝까지 진행된다. */
    // 두 콜백을 키로 — 콜백이 바뀔 때만 람다를 새로 만든다
    val onConfirmWithConflictCheck: (SelectMode.LocalOperation) -> Unit = remember(onRequestConfirm, onSubmitSelection) {
        { mode -> onSubmitSelection()?.let { onRequestConfirm(it, mode) } }
    }
    /** 누른 시점의 선택 목록으로 정보 다이얼로그를 연다. */
    // info를 키로 — 선택 목록은 rememberUpdatedState로 실행 시점에 읽고, 콜백 묶음이 바뀔 때만 람다를 새로 만든다
    val onShowSelectedInfo: () -> Unit = remember(info) { { info.onShow(latestSelectedList) } }

    ModeAwareButtons(
        modifier = modifier,
        selectMode = selectMode,
        selectedList = currentSelectedList,
        canUnzip = canUnzip,
        archiveFolderOf = compress.folderOf,
        dialogState = dialogState,
        onConfirmWithConflictCheck = onConfirmWithConflictCheck,
        onSelectOperation = onSelectOperation,
        onResetSelection = onResetSelection,
        onShowInfo = onShowSelectedInfo,
        transfer = transfer,
    )
    BottomButtonDialogHost(
        dialogState = dialogState,
        pendingConflict = pendingConflict,
        selectedList = currentSelectedList,
        info = info,
        onSubmitSelection = onSubmitSelection,
        compress = compress,
        rename = rename,
        pendingRename = pendingRename,
        onDelete = onDelete,
        onResolveConflict = onResolveConflict,
    )
}
