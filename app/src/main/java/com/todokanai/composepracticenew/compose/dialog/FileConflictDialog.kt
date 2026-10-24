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

/**
 * 동일한 이름의 파일이 목적지에 이미 존재하거나, 목적지의 기존 파일을 확인하지 못했을 때 처리 방법을 사용자에게 묻는 다이얼로그.
 * 기존 파일을 확인하지 못했으면 덮어써질 수 있음을 알리고 계속할지만 묻는다.
 */
@Composable
fun FileConflictDialog(
    conflictingFiles: List<FileHolderItem>,
    totalCount: Int,
    destinationUnconfirmed: Boolean,
    onOverwrite: () -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit
) {
    if (destinationUnconfirmed) {
        UnconfirmedDestinationDialog(onContinue = onOverwrite, onCancel = onCancel)
        return
    }
    AppAlertDialog(
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
            Button(onClick = onOverwrite) {
                Text(stringResource(R.string.btn_overwrite))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.btn_cancel))
                }
                if (conflictingFiles.size < totalCount) {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.btn_skip_conflicts))
                    }
                }
            }
        }
    )
}

/** 목적지의 기존 파일을 확인하지 못해 같은 이름의 파일이 덮어써질 수 있음을 알리고 계속할지 묻는 다이얼로그. */
@Composable
private fun UnconfirmedDestinationDialog(onContinue: () -> Unit, onCancel: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.dialog_unconfirmed_destination_title)) },
        text = { Text(stringResource(R.string.dialog_unconfirmed_destination_message)) },
        confirmButton = {
            Button(onClick = onContinue) {
                Text(stringResource(R.string.btn_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
