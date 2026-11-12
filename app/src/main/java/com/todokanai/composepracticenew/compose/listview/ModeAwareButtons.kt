package com.todokanai.composepracticenew.compose.listview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.BottomButtonAction
import com.todokanai.composepracticenew.compose.BottomButtons
import com.todokanai.composepracticenew.compose.ConfirmButtons
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.LocalSelectMode
import com.todokanai.composepracticenew.ui.model.SelectMode

/** [operation]의 목적지 확인 단계로 전환하는 버튼 항목을 만든다. */
private fun operationAction(
    operation: SelectMode.LocalOperation,
    onSelectOperation: (SelectMode.LocalOperation) -> Unit,
) = BottomButtonAction(operation.labelRes) { onSelectOperation(operation) }

/** 다중 선택 모드에서 하단에 바로 보이는 버튼 항목(이동·복사·삭제)을 만든다. */
private fun multiSelectPrimaryActions(
    dialogState: BottomButtonDialogState,
    onSelectOperation: (SelectMode.LocalOperation) -> Unit,
): List<BottomButtonAction> = listOf(
    operationAction(SelectMode.LocalOperation.Move, onSelectOperation),
    operationAction(SelectMode.LocalOperation.Copy, onSelectOperation),
    BottomButtonAction(R.string.btn_delete, dialogState::openDelete),
)

/**
 * 다중 선택 모드의 더보기 메뉴 항목을 만든다. 압축은 압축 파일을 만들 폴더를 정할 수 있을 때, 이름 변경은 하나만 선택했을 때,
 * 압축 해제는 그 항목을 해제할 수 있을 때만 넣는다.
 * @param selectedList 현재 선택된 파일 목록
 * @param canUnzip 항목을 압축 해제할 수 있는지 판별하는 함수
 * @param archiveFolderOf 선택 목록으로 압축 파일을 만들 폴더를 정하는 함수. 정할 수 없으면 null
 * @param dialogState 다이얼로그 열기 트리거에 사용하는 상태 홀더
 * @param onSelectOperation 선택을 유지한 채 로컬 작업의 목적지 확인 단계로 전환하는 콜백
 * @param onShowInfo 현재 선택 항목의 정보 다이얼로그를 여는 콜백
 * @param onUpload 업로드 모드로 들어가는 콜백
 * @return 표시 순서대로 나열한 메뉴 항목
 */
internal fun multiSelectMoreActions(
    selectedList: List<FileHolderItem>,
    canUnzip: (FileHolderItem) -> Boolean,
    archiveFolderOf: (List<FileHolderItem>) -> String?,
    dialogState: BottomButtonDialogState,
    onSelectOperation: (SelectMode.LocalOperation) -> Unit,
    onShowInfo: () -> Unit,
    onUpload: () -> Unit,
): List<BottomButtonAction> = buildList {
    archiveFolderOf(selectedList)?.let { folder -> add(BottomButtonAction(R.string.btn_zip) { dialogState.openCompress(folder) }) }
    add(BottomButtonAction(R.string.btn_info, onShowInfo))
    add(BottomButtonAction(R.string.btn_upload, onUpload))
    val single = selectedList.singleOrNull() ?: return@buildList
    add(BottomButtonAction(R.string.btn_rename, dialogState::openRename))
    if (canUnzip(single)) {
        add(operationAction(SelectMode.LocalOperation.Unzip, onSelectOperation))
        add(operationAction(SelectMode.LocalOperation.UnzipHere, onSelectOperation))
    }
}

/**
 * 로컬 화면의 selectMode에 따라 적절한 하단 버튼 UI를 렌더링한다.
 *
 * @param modifier 레이아웃 수정자
 * @param selectMode 현재 로컬 선택 모드
 * @param selectedList 현재 선택된 파일 목록
 * @param canUnzip 항목을 압축 해제할 수 있는지 판별하는 함수
 * @param archiveFolderOf 선택 목록으로 압축 파일을 만들 폴더를 정하는 함수. 정할 수 없으면 null이며 압축 메뉴를 두지 않는다
 * @param dialogState 다이얼로그 열기 트리거에 사용하는 상태 홀더
 * @param onConfirmOperation 로컬 작업 실행을 요청하는 콜백 — 같은 이름의 항목은 작업이 쓰는 중에 묻는다
 * @param onSelectOperation 선택을 유지한 채 로컬 작업의 목적지 확인 단계로 전환하는 콜백
 * @param onResetSelection 선택 모드·선택 목록을 초기화하는 콜백 — 확인 단계의 취소도 선택을 비운다
 * @param onShowInfo 현재 선택 항목의 정보 다이얼로그를 여는 콜백
 * @param transfer 다운로드 확인·업로드 모드 진입 콜백 묶음
 */
@Composable
fun ModeAwareButtons(
    modifier: Modifier,
    selectMode: LocalSelectMode,
    selectedList: List<FileHolderItem>,
    canUnzip: (FileHolderItem) -> Boolean,
    archiveFolderOf: (List<FileHolderItem>) -> String?,
    dialogState: BottomButtonDialogState,
    onConfirmOperation: (SelectMode.LocalOperation) -> Unit,
    onSelectOperation: (SelectMode.LocalOperation) -> Unit,
    onResetSelection: () -> Unit,
    onShowInfo: () -> Unit,
    transfer: LocalTransferActions,
) {
    when (selectMode) {
        SelectMode.Default -> Unit
        SelectMode.MultiSelect -> {
            /** 하단에 바로 보이는 버튼 항목. */
            // dialogState·onSelectOperation을 키로 — 선택이 바뀌어도 그대로 재사용해 항목 목록을 새로 만들지 않는다
            val primaryActions = remember(dialogState, onSelectOperation) {
                multiSelectPrimaryActions(dialogState, onSelectOperation)
            }
            /** 더보기 메뉴 항목. */
            // 선택 목록·판별 함수·항목이 캡처하는 콜백을 키로 — 이 값들이 바뀔 때만 메뉴 항목을 다시 만들고, 바뀐 콜백을 놓치지 않는다
            val moreActions = remember(selectedList, canUnzip, archiveFolderOf, dialogState, onSelectOperation, onShowInfo, transfer) {
                // 업로드 진입이 로컬 선택을 꺼내며 비우므로 여기서 따로 비우지 않는다
                multiSelectMoreActions(selectedList, canUnzip, archiveFolderOf, dialogState, onSelectOperation, onShowInfo, transfer.onEnterUploadMode)
            }
            BottomButtons(
                modifier = modifier,
                primaryActions = primaryActions,
                moreActions = moreActions,
            )
        }
        is SelectMode.LocalOperation -> ConfirmButtons(
            modifier = modifier,
            confirm = { onConfirmOperation(selectMode) },
            cancel = onResetSelection,
            mode = selectMode
        )
        SelectMode.Download -> ConfirmButtons(
            modifier = modifier,
            confirm = transfer.onConfirmDownload,
            cancel = onResetSelection,
            mode = SelectMode.Download
        )
    }
}
