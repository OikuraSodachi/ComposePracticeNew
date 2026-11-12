package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.presets.dialog.AppAlertDialog
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.NameCollisionGroup

/**
 * 목적지에 같은 이름의 항목이 이미 있거나, 함께 보내는 항목끼리 목적지에서 같은 이름이 될 수 있을 때 처리 방법을 사용자에게 묻는 다이얼로그.
 * 건너뛰기는 목적지에 이미 있는 이름의 항목만 뺀다. 함께 보내는 항목끼리의 겹침은 알리기만 하므로, 그것뿐이면 계속·취소만 고른다.
 */
@Composable
fun FileConflictDialog(
    conflictingFiles: List<FileHolderItem>,
    collisions: List<NameCollisionGroup>,
    totalCount: Int,
    onOverwrite: () -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit
) {
    AppAlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.dialog_file_conflict_title)) },
        text = {
            Column {
                if (conflictingFiles.isNotEmpty()) {
                    Text(stringResource(R.string.dialog_file_conflict_message))
                    Spacer(Modifier.height(8.dp))
                    conflictingFiles.forEach { Text("• ${it.name}") }
                }
                if (collisions.isNotEmpty()) {
                    if (conflictingFiles.isNotEmpty()) Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.dialog_name_collision_message))
                    Spacer(Modifier.height(8.dp))
                    collisions.forEach { Text("• ${collisionLine(it)}") }
                }
            }
        },
        confirmButton = {
            Button(onClick = onOverwrite) {
                Text(stringResource(if (conflictingFiles.isNotEmpty()) R.string.btn_overwrite else R.string.btn_continue))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.btn_cancel))
                }
                if (conflictingFiles.isNotEmpty() && conflictingFiles.size < totalCount) {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.btn_skip_conflicts))
                    }
                }
            }
        }
    )
}

/** 겹칠 수 있는 이름 묶음 한 줄. 폴더 안쪽이면 폴더의 표시용 경로를 앞에 붙인다. */
@Composable
private fun collisionLine(collision: NameCollisionGroup): String {
    val names = collision.names.joinToString(", ")
    return collision.folderLabel?.let { stringResource(R.string.dialog_name_collision_in_folder, it, names) } ?: names
}
