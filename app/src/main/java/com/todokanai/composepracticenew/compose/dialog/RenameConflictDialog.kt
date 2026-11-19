package com.todokanai.composepracticenew.compose.dialog

import androidx.annotation.StringRes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.presets.dialog.ConfirmCancelDialog
import com.todokanai.composepracticenew.model.RenameOverwrite
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.PendingRename

/**
 * 이름 변경 시 같은 이름의 항목이 있을 때 덮어쓸지(폴더는 합칠지) 묻는 다이얼로그.
 * @param pending 확인을 기다리는 이름 변경
 * @param onConfirm 덮어쓰기(합치기)를 확인했을 때의 콜백
 * @param onDismiss 취소·닫기 콜백
 */
@Composable
fun RenameConflictDialog(
    pending: PendingRename,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ConfirmCancelDialog(
        modifier = Modifier,
        title = stringResource(R.string.dialog_rename_conflict_title),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmText = stringResource(confirmTextRes(pending.overwrite))
    ) {
        Text(renameConflictMessage(pending))
    }
}

/**
 * 덮어쓰기 결과에 맞는 확인 버튼 문구를 고른다.
 * @param overwrite 확인하면 저장소에서 일어나는 일
 * @return 문자열 리소스 ID — 폴더 합치기면 합치기, 그 밖에는 덮어쓰기
 */
@StringRes
private fun confirmTextRes(overwrite: RenameOverwrite): Int = when (overwrite) {
    RenameOverwrite.MERGE_FOLDER -> R.string.btn_merge
    RenameOverwrite.REPLACE_FILE, RenameOverwrite.STORAGE_DEFINED -> R.string.btn_overwrite
}

/**
 * 덮어쓰기 결과에 맞는 안내 문구를 만든다.
 * @param pending 확인을 기다리는 이름 변경
 * @return 다이얼로그 본문 문구
 */
@Composable
private fun renameConflictMessage(pending: PendingRename): String = when (pending.overwrite) {
    RenameOverwrite.STORAGE_DEFINED -> stringResource(R.string.dialog_rename_conflict_remote, pending.newName)
    RenameOverwrite.MERGE_FOLDER -> stringResource(R.string.dialog_rename_conflict_folder, pending.newName)
    RenameOverwrite.REPLACE_FILE -> stringResource(R.string.dialog_rename_conflict_file, pending.newName)
}

/** 로컬 폴더 합치기 확인 모양을 확인하는 프리뷰. */
@Preview
@Composable
private fun RenameConflictDialogPreview() {
    RenameConflictDialog(
        pending = PendingRename(
            target = FileHolderItem(path = "/a", isDirectory = true, name = "a", size = "", lastModified = "", sizeBytes = null, lastModifiedMillis = 0L),
            newName = "b",
            overwrite = RenameOverwrite.MERGE_FOLDER
        ),
        onConfirm = {},
        onDismiss = {}
    )
}
