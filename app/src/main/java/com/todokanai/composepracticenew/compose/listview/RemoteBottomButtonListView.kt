package com.todokanai.composepracticenew.compose.listview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.ConfirmButtons
import com.todokanai.composepracticenew.ui.model.PendingRename
import com.todokanai.composepracticenew.ui.model.RemoteSelectMode
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.viewmodel.SubmittedSelection

/**
 * 원격 파일 목록 하단 버튼 영역 — 멀티셀렉트 모드에서 다운로드·삭제·이름변경을 제공한다.
 *
 * @param modifier 레이아웃 수정자
 * @param selectMode 현재 선택 모드
 * @param onResetSelection 기본 모드로 되돌리고 선택 목록을 비우는 콜백 — 취소·뒤로가기용
 * @param onSubmitSelection 확인한 작업의 대상으로 선택을 꺼내고 기본 모드로 되돌리는 콜백. 꺼낼 선택이 없으면 null
 * @param selectedList 선택 목록을 반환하는 함수 — 이 컴포저블에서 읽어 선택 변경 시 리컴포지션을 하단 버튼 영역으로 한정한다
 * @param rename 이름 변경 다이얼로그의 콜백 묶음
 * @param pendingRename 덮어쓰기 확인을 기다리는 이름 변경. null이면 확인 다이얼로그를 표시하지 않음
 * @param onDelete 선택에서 꺼낸 파일 일괄 삭제 실행 콜백
 * @param onEnterDownloadMode 원격 선택을 꺼내 다운로드 모드로 진입하는 콜백
 * @param onConfirmUpload 업로드 확인 콜백
 */
@Composable
fun RemoteBottomButtonListView(
    modifier: Modifier,
    selectMode: RemoteSelectMode,
    onResetSelection: () -> Unit,
    onSubmitSelection: () -> SubmittedSelection?,
    selectedList: () -> List<FileHolderItem>,
    rename: RenameActions,
    pendingRename: PendingRename?,
    onDelete: (SubmittedSelection) -> Unit,
    onEnterDownloadMode: () -> Unit,
    onConfirmUpload: () -> Unit,
) {
    val currentSelectedList = selectedList()
    /** 이 화면의 이름 변경·삭제 다이얼로그 열림 상태. */
    // 화면 단위 ViewModel에 보관된 다이얼로그 상태 — 대상인 coordinator의 선택 목록과 수명이 같아, 회전 시 유지되고
    // 화면 이동·프로세스 종료 후 복귀 시 닫힌다(rememberScreenDialogState 참고)
    val dialogState = rememberRemoteBottomButtonDialogState()

    val strRemoteDownload = stringResource(R.string.btn_remote_download)
    val strRemoteDelete   = stringResource(R.string.btn_remote_delete)
    val strRemoteRename   = stringResource(R.string.btn_remote_rename)

    SelectionEntryDialogs(
        openId = dialogState.openId,
        selectedList = currentSelectedList,
        entryActions = dialogState.entryActions,
        onSubmitSelection = onSubmitSelection,
        rename = rename,
        pendingRename = pendingRename,
        onDelete = onDelete,
    )

    when (selectMode) {
        SelectMode.MultiSelect -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onEnterDownloadMode) { Text(strRemoteDownload) }
                TextButton(
                    enabled = currentSelectedList.isNotEmpty(),
                    onClick = dialogState.entryActions::openDelete
                ) { Text(strRemoteDelete) }
                TextButton(
                    enabled = currentSelectedList.size == 1,
                    onClick = dialogState.entryActions::openRename
                ) { Text(strRemoteRename) }
            }
        }
        is SelectMode.Upload -> {
            ConfirmButtons(
                modifier = modifier,
                // 선택 초기화는 실제 실행 시점에 coordinator가 담당 — 충돌 확인을 취소하면 업로드 확인 모드가 유지된다
                confirm = onConfirmUpload,
                cancel = onResetSelection,
                mode = selectMode
            )
        }
        SelectMode.Default -> Unit
    }
}
