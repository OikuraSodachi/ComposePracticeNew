package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.ProgressStateModel

/**
 * 하나 이상의 파일 작업 진행률을 표시하는 다이얼로그. progressMap이 비어 있으면 호출하지 않을 것.
 *
 * @param onDismissRequest 다이얼로그 바깥 탭·뒤로가기·닫기 버튼의 콜백. 작업은 계속 진행된다.
 * @param onCancel 진행 중인 파일 작업을 취소하는 버튼의 콜백.
 */
@Composable
fun CustomProgressDialog(
    progressMap: Map<Int, ProgressStateModel>,
    onDismissRequest: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.progress_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                progressMap.values.forEach { state ->
                    ProgressItem(state)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.btn_close))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@Composable
private fun ProgressItem(state: ProgressStateModel) {
    val progress = state.progress ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = operationLabel(state.operationType),
            style = MaterialTheme.typography.labelMedium
        )
        ProgressLinearIndicator(
            modifier = Modifier.fillMaxWidth().height(8.dp),
            progress = progress / 100f
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "$progress%", style = MaterialTheme.typography.bodySmall)
            if (state.currentSize != null && state.totalSize != null) {
                Text(
                    text = "${state.currentSize} / ${state.totalSize}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (state.currentFileName != null) {
            Text(
                text = state.currentFileName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (state.currentFileSize != null) {
                Text(
                    text = state.currentFileSize,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (state.listSize != null && state.currentIndex != null) {
            ProgressLinearIndicator(
                modifier = Modifier.fillMaxWidth().height(6.dp),
                progress = state.currentIndex.toFloat() / state.listSize.coerceAtLeast(1),
                progressColor = MaterialTheme.colorScheme.secondary,
                backgroundColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.24f)
            )
            Text(
                text = "${state.currentIndex} / ${state.listSize} 파일",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun operationLabel(type: FileOperationType?) = when (type) {
    FileOperationType.COPY -> stringResource(R.string.progress_copying)
    FileOperationType.MOVE -> stringResource(R.string.progress_moving)
    FileOperationType.DELETE -> stringResource(R.string.progress_deleting)
    FileOperationType.ZIP -> stringResource(R.string.progress_zipping)
    FileOperationType.UNZIP -> stringResource(R.string.progress_unzipping)
    FileOperationType.DOWNLOAD -> stringResource(R.string.progress_downloading)
    FileOperationType.UPLOAD -> stringResource(R.string.progress_uploading)
    null -> stringResource(R.string.progress_processing)
}

/** 둥근 모서리의 커스텀 LinearProgressIndicator. */
@Composable
private fun ProgressLinearIndicator(
    modifier: Modifier = Modifier,
    progress: Float,
    progressColor: Color = Color.Red,
    backgroundColor: Color = Color.Red.copy(0.24f),
    clipShape: Shape = RoundedCornerShape(16.dp)
) {
    Box(
        modifier = modifier
            .clip(clipShape)
            .background(backgroundColor)
    ) {
        Box(
            modifier = Modifier
                .background(progressColor)
                .fillMaxHeight()
                .fillMaxWidth(progress)
        )
    }
}

@Preview
@Composable
private fun CustomProgressDialogPreview() {
    Surface {
        CustomProgressDialog(
            progressMap = mapOf(
                1 to ProgressStateModel(progress = 42, operationType = FileOperationType.COPY),
                2 to ProgressStateModel(progress = 75, operationType = FileOperationType.ZIP)
            )
        )
    }
}
