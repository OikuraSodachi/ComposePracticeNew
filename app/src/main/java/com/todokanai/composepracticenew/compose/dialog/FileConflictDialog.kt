package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.ui.model.FileHolderItem

/** 동일한 이름의 파일이 목적지에 이미 존재할 때 처리 방법을 사용자에게 묻는 다이얼로그. */
@Composable
fun FileConflictDialog(
    conflictingFiles: List<FileHolderItem>,
    totalCount: Int,
    onOverwrite: () -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.dialog_file_conflict_title)) },
        text = {
            Column {
                Text(stringResource(R.string.dialog_file_conflict_message))
                Spacer(Modifier.height(8.dp))
                conflictingFiles.forEach { Text("• ${it.name}") }
            }
        },
        confirmButton = {
            Button(onClick = { onOverwrite(); onCancel() }) {
                Text(stringResource(R.string.btn_overwrite))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.btn_cancel))
                }
                if (conflictingFiles.size < totalCount) {
                    TextButton(onClick = { onSkip(); onCancel() }) {
                        Text(stringResource(R.string.btn_skip_conflicts))
                    }
                }
            }
        }
    )
}
