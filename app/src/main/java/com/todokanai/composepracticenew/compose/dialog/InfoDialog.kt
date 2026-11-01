package com.todokanai.composepracticenew.compose.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.presets.dialog.ConfirmCancelDialog
import com.todokanai.composepracticenew.compose.presets.text.MarqueeText
import com.todokanai.composepracticenew.ui.model.AttributesInfo
import com.todokanai.composepracticenew.ui.model.ChildCountInfo
import com.todokanai.composepracticenew.ui.model.ItemKind
import com.todokanai.composepracticenew.ui.model.SelectionSummary
import com.todokanai.composepracticenew.ui.model.SizeInfo

/** 정보 다이얼로그에서 속성과 속성 사이의 간격. 한 속성의 이름·값보다 넓게 두어 어느 이름이 어느 값의 것인지 묶여 보이게 한다. */
private val INFO_ROW_SPACING = 12.dp

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

/**
 * 속성 하나를 이름(작고 흐린 글씨) 아래 값(본문 글씨)으로 표시한다. 둘 다 한 줄이며, 잘리면 가로로 흐른다.
 * @param title 속성 이름
 * @param info 속성 값
 */
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
        MarqueeText(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        MarqueeText(
            text = info,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/** 항목 하나의 상세(이름·위치·종류·크기·수정 시각, 폴더면 하위 항목 수)를 나열한다. */
@Composable
private fun SingleInfoChart(
    modifier: Modifier,
    summary: SelectionSummary.Single
){
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(INFO_ROW_SPACING)
    ) {
        InfoColumn(
            modifier = Modifier,
            title = stringResource(R.string.dialog_info_name),
            info = summary.name
        )
        InfoColumn(
            modifier = Modifier,
            title = stringResource(R.string.dialog_info_location),
            info = summary.location ?: stringResource(R.string.dialog_info_location_unknown)
        )
        InfoColumn(
            modifier = Modifier,
            title = stringResource(R.string.dialog_info_type),
            info = kindText(summary.attributes)
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
            info = modifiedText(summary.attributes)
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
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(INFO_ROW_SPACING)
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

/** 속성 표시 상태 중 항목 종류의 표시 문구. */
@Composable
private fun kindText(attributes: AttributesInfo): String = when (attributes) {
    AttributesInfo.Loading -> stringResource(R.string.dialog_info_loading)
    is AttributesInfo.Known -> kindText(attributes.kind)
    is AttributesInfo.Failed -> failedText(attributes.message)
}

/** 항목 종류의 표시 문구. */
@Composable
private fun kindText(kind: ItemKind): String = when (kind) {
    ItemKind.Folder -> stringResource(R.string.dialog_info_type_folder)
    is ItemKind.File -> kind.extension?.let { stringResource(R.string.dialog_info_type_file_extension, it) }
        ?: stringResource(R.string.dialog_info_type_file)
}

/** 속성 표시 상태 중 수정 시각의 표시 문구. */
@Composable
private fun modifiedText(attributes: AttributesInfo): String = when (attributes) {
    AttributesInfo.Loading -> stringResource(R.string.dialog_info_loading)
    is AttributesInfo.Known -> attributes.lastModified
    is AttributesInfo.Failed -> failedText(attributes.message)
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
                attributes = AttributesInfo.Known(kind = ItemKind.File("jpg"), lastModified = "2026. 10. 31. 오후 3:00:00"),
                size = SizeInfo.Exact("2.4 MB"),
                childCount = null
            )
        ) {}
    }
}
