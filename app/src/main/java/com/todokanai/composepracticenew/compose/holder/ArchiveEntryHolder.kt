package com.todokanai.composepracticenew.compose.holder

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.ui.model.ArchiveEntryRow
import com.todokanai.composepracticenew.ui.model.ArchiveEntryRowKind
import com.todokanai.composepracticenew.ui.model.FileType

/**
 * 압축 파일 내용 보기의 항목 하나를 표시하는 컴포저블. 파일 목록 행과 같은 틀([SelectableFileRow])을 쓰고, 이름 아래에 수정 시각을, 오른쪽 아래에 크기를 보인다.
 * 링크·위험한 경로·암호화된 파일은 이름 아래에 그 표시를 함께 보인다.
 * 압축 파일 안의 항목은 저장소 경로가 없어 파일 목록 행([FileHolder])의 모델(경로로 썸네일을 불러오는 FileHolderItem)을 쓰지 않는다.
 * @param modifier 행의 배치를 정하는 Modifier
 * @param row 표시할 행
 * @param isSelected 선택된 행이면 true
 * @param clickEnabled 행을 누를 수 있으면 true — 기본 모드에서는 폴더 행만, 다중 선택 중에는 모든 행을 누를 수 있다
 * @param typeIcons 항목 종류별 placeholder 아이콘 — 목록 전체가 공유하는 같은 인스턴스여야 행 리컴포지션이 생략된다
 * @param onClick 행을 눌렀을 때 부르는 콜백
 */
@Composable
fun ArchiveEntryHolder(
    modifier: Modifier,
    row: ArchiveEntryRow,
    isSelected: Boolean,
    clickEnabled: Boolean,
    typeIcons: Map<FileType, Painter>,
    onClick: () -> Unit
) {
    SelectableFileRow(
        modifier = modifier,
        isSelected = isSelected,
        clickEnabled = clickEnabled,
        onClick = onClick,
        amount = row.size
    ) {
        FileIconAndName(name = row.name, icon = typeIcons.getValue(row.fileType), thumbnail = null, isHidden = row.isHidden) {
            FileRowSubtitle(text = subtitleOf(row))
        }
    }
}

/** 이름 아래 줄. 종류 표시가 있으면 수정 시각 앞에 붙인다. */
@Composable
private fun subtitleOf(row: ArchiveEntryRow): String {
    val mark = markRes(row.kind)?.let { stringResource(it) } ?: return row.lastModified
    return if (row.lastModified.isEmpty()) mark else "$mark · ${row.lastModified}"
}

/**
 * 행 종류의 표시 문구를 정한다.
 * @param kind 행 종류
 * @return 표시 문구의 문자열 리소스 id. 표시할 것이 없는 종류면 null
 */
@StringRes
private fun markRes(kind: ArchiveEntryRowKind): Int? = when (kind) {
    ArchiveEntryRowKind.FOLDER, ArchiveEntryRowKind.FILE -> null
    ArchiveEntryRowKind.ENCRYPTED_FILE -> R.string.archive_entry_encrypted
    ArchiveEntryRowKind.LINK -> R.string.archive_entry_link
    ArchiveEntryRowKind.UNSAFE_PATH -> R.string.archive_entry_unsafe_path
}
