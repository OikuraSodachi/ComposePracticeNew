package com.todokanai.composepracticenew.compose.listview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.todokanai.composepracticenew.ui.model.ConflictResolution
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.LocalSelectMode
import com.todokanai.composepracticenew.ui.model.PendingConflict
import com.todokanai.composepracticenew.ui.model.PendingRename
import com.todokanai.composepracticenew.ui.model.SelectMode

/**
 * 로컬 화면의 하단 파일 작업 버튼(ModeAwareButtons)과 관련 다이얼로그(BottomButtonDialogHost)를 조합한다.
 * 충돌 검사와 실행 여부 판단은 ViewModel이 수행하며, 이 컴포저블은 검사를 지금 모드에 묶어 기다리고 결과에 따라 선택 상태만 초기화한다.
 * @param onSelectOperation 선택을 유지한 채 로컬 작업의 목적지 확인 단계로 전환하는 콜백
 * @param onResetSelection 기본 모드로 되돌리고 선택 목록을 비우는 콜백
 * @param selectedList 선택 목록을 반환하는 함수 — 이 컴포저블에서 읽어 선택 변경 시 리컴포지션을 하단 버튼 영역으로 한정한다
 * @param info 선택 항목 정보 다이얼로그의 표시 값과 콜백 묶음
 * @param transfer 다운로드 확인·업로드 모드 진입 콜백 묶음
 * @param zip 압축 다이얼로그의 콜백 묶음
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
    selectedList: () -> List<FileHolderItem>,
    info: SelectionInfoActions,
    pendingConflict: PendingConflict?,
    transfer: LocalTransferActions,
    onRequestConfirm: suspend (List<FileHolderItem>, SelectMode.LocalOperation) -> Boolean,
    onResolveConflict: (ConflictResolution) -> Boolean,
    zip: ZipActions,
    canUnzip: (FileHolderItem) -> Boolean,
    rename: RenameActions,
    pendingRename: PendingRename?,
    onDelete: (List<FileHolderItem>) -> Unit,
) {
    val dialogState = rememberBottomButtonDialogState()
    val currentSelectedList = selectedList()

    // rememberUpdatedState — 람다를 재생성하지 않고 실행 시점에 최신 선택 목록을 읽도록 보장
    val latestSelectedList by rememberUpdatedState(currentSelectedList)

    /** 확인 버튼으로 요청한 작업. 충돌 검사(압축 해제는 아카이브 읽기)를 기다리는 동안만 값이 있다. */
    // selectMode를 키로 — 모드를 벗어나면(취소·뒤로가기) 요청을 버려, 아래 LaunchedEffect가 진행 중인 검사를 취소하고 실행하지 않게 한다
    val confirmRequest = remember(selectMode) { mutableStateOf<SelectMode.LocalOperation?>(null) }
    // confirmRequest.value를 키로 — 요청이 생기면 검사를 시작하고 요청이 사라지면 취소한다. 검사 중 다시 눌러도 키가 같아 두 번 실행하지 않는다
    LaunchedEffect(confirmRequest.value) {
        val mode = confirmRequest.value ?: return@LaunchedEffect
        if (onRequestConfirm(latestSelectedList, mode)) onResetSelection()
        confirmRequest.value = null
    }
    // confirmRequest를 키로 — 모드가 바뀌어 요청 상태가 새로 만들어지면 새 상태에 쓰도록 람다를 다시 만든다
    val onConfirmWithConflictCheck: (SelectMode.LocalOperation) -> Unit = remember(confirmRequest) {
        { mode -> confirmRequest.value = mode }
    }
    /** 누른 시점의 선택 목록으로 정보 다이얼로그를 연다. */
    // info를 키로 — 선택 목록은 rememberUpdatedState로 실행 시점에 읽고, 콜백 묶음이 바뀔 때만 람다를 새로 만든다
    val onShowSelectedInfo: () -> Unit = remember(info) { { info.onShow(latestSelectedList) } }

    ModeAwareButtons(
        modifier = modifier,
        selectMode = selectMode,
        selectedList = currentSelectedList,
        canUnzip = canUnzip,
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
        onResetSelection = onResetSelection,
        zip = zip,
        rename = rename,
        pendingRename = pendingRename,
        onDelete = onDelete,
        onResolveConflict = onResolveConflict,
    )
}
