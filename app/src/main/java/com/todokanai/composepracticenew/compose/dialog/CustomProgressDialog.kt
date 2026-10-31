package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.KnownSizeProgressModel
import com.todokanai.composepracticenew.model.ProgressStateModel

/**
 * 파일 작업 하나의 진행률을 표시하는 다이얼로그.
 *
 * @param progress 표시할 작업의 진행 상태.
 * @param onDismissRequest 다이얼로그 바깥 탭·뒤로가기·닫기 버튼의 콜백. 작업은 계속 진행된다.
 * @param onCancel 이 작업을 취소하는 버튼의 콜백.
 */
@Composable
fun CustomProgressDialog(
    progress: ProgressStateModel,
    onDismissRequest: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.progress_dialog_title)) },
        text = { ProgressItem(progress) },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.btn_dismiss))
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
    val progress = state.progress
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = operationLabel(state.operationType),
            style = MaterialTheme.typography.labelMedium
        )
        if (progress != null) {
            ProgressLinearIndicator(
                modifier = Modifier.fillMaxWidth().height(8.dp),
                progress = progress / 100f
            )
        } else {
            // 진행률을 모르면 0%로 멈춘 막대 대신 계속 움직이는 막대로 작업이 진행 중임을 보여준다
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(16.dp)),
                color = Color.Red,
                trackColor = Color.Red.copy(0.24f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = progressText(progress), style = MaterialTheme.typography.bodySmall)
            sizeText(state)?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
        }
        state.knownSize?.let { KnownSizePart(it) }
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

/**
 * 크기를 모르는 파일이 섞인 작업에서 크기를 아는 파일들의 진행과, 크기를 모르는 파일들에서 받은 크기를 나눠 보여준다.
 * 크기를 모르는 파일을 처리하는 동안에는 아는 부분의 막대는 서 있고 모르는 파일의 받은 크기가 늘어난다.
 */
@Composable
private fun KnownSizePart(knownSize: KnownSizeProgressModel) {
    knownSize.progress?.let { progress ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = stringResource(R.string.progress_known_part, progress), style = MaterialTheme.typography.bodySmall)
            Text(text = "${knownSize.writtenSize} / ${knownSize.totalSize}", style = MaterialTheme.typography.bodySmall)
        }
        ProgressLinearIndicator(
            modifier = Modifier.fillMaxWidth().height(6.dp),
            progress = progress / 100f,
            progressColor = MaterialTheme.colorScheme.tertiary,
            backgroundColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.24f)
        )
    }
    Text(
        text = stringResource(R.string.progress_unknown_files, knownSize.unknownFileCount, knownSize.unknownWrittenSize),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 진행률의 표시 문구. 모르면 퍼센트 대신 알 수 없음을 보여준다. */
@Composable
private fun progressText(progress: Int?): String =
    progress?.let { "$it%" } ?: stringResource(R.string.progress_unknown)

/** 처리한 크기의 표시 문구. 전체 크기를 알면 "처리 / 전체", 모르면 처리한 크기만, 처리한 크기도 없으면 null이다. */
private fun sizeText(state: ProgressStateModel): String? {
    val current = state.currentSize ?: return null
    return state.totalSize?.let { "$current / $it" } ?: current
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
    // 진행 막대를 draw 단계에서 그린다 — 진행률이 바뀌어도 measure/layout 없이 다시 그리기만 한다
    Box(
        modifier = modifier
            .clip(clipShape)
            .background(backgroundColor)
            .drawBehind {
                val filledWidth = size.width * progress.coerceIn(0f, 1f)
                // 레이아웃 방향의 시작점부터 채운다 — RTL에서는 오른쪽에서 왼쪽으로
                val startX = if (layoutDirection == LayoutDirection.Rtl) size.width - filledWidth else 0f
                drawRect(
                    color = progressColor,
                    topLeft = Offset(x = startX, y = 0f),
                    size = Size(width = filledWidth, height = size.height)
                )
            }
    )
}

@Preview
@Composable
private fun CustomProgressDialogPreview() {
    Surface {
        CustomProgressDialog(
            progress = ProgressStateModel(progress = 42, operationType = FileOperationType.COPY)
        )
    }
}

@Preview
@Composable
private fun CustomProgressDialogKnownSizePreview() {
    Surface {
        CustomProgressDialog(
            progress = ProgressStateModel(
                currentSize = "300 MB",
                listSize = 2,
                currentIndex = 2,
                currentFileName = "unknown.bin",
                operationType = FileOperationType.DOWNLOAD,
                knownSize = KnownSizeProgressModel(
                    progress = 100,
                    writtenSize = "4 KB",
                    totalSize = "4 KB",
                    unknownFileCount = 1,
                    unknownWrittenSize = "300 MB"
                )
            )
        )
    }
}
