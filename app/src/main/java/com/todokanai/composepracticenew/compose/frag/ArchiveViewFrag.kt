package com.todokanai.composepracticenew.compose.frag

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.dialog.ArchivePasswordDialog
import com.todokanai.composepracticenew.compose.holder.ArchiveEntryHolder
import com.todokanai.composepracticenew.compose.listview.SelectAllBar
import com.todokanai.composepracticenew.compose.listview.dragSelectGesture
import com.todokanai.composepracticenew.compose.listview.rememberTypeIcons
import com.todokanai.composepracticenew.model.ArchiveEntryKey
import com.todokanai.composepracticenew.ui.model.ArchiveEntryRow
import com.todokanai.composepracticenew.ui.model.ArchiveEntryRowKind
import com.todokanai.composepracticenew.ui.model.DragSelection
import com.todokanai.composepracticenew.ui.model.asString
import com.todokanai.composepracticenew.viewmodel.ArchiveViewContent
import com.todokanai.composepracticenew.viewmodel.ArchiveViewSelection
import com.todokanai.composepracticenew.viewmodel.ArchiveViewViewModel

/** 경로 표시줄에서 폴더 이름 사이에 넣는 구분 표시. 표시 전용이며 경로를 만들지 않는다. */
private const val FOLDER_SEPARATOR = " › "

/**
 * 내용 보기 목록 행의 LazyColumn 키. 드래그 선택(dragSelectGesture)이 손가락 아래 행을 이 키로 찾으므로 목록과 제스처가 함께 쓴다.
 * 한 폴더의 행만 구분하면 된다 — 파일은 압축 파일 안의 순번(Int), 폴더는 이름(String, 같은 이름의 폴더는 합쳐 있다)으로, 타입이 달라 서로 겹치지 않으며 Bundle에 담긴다.
 * @param row 행
 * @return 지금 폴더 안에서 행마다 하나인 키
 */
internal fun archiveListKeyOf(row: ArchiveEntryRow): Any = when (val key = row.key) {
    is ArchiveEntryKey.Item -> key.index
    is ArchiveEntryKey.Folder -> key.name
}

/**
 * 압축 파일 내용 보기 화면. 위에 압축 파일 이름과 지금 폴더를, 아래에 지금 폴더의 항목을 보인다. 압축 파일을 바꾸지 않는다.
 * 길게 눌러 끌면 다중 선택으로 들어가며, 다중 선택 중에는 위쪽 이름 자리에 모두 선택 바를 겹쳐 그린다. 항목 목록까지 암호화한 압축 파일이면 비밀번호를 묻는다.
 * @param modifier 화면의 배치를 정하는 Modifier
 * @param viewModel 목록 읽기·폴더 탐색·선택·비밀번호 질문을 맡는 ViewModel
 */
