package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.presets.dialog.ConfirmCancelDialog
import com.todokanai.composepracticenew.ui.model.ChildCountInfo
import com.todokanai.composepracticenew.ui.model.ItemKind
import com.todokanai.composepracticenew.ui.model.SelectionSummary
import com.todokanai.composepracticenew.ui.model.SizeInfo


/**
 * 선택한 항목의 정보 다이얼로그. 항목이 하나면 이름·위치·종류·크기·수정 시각(폴더면 하위 항목 수)을, 여럿이면 개수와 전체 크기를 보여준다.
 * 확인할 동작이 없으므로 닫기 버튼만 둔다.
 *
 * 정보 계산은 ViewModel이 [summary]로 해 두고, 이 다이얼로그는 표시만 담당한다.
 */
@Composable
fun InfoDialog(
    summary: SelectionSummary,
    onDismiss: () -> Unit
) {
    ConfirmCancelDialog(
        modifier = Modifier,
        title = null,
        onConfirm = null,
        onDismiss = onDismiss,
        dismissText = stringResource(R.string.btn_dismiss)
    ) {
        when (summary) {
            is SelectionSummary.Single -> SingleInfoChart(modifier = Modifier, summary = summary)
            is SelectionSummary.Multiple -> MultipleInfoChart(modifier = Modifier, summary = summary)
        }
    }
}

@Composable
private fun InfoColumn(
    modifier: Modifier,
    title:String,
    info:String
){
    Column(
        modifier = modifier
            .fillMaxWidth()
    ){
        Text(title)
        Text(info)
    }
}

/** 항목 하나의 상세(이름·위치·종류·크기·수정 시각, 폴더면 하위 항목 수)를 나열한다. */
@Composable
private fun SingleInfoChart(
    modifier: Modifier,
    summary: SelectionSummary.Single
){
    Column(
        modifier = modifier
    ) {
        InfoColumn(
            modifier = Modifier,
            title = stringResource(R.string.dialog_info_name),
            info = summary.name
        )
        InfoColumn(
            modifier = Modifier,
            title = stringResource(R.string.dialog_info_location),
            info = summary.location
        )
        InfoColumn(
            modifier = Modifier,
            title = stringResource(R.string.dialog_info_type),
            info = kindText(summary.kind)
        )
        InfoColumn(
            modifier = Modifier,
            title = stringResource(R.string.dialog_info_size),
            info = sizeText(summary.size)
        )
        summary.childCount?.let { childCount ->
            InfoColumn(
                modifier = Modifier,
                title = stringResource(R.string.dialog_info_contents),
                info = childCountText(childCount)
            )
        }
        InfoColumn(
            modifier = Modifier,
            title = stringResource(R.string.dialog_info_modified),
            info = summary.lastModified
        )
    }
}

/** 여러 항목의 개수와 전체 크기를 나열한다. */
@Composable
private fun MultipleInfoChart(
    modifier: Modifier,
    summary: SelectionSummary.Multiple
){
    Column(
        modifier = modifier
    ) {
        InfoColumn(
            modifier = Modifier,
            title = stringResource(R.string.dialog_info_selected),
            info = stringResource(R.string.dialog_info_selected_count, summary.count)
        )
        InfoColumn(
            modifier = Modifier,
            title = stringResource(R.string.dialog_info_size),
            info = sizeText(summary.size)
        )
    }
}

/** 항목 종류의 표시 문구. */
@Composable
private fun kindText(kind: ItemKind): String = when (kind) {
    ItemKind.Folder -> stringResource(R.string.dialog_info_type_folder)
    is ItemKind.File -> kind.extension?.let { stringResource(R.string.dialog_info_type_file_extension, it) }
        ?: stringResource(R.string.dialog_info_type_file)
}

/** 크기 표시 상태의 표시 문구. 하한은 읽지 못한 항목 수와 함께 보여준다. */
@Composable
private fun sizeText(size: SizeInfo): String = when (size) {
    SizeInfo.Loading -> stringResource(R.string.dialog_info_loading)
    is SizeInfo.Exact -> size.text
    is SizeInfo.AtLeast -> stringResource(R.string.dialog_info_size_at_least, size.text, size.unreadableCount)
    is SizeInfo.Failed -> failedText(size.message)
}

/** 하위 항목 수 표시 상태의 표시 문구. */
@Composable
private fun childCountText(childCount: ChildCountInfo): String = when (childCount) {
    ChildCountInfo.Loading -> stringResource(R.string.dialog_info_loading)
    is ChildCountInfo.Known -> stringResource(R.string.dialog_info_child_count, childCount.count)
    is ChildCountInfo.Failed -> failedText(childCount.message)
}

/** 읽기 실패의 표시 문구. 원인을 알면 함께 보여준다. */
@Composable
private fun failedText(message: String?): String =
    message?.let { stringResource(R.string.dialog_info_failed_reason, it) } ?: stringResource(R.string.dialog_info_failed)

@Preview
@Composable
private fun InfoDialogPreview(){
    Surface() {
        InfoDialog(
            summary = SelectionSummary.Single(
                name = "photo.jpg",
                location = "/storage/emulated/0/DCIM",
                kind = ItemKind.File("jpg"),
                lastModified = "2026. 10. 31. 오후 3:00:00",
                size = SizeInfo.Exact("2.4 MB"),
                childCount = null
            )
        ) {}
    }
}
