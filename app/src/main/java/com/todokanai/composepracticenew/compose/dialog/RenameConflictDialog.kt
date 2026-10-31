package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.presets.dialog.ConfirmCancelDialog
import com.todokanai.composepracticenew.model.RenameConflict
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.PendingRename

/**
 * 이름 변경 시 같은 이름의 항목이 있을 때 덮어쓸지(폴더는 합칠지) 묻는 다이얼로그.
 * @param pending 확인을 기다리는 이름 변경
 * @param isRemote 원격 저장소이면 true — 결과가 서버 동작에 따라 다르다는 안내를 보여준다
 * @param onConfirm 덮어쓰기(합치기)를 확인했을 때의 콜백
 * @param onDismiss 취소·닫기 콜백
 */
@Composable
fun RenameConflictDialog(
    pending: PendingRename,
    isRemote: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val mergesFolder = !isRemote && pending.conflict == RenameConflict.FOLDER
    ConfirmCancelDialog(
        modifier = Modifier,
        title = stringResource(R.string.dialog_rename_conflict_title),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmText = stringResource(if (mergesFolder) R.string.btn_merge else R.string.btn_overwrite)
    ) {
        Text(renameConflictMessage(pending, isRemote))
    }
}

/**
 * 겹치는 방식과 저장소 종류에 맞는 안내 문구를 만든다.
 * @param pending 확인을 기다리는 이름 변경
 * @param isRemote 원격 저장소이면 true
 * @return 다이얼로그 본문 문구
 */
@Composable
private fun renameConflictMessage(pending: PendingRename, isRemote: Boolean): String = when {
    isRemote -> stringResource(R.string.dialog_rename_conflict_remote, pending.newName)
    pending.conflict == RenameConflict.FOLDER -> stringResource(R.string.dialog_rename_conflict_folder, pending.newName)
    else -> stringResource(R.string.dialog_rename_conflict_file, pending.newName)
}

/** 로컬 폴더 합치기 확인 모양을 확인하는 프리뷰. */
@Preview
@Composable
private fun RenameConflictDialogPreview() {
    RenameConflictDialog(
        pending = PendingRename(
            target = FileHolderItem(path = "/a", isDirectory = true, name = "a", size = "", lastModified = "", sizeBytes = null, lastModifiedMillis = 0L),
            newName = "b",
            conflict = RenameConflict.FOLDER
        ),
        isRemote = false,
        onConfirm = {},
        onDismiss = {}
    )
}
