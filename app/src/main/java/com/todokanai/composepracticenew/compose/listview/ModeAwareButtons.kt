package com.todokanai.composepracticenew.compose.listview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.todokanai.composepracticenew.compose.BottomButtons
import com.todokanai.composepracticenew.compose.ConfirmButtons
import com.todokanai.composepracticenew.myobjects.AppConstants
import com.todokanai.composepracticenew.ui.model.FileHolderItem

/**
 * selectMode에 따라 적절한 하단 버튼 UI를 렌더링한다.
 *
 * @param modifier 레이아웃 수정자
 * @param selectMode 현재 선택 모드
 * @param selectedList 현재 선택된 파일 목록
 * @param dialogState 다이얼로그 열기 트리거에 사용하는 상태 홀더
 * @param onConfirmWithConflictCheck 충돌 여부를 검사하고, 없으면 즉시 확인·있으면 충돌 다이얼로그를 표시하는 콜백
 * @param onSelectModeChange 선택 모드 변경 요청 콜백
 * @param onClearSelection 선택 목록 초기화 콜백
 * @param onConfirmDownload 다운로드 확인 콜백
 * @param onEnterUploadMode 업로드 모드 진입 콜백
 */
@Composable
fun ModeAwareButtons(
    modifier: Modifier,
    selectMode: Int,
    selectedList: List<FileHolderItem>,
    dialogState: BottomButtonDialogState,
    onConfirmWithConflictCheck: () -> Unit,
    onSelectModeChange: (Int) -> Unit,
    onClearSelection: () -> Unit,
    onConfirmDownload: () -> Unit,
    onEnterUploadMode: () -> Unit,
) {
    when (selectMode) {
        AppConstants.MULTI_SELECT_MODE -> {
            BottomButtons(
                modifier = modifier,
                move = { onSelectModeChange(AppConstants.CONFIRM_MODE_MOVE) },
                copy = { onSelectModeChange(AppConstants.CONFIRM_MODE_COPY) },
                delete = { dialogState.deleteDialog = true },
                zip = { dialogState.zipDialog = true },
                unzip = { onSelectModeChange(AppConstants.CONFIRM_MODE_UNZIP) },
                unzipHere = { onSelectModeChange(AppConstants.CONFIRM_MODE_UNZIP_HERE) },
                rename = { dialogState.renameDialog = true },
                info = { dialogState.infoDialog = true },
                upload = {
                    onEnterUploadMode()
                    onSelectModeChange(AppConstants.DEFAULT_MODE)
                    onClearSelection()
                },
                selectedList = selectedList
            )
        }
        AppConstants.CONFIRM_MODE_MOVE -> {
            ConfirmButtons(
                modifier = modifier,
                confirm = onConfirmWithConflictCheck,
                cancel = { onSelectModeChange(AppConstants.DEFAULT_MODE) },
                mode = AppConstants.CONFIRM_MODE_MOVE
            )
        }
        AppConstants.CONFIRM_MODE_COPY -> {
            ConfirmButtons(
                modifier = modifier,
                confirm = onConfirmWithConflictCheck,
                cancel = { onSelectModeChange(AppConstants.DEFAULT_MODE) },
                mode = AppConstants.CONFIRM_MODE_COPY
            )
        }
        AppConstants.CONFIRM_MODE_UNZIP, AppConstants.CONFIRM_MODE_UNZIP_HERE -> {
            ConfirmButtons(
                modifier = modifier,
                confirm = onConfirmWithConflictCheck,
                cancel = { onSelectModeChange(AppConstants.DEFAULT_MODE) },
                mode = selectMode
            )
        }
        AppConstants.CONFIRM_MODE_DOWNLOAD -> {
            ConfirmButtons(
                modifier = modifier,
                confirm = {
                    onConfirmDownload()
                    onSelectModeChange(AppConstants.DEFAULT_MODE)
                    onClearSelection()
                },
                cancel = { onSelectModeChange(AppConstants.DEFAULT_MODE) },
                mode = AppConstants.CONFIRM_MODE_DOWNLOAD
            )
        }
    }
}
