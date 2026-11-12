package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.presets.dialog.AppAlertDialog
import com.todokanai.composepracticenew.model.ConflictAnswer
import com.todokanai.composepracticenew.model.ConflictChoice
import com.todokanai.composepracticenew.model.ConflictQuestion
import com.todokanai.composepracticenew.model.WriteConflictKind
import com.todokanai.composepracticenew.ui.model.operationLabelRes

/**
 * 진행 중인 작업이 쓰려는 자리에 같은 이름의 항목이 있을 때 처리 방법을 묻는 다이얼로그.
 * 파일은 덮어쓰기, 폴더는 합치기를 고를 수 있고, 종류가 엇갈리면 건너뛰기만 고를 수 있다. 바깥을 눌러 닫지 않는다 — 답하지 않고 닫으면 작업이 계속 기다리므로.
 * @param question 묻는 질문
 * @param onAnswer 사용자의 답을 전달하는 콜백
 */
@Composable
fun WriteConflictDialog(
    question: ConflictQuestion,
    onAnswer: (ConflictAnswer) -> Unit,
) {
    val conflict = question.conflict
    // question.id를 키로 — 다음 질문이 오면 체크를 다시 끄고, 화면 회전에도 이 질문의 체크 상태를 유지한다
    var applyToRest by rememberSaveable(question.id) { mutableStateOf(false) }
    val choose = { choice: ConflictChoice -> onAnswer(ConflictAnswer.Choose(choice, applyToRest)) }

    AppAlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.dialog_write_conflict_title, stringResource(operationLabelRes(question.operationType)))) },
        text = {
            Column {
                Text(stringResource(messageRes(conflict.kind), conflict.name, conflict.folderPath))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = applyToRest, onCheckedChange = { applyToRest = it })
                    Text(stringResource(R.string.dialog_write_conflict_apply_to_rest))
                }
            }
        },
        confirmButton = {
            proceedLabelRes(conflict.kind)?.let { label ->
                Button(onClick = { choose(ConflictChoice.PROCEED) }) { Text(stringResource(label)) }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onAnswer(ConflictAnswer.Cancel) }) { Text(stringResource(R.string.btn_cancel)) }
                TextButton(onClick = { choose(ConflictChoice.SKIP) }) { Text(stringResource(R.string.btn_skip_conflicts)) }
            }
        }
    )
}

/** 겹침 종류별 안내 문구. */
private fun messageRes(kind: WriteConflictKind): Int = when (kind) {
    WriteConflictKind.FILE -> R.string.dialog_write_conflict_file
    WriteConflictKind.FOLDER -> R.string.dialog_write_conflict_folder
    WriteConflictKind.TYPE_MISMATCH -> R.string.dialog_write_conflict_type_mismatch
}

/** 겹침 종류별 진행 버튼 문구. 진행할 수 없는 겹침이면 null(버튼 없음). */
private fun proceedLabelRes(kind: WriteConflictKind): Int? = when (kind) {
    WriteConflictKind.FILE -> R.string.btn_overwrite
    WriteConflictKind.FOLDER -> R.string.btn_merge
    WriteConflictKind.TYPE_MISMATCH -> null
}
