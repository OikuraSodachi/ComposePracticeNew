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
import com.todokanai.composepracticenew.ui.model.ByteAmountModel
import com.todokanai.composepracticenew.ui.model.CurrentFileModel
import com.todokanai.composepracticenew.ui.model.IndeterminatePhaseModel
import com.todokanai.composepracticenew.ui.model.KnownSizeProgressModel
import com.todokanai.composepracticenew.ui.model.ProgressPhaseModel
import com.todokanai.composepracticenew.ui.model.ProgressStateModel
import com.todokanai.composepracticenew.ui.model.asString
import com.todokanai.composepracticenew.ui.model.knownPartTextOf
import com.todokanai.composepracticenew.ui.model.operationLabelRes
import com.todokanai.composepracticenew.ui.model.percent
import com.todokanai.composepracticenew.ui.model.phaseTextOf
import com.todokanai.composepracticenew.ui.model.sizeText
import com.todokanai.composepracticenew.ui.model.unknownFilesTextOf

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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(operationLabelRes(state.operationType)),
            style = MaterialTheme.typography.labelMedium
        )
        when (val phase = state.phase) {
            ProgressPhaseModel.Starting -> PercentPart(percent = null, sizeText = null)
            is IndeterminatePhaseModel -> IndeterminatePart(phase)
            is ProgressPhaseModel.Counted -> {
                PercentPart(percent = phase.percent, sizeText = null)
                CurrentNamePart(phase.currentName, size = null)
                CountPart(phase.processed, phase.total)
            }
            is ProgressPhaseModel.Bytes -> {
                PercentPart(percent = phase.percent(), sizeText = phase.sizeText())
                when (val amount = phase.amount) {
                    is ByteAmountModel.Known -> Unit
                    is ByteAmountModel.PartlyUnknown -> KnownSizePart(amount.knownSize, state.operationType)
                }
                phase.currentFile?.let { CurrentNamePart(it.name, it.size) }
                CountPart(phase.fileIndex, phase.fileCount)
            }
        }
    }
}

/**
 * 작업 전체의 진행 막대와 퍼센트·처리한 크기 줄을 보여준다.
 * @param percent 진행도(0~100 퍼센트). 모르면 null — 0%로 멈춘 막대 대신 계속 움직이는 막대를 보인다
 * @param sizeText 처리한 크기의 표시 문구. 없으면 null
 */
@Composable
private fun PercentPart(percent: Int?, sizeText: String?) {
    if (percent != null) {
        ProgressLinearIndicator(
            modifier = Modifier.fillMaxWidth().height(8.dp),
            progress = percent / 100f
        )
    } else {
        // 진행률을 모르면 0%로 멈춘 막대 대신 계속 움직이는 막대로 작업이 진행 중임을 보여준다
        IndeterminateIndicator()
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = progressText(percent), style = MaterialTheme.typography.bodySmall)
        sizeText?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
    }
}

/**
 * 지금 처리하는 항목의 이름과 크기를 보여준다.
 * @param name 항목 이름
 * @param size 항목 크기의 표시 문구. 모르면 null
 */