@Composable
fun ArchiveViewFrag(modifier: Modifier, viewModel: ArchiveViewViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val passwordRequest by viewModel.passwordRequest.collectAsStateWithLifecycle()
    // 지금 폴더(이름 목록)를 키로 — 다른 폴더로 가면 맨 위부터 새로 시작한다(파일 목록의 rememberListingScrollState와 같은 규칙).
    // rememberSaveable이라 화면 회전 뒤에도 같은 폴더의 위치가 복원된다
    val listState = rememberSaveable(uiState.folderNames, saver = LazyListState.Saver) { LazyListState() }
    /** 부를 때의 선택 목록을 읽는 함수. uiState State를 읽어, 모두 선택 바의 derivedStateOf가 선택 변경을 추적한다. */
    val selectedRows: () -> List<ArchiveEntryRow> = { selectedRowsOf(uiState.selection) }
    val rows = (uiState.content as? ArchiveViewContent.Loaded)?.rows.orEmpty()
    val isMultiSelect = uiState.selection is ArchiveViewSelection.MultiSelect

    Column(modifier = modifier.fillMaxSize()) {
        // 다중 선택 중에는 이름 자리 위에 모두 선택 바를 같은 크기로 겹친다 — 목록 위에 새 줄을 더하면 목록이 밀려 진행 중인 드래그 선택이 어긋난다(#520)
        Box(modifier = Modifier.fillMaxWidth()) {
            ArchiveHeader(
                modifier = if (isMultiSelect) Modifier.clearAndSetSemantics {} else Modifier,
                archiveName = uiState.archiveName,
                folderNames = uiState.folderNames
            )
            if (isMultiSelect) {
                SelectAllBar(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.background),
                    items = rows,
                    keyOf = ::archiveListKeyOf,
                    selectedList = selectedRows,
                    onReplaceSelection = viewModel::replaceSelection
                )
            }
        }
        HorizontalDivider()
        ArchiveContent(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            content = uiState.content,
            listState = listState,
            isMultiSelect = isMultiSelect,
            selectedRows = selectedRows,
            onRetry = viewModel::retry,
            onRowClick = viewModel::onRowClick,
            startSelection = { anchor -> startDragSelection(uiState.selection, anchor, viewModel::startMultiSelect) },
            onReplaceSelection = viewModel::replaceSelection
        )
    }

    passwordRequest?.let { request ->
        // 질문 id를 키로 — 다시 물을 때 앞서 입력한 비밀번호가 새 질문에 남지 않도록 다이얼로그 상태를 새로 만든다
        key(request.id) {
            ArchivePasswordDialog(
                archiveName = request.archiveName,
                prompt = request.prompt,
                onConfirm = { password -> viewModel.answerPassword(request.id, password) },
                onCancel = { viewModel.cancelPassword(request.id) }
            )
        }
    }
}

/**
 * 압축 파일 이름과 지금 폴더.
 * @param modifier 배치를 정하는 Modifier
 * @param archiveName 압축 파일 이름
 * @param folderNames 루트부터 들어간 폴더 이름들
 */
