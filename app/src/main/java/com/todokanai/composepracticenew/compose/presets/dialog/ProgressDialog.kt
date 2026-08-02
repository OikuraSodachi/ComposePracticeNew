package com.todokanai.composepracticenew.compose.presets.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.todokanai.composepracticenew.R
import androidx.compose.ui.unit.dp
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_COPY
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_DELETE
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_MOVE
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_UNZIP
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_ZIP

/** 하나 이상의 파일 작업 진행률을 표시하는 다이얼로그. progressMap이 비어 있으면 호출하지 않을 것. */
@Composable
fun ProgressDialog(
    progressMap: Map<Int, ProgressState>,
    onDismissRequest: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.progress_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                progressMap.values.forEach { state ->
                    ProgressItem(state)
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun ProgressItem(state: ProgressState) {
    val progress = state.progress ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = actionKeyToLabel(state.actionKey),
            style = MaterialTheme.typography.labelMedium
        )
        CustomLinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            progress = progress / 100f
        )
        Text(
            text = "$progress%",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun actionKeyToLabel(actionKey: Int?) = when (actionKey) {
    ACTION_KEY_COPY -> stringResource(R.string.progress_copying)
    ACTION_KEY_MOVE -> stringResource(R.string.progress_moving)
    ACTION_KEY_DELETE -> stringResource(R.string.progress_deleting)
    ACTION_KEY_ZIP -> stringResource(R.string.progress_zipping)
    ACTION_KEY_UNZIP -> stringResource(R.string.progress_unzipping)
    else -> stringResource(R.string.progress_processing)
}

/** 둥근 모서리의 커스텀 LinearProgressIndicator. */
@Composable
fun CustomLinearProgressIndicator(
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
private fun ProgressDialogPreview() {
    Surface {
        ProgressDialog(
            progressMap = mapOf(
                ACTION_KEY_COPY to ProgressState(progress = 42, actionKey = ACTION_KEY_COPY),
                ACTION_KEY_ZIP to ProgressState(progress = 75, actionKey = ACTION_KEY_ZIP)
            )
        )
    }
}

@Preview
@Composable
private fun CustomLinearProgressIndicatorPreview() {
    Surface {
        CustomLinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
            progress = 0.7f
        )
    }
}