@Composable
private fun CurrentNamePart(name: String, size: String?) {
    Text(
        text = name,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    if (size != null) {
        Text(
            text = size,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 처리한 항목 수의 막대와 "처리 / 전체" 줄을 보여준다.
 * @param index 처리한(처리 중인) 항목 순번
 * @param total 전체 항목 수
 */
@Composable
private fun CountPart(index: Int, total: Int) {
    ProgressLinearIndicator(
        modifier = Modifier.fillMaxWidth().height(6.dp),
        progress = index.toFloat() / total.coerceAtLeast(1),
        progressColor = MaterialTheme.colorScheme.secondary,
        backgroundColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.24f)
    )
    Text(
        text = stringResource(R.string.progress_file_count, index, total),
        style = MaterialTheme.typography.bodySmall
    )
}

/**
 * 퍼센트 없이 보이는 단계의 진행을 보여준다. 전체를 모르거나 셀 수 없으므로 퍼센트 대신 움직이는 막대와 단계의 문구, 대상 이름을 보여준다.
 * @param phase 퍼센트 없이 보이는 단계의 진행
 */
@Composable
private fun IndeterminatePart(phase: IndeterminatePhaseModel) {
    IndeterminateIndicator()
    Text(
        text = phaseTextOf(phase).asString(),
        style = MaterialTheme.typography.bodySmall
    )
    Text(
        text = phaseTargetName(phase),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * 퍼센트 없이 보이는 단계에서 지금 다루는 대상의 이름을 고른다.
 * @param phase 단계
 * @return 준비 단계면 마지막으로 찾은 항목, 비밀번호 확인이면 압축 파일의 이름
 */
private fun phaseTargetName(phase: IndeterminatePhaseModel): String = when (phase) {
    is IndeterminatePhaseModel.Preparing -> phase.currentName
    is IndeterminatePhaseModel.CheckingPassword -> phase.archiveName
}

/** 진행률을 모를 때 작업이 진행 중임을 보여주는 계속 움직이는 막대. */
@Composable
private fun IndeterminateIndicator() {
    LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(16.dp)),
        color = Color.Red,
        trackColor = Color.Red.copy(0.24f)
    )
}

/**
 * 크기를 모르는 파일이 섞인 작업에서 크기를 아는 파일들의 진행과, 크기를 모르는 파일들에서 처리한 크기를 나눠 보여준다.
 * 크기를 모르는 파일을 처리하는 동안에는 아는 부분의 막대는 서 있고 모르는 파일의 처리한 크기가 늘어난다.
 * @param knownSize 크기를 아는 부분과 모르는 부분의 진행
 * @param operationType 작업 종류 — 크기를 모르는 파일의 처리 문구(받음·보냄·처리)를 고른다
 */
@Composable
private fun KnownSizePart(knownSize: KnownSizeProgressModel, operationType: FileOperationType) {
    knownSize.progress?.let { progress ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = knownPartTextOf(progress).asString(), style = MaterialTheme.typography.bodySmall)
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
        text = unknownFilesTextOf(knownSize, operationType).asString(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 진행률의 표시 문구. 모르면 퍼센트 대신 알 수 없음을 보여준다. */
@Composable
private fun progressText(progress: Int?): String =
    progress?.let { "$it%" } ?: stringResource(R.string.progress_unknown)

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
            progress = ProgressStateModel(
                operationType = FileOperationType.COPY,
                phase = ProgressPhaseModel.Bytes(
                    amount = ByteAmountModel.Known(percent = 42, totalSize = "10 MB"),
                    writtenSize = "4.2 MB",
                    fileIndex = 1,
                    fileCount = 3,
                    currentFile = CurrentFileModel(name = "photo.jpg", size = "5 MB")
                )
            )
        )
    }
}

@Preview
@Composable
private fun CustomProgressDialogPreparingPreview() {
    Surface {
        CustomProgressDialog(
            progress = ProgressStateModel(
                operationType = FileOperationType.DOWNLOAD,
                phase = IndeterminatePhaseModel.Preparing(foundEntries = 1234, currentName = "photos")
            )
        )
    }
}

/** CustomProgressDialog의 비밀번호 확인 단계 모양을 확인하는 프리뷰. */
@Preview
@Composable
private fun CustomProgressDialogCheckingPasswordPreview() {
    Surface {
        CustomProgressDialog(
            progress = ProgressStateModel(
                operationType = FileOperationType.UNZIP,
                phase = IndeterminatePhaseModel.CheckingPassword(archiveName = "photos.rar")
            )
        )
    }
}

@Preview
@Composable
private fun CustomProgressDialogKnownSizePreview() {
    Surface {
        CustomProgressDialog(
            progress = ProgressStateModel(
                operationType = FileOperationType.DOWNLOAD,
                phase = ProgressPhaseModel.Bytes(
                    amount = ByteAmountModel.PartlyUnknown(
                        KnownSizeProgressModel(
                            progress = 100,
                            writtenSize = "4 KB",
                            totalSize = "4 KB",
                            unknownFileCount = 1,
                            unknownWrittenSize = "300 MB"
                        )
                    ),
                    writtenSize = "300 MB",
                    fileIndex = 2,
                    fileCount = 2,
                    currentFile = CurrentFileModel(name = "unknown.bin", size = null)
                )
            )
        )
    }
}
