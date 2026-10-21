package com.todokanai.composepracticenew.compose.listview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.ui.model.ConflictResolution
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.LocalSelectMode
import com.todokanai.composepracticenew.ui.model.PendingConflict
import com.todokanai.composepracticenew.ui.model.PendingRename
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.ui.model.SelectionSummary

/**
 * 로컬 화면의 하단 파일 작업 버튼(ModeAwareButtons)과 관련 다이얼로그(BottomButtonDialogHost)를 조합한다.
 * 충돌 검사와 실행 여부 판단은 ViewModel이 수행하며, 이 컴포저블은 결과에 따라 선택 상태만 초기화한다.
 * @param selectedList 선택 목록을 반환하는 함수 — 이 컴포저블에서 읽어 선택 변경 시 리컴포지션을 하단 버튼 영역으로 한정한다
 * @param selectionSummary 선택 목록 요약을 반환하는 함수 — 정보 다이얼로그가 열렸을 때만 읽는다
 * @param validateZipName 압축 파일 이름(확장자 제외)을 검증해 위반한 규칙 집합을 반환하는 함수
 * @param validateRename 대상 항목과 새 이름을 검증해 위반한 규칙 집합을 반환하는 함수(같은 폴더의 종류가 다른 같은 이름 항목 포함)
 * @param pendingRename 덮어쓰기 확인을 기다리는 이름 변경. null이면 확인 다이얼로그를 표시하지 않음
 * @param onConfirmRename 이름 변경 덮어쓰기(합치기) 확인 콜백
 * @param onCancelRename 이름 변경 덮어쓰기 취소 콜백
 */
@Composable
fun BottomButtonListView(
    modifier: Modifier,
    selectMode: LocalSelectMode,
    onSelectModeChange: (LocalSelectMode) -> Unit,
    onClearSelection: () -> Unit,
    selectedList: () -> List<FileHolderItem>,
    selectionSummary: () -> SelectionSummary,
    pendingConflict: PendingConflict?,
    onConfirmDownload: () -> Unit = {},
    onEnterUploadMode: () -> Unit = {},
    onRequestConfirm: (List<FileHolderItem>, SelectMode.LocalOperation) -> Boolean,
    onResolveConflict: (ConflictResolution) -> Boolean,
    onZip: (List<FileHolderItem>, String) -> Unit,
    validateZipName: (String) -> Set<FileNameError>,
    onRename: (FileHolderItem, String) -> Unit,
    validateRename: (FileHolderItem, String) -> Set<FileNameError>,
    pendingRename: PendingRename?,
    onConfirmRename: () -> Unit,
    onCancelRename: () -> Unit,
    onDelete: (List<FileHolderItem>) -> Unit,
) {
    val dialogState = rememberBottomButtonDialogState()
    val currentSelectedList = selectedList()

    // rememberUpdatedState — 람다를 재생성하지 않고 실행 시점에 최신 선택 목록을 읽도록 보장
    val latestSelectedList by rememberUpdatedState(currentSelectedList)

    // 키 없음 — onSelectModeChange·onClearSelection은 ViewModel 메서드 참조라 안정적이므로 재생성 불필요
    val onResetSelection: () -> Unit = remember {
        {
            onSelectModeChange(SelectMode.Default)
            onClearSelection()
        }
    }

    // 키 없음 — 선택 목록은 rememberUpdatedState로 실행 시점에 읽고, 나머지 캡처는 안정 참조
    val onConfirmWithConflictCheck: (SelectMode.LocalOperation) -> Unit = remember {
        { mode -> if (onRequestConfirm(latestSelectedList, mode)) onResetSelection() }
    }

    ModeAwareButtons(
        modifier = modifier,
        selectMode = selectMode,
        selectedList = currentSelectedList,
        dialogState = dialogState,
        onConfirmWithConflictCheck = onConfirmWithConflictCheck,
        onSelectModeChange = onSelectModeChange,
        onResetSelection = onResetSelection,
        onConfirmDownload = onConfirmDownload,
        onEnterUploadMode = onEnterUploadMode,
    )
    BottomButtonDialogHost(
        dialogState = dialogState,
        pendingConflict = pendingConflict,
        selectedList = currentSelectedList,
        selectionSummary = selectionSummary,
        onResetSelection = onResetSelection,
        onZip = onZip,
        validateZipName = validateZipName,
        onRename = onRename,
        validateRename = validateRename,
        pendingRename = pendingRename,
        onConfirmRename = onConfirmRename,
        onCancelRename = onCancelRename,
        onDelete = onDelete,
        onResolveConflict = onResolveConflict,
    )
}