@Composable
private fun ArchiveHeader(modifier: Modifier, archiveName: String, folderNames: List<String>) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = archiveName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp)
        )
        Text(
            text = folderNames.joinToString(separator = FOLDER_SEPARATOR, prefix = FOLDER_SEPARATOR),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

/**
 * 목록 영역. 읽는 중 표시, 읽기 실패 안내와 다시 시도 버튼, 빈 폴더 안내, 또는 항목 목록을 보인다.
 * @param modifier 목록 영역의 크기·배치를 정하는 Modifier
 * @param content 목록 영역의 상태
 * @param listState 항목 목록의 스크롤 상태
 * @param isMultiSelect 다중 선택 중이면 true
 * @param selectedRows 선택 목록을 반환하는 함수
 * @param onRetry 다시 시도 버튼이 부르는 콜백
 * @param onRowClick 행을 눌렀을 때 부르는 콜백
 * @param startSelection 길게 누른 행으로 드래그 선택을 시작하는 함수
 * @param onReplaceSelection 드래그 범위를 반영한 선택 목록을 전달하는 콜백
 */
@Composable
private fun ArchiveContent(
    modifier: Modifier,
    content: ArchiveViewContent,
    listState: LazyListState,
    isMultiSelect: Boolean,
    selectedRows: () -> List<ArchiveEntryRow>,
    onRetry: () -> Unit,
    onRowClick: (ArchiveEntryRow) -> Unit,
    startSelection: (ArchiveEntryRow) -> DragSelection<ArchiveEntryRow>?,
    onReplaceSelection: (List<ArchiveEntryRow>) -> Unit
) {
    when (content) {
        ArchiveViewContent.Loading -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is ArchiveViewContent.Failed -> Column(
            modifier = modifier
                .wrapContentSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = stringResource(R.string.archive_view_failed), textAlign = TextAlign.Center)
            content.detail?.let {
                Text(text = it.asString(), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            Button(onClick = onRetry) {
                Text(text = stringResource(R.string.btn_retry))
            }
        }
        is ArchiveViewContent.Loaded ->
            if (content.rows.isEmpty()) {
                Text(text = stringResource(R.string.archive_view_empty), modifier = modifier.wrapContentSize())
            } else {
                ArchiveEntryList(
                    modifier = modifier,
                    rows = content.rows,
                    listState = listState,
                    isMultiSelect = isMultiSelect,
                    selectedRows = selectedRows,
                    onRowClick = onRowClick,
                    startSelection = startSelection,
                    onReplaceSelection = onReplaceSelection
                )
            }
    }
}

/**
 * 지금 폴더의 항목 목록. 길게 누르기는 행이 아니라 목록(dragSelectGesture)만 받는다.
 * @param modifier 목록의 크기·배치를 정하는 Modifier
 * @param rows 표시할 행들(정렬됨)
 * @param listState 목록의 스크롤 상태. 폴더마다 새로 만든 것을 받는다
 * @param isMultiSelect 다중 선택 중이면 true — 모든 행을 누를 수 있다
 * @param selectedRows 선택 목록을 반환하는 함수
 * @param onRowClick 행을 눌렀을 때 부르는 콜백
 * @param startSelection 길게 누른 행으로 드래그 선택을 시작하는 함수
 * @param onReplaceSelection 드래그 범위를 반영한 선택 목록을 전달하는 콜백
 */
@Composable
private fun ArchiveEntryList(
    modifier: Modifier,
    rows: List<ArchiveEntryRow>,
    listState: LazyListState,
    isMultiSelect: Boolean,
    selectedRows: () -> List<ArchiveEntryRow>,
    onRowClick: (ArchiveEntryRow) -> Unit,
    startSelection: (ArchiveEntryRow) -> DragSelection<ArchiveEntryRow>?,
    onReplaceSelection: (List<ArchiveEntryRow>) -> Unit
) {
    // items{} 밖에서 한 번 만들어 모든 행이 같은 인스턴스를 공유 — 행이 Strong Skipping의 인스턴스 비교로 생략된다
    val typeIcons = rememberTypeIcons()
    val selectedKeys = selectedRows().mapTo(HashSet()) { it.key }
    LazyColumn(
        modifier = modifier.dragSelectGesture(
            listState = listState,
            items = rows,
            keyOf = ::archiveListKeyOf,
            startSelection = startSelection,
            onReplaceSelection = onReplaceSelection
        ),
        state = listState
    ) {
        items(rows, key = ::archiveListKeyOf) { row ->
            ArchiveEntryHolder(
                modifier = Modifier,
                row = row,
                isSelected = row.key in selectedKeys,
                clickEnabled = isMultiSelect || row.kind == ArchiveEntryRowKind.FOLDER,
                typeIcons = typeIcons,
                onClick = { onRowClick(row) }
            )
        }
    }
}

/**
 * 선택 상태에서 선택된 행들을 꺼낸다.
 * @param selection 선택 상태
 * @return 다중 선택이면 선택된 행들, 아니면 빈 목록
 */
private fun selectedRowsOf(selection: ArchiveViewSelection): List<ArchiveEntryRow> = when (selection) {
    ArchiveViewSelection.None -> emptyList()
    is ArchiveViewSelection.MultiSelect -> selection.selected
}

/**
 * 선택 상태에 맞춰 드래그 선택을 시작하고 이번 드래그의 범위 선택 규칙을 정한다.
 * 기본 모드에서 시작하면 다중 선택으로 들어가 범위를 선택하고, 다중 선택 중에 시작하면 시작 행의 선택 여부에 따라 범위를 선택하거나 해제한다.
 * @param selection 드래그를 시작할 때의 선택 상태
 * @param anchor 드래그를 시작한 행
 * @param onStartMultiSelect anchor를 선택한 채로 다중 선택으로 들어가는 콜백 — 기본 모드에서만 부른다
 * @return 범위 선택 규칙
 */
private fun startDragSelection(
    selection: ArchiveViewSelection,
    anchor: ArchiveEntryRow,
    onStartMultiSelect: (ArchiveEntryRow) -> Unit
): DragSelection<ArchiveEntryRow> = when (selection) {
    ArchiveViewSelection.None -> DragSelection.fresh(::archiveListKeyOf).also { onStartMultiSelect(anchor) }
    is ArchiveViewSelection.MultiSelect -> DragSelection.startingAt(anchor, selection.selected, ::archiveListKeyOf)
}
