package com.todokanai.composepracticenew.compose.frag

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.listview.DirectoryListingContent
import com.todokanai.composepracticenew.compose.listview.FileListView
import com.todokanai.composepracticenew.compose.listview.ListWithBottomButtons
import com.todokanai.composepracticenew.compose.listview.SelectAllBar
import com.todokanai.composepracticenew.compose.listview.fileListKeyOf
import com.todokanai.composepracticenew.compose.listview.rememberListingScrollState
import com.todokanai.composepracticenew.compose.listview.selectAllTargetsOf
import com.todokanai.composepracticenew.ui.model.DirectoryItem
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.ItemClickAction
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.ui.model.allowsPathBarNavigation
import com.todokanai.composepracticenew.ui.model.pickerMode
import com.todokanai.composepracticenew.viewmodel.ArchiveAddPickerViewModel

/**
 * 압축 파일 내용 보기 안에서 압축 파일에 넣을 로컬 항목(파일·폴더, 여러 개)을 고르는 화면. 내용 보기 destination 안의 상태로, 내용 보기 대신 보인다.
 * 위에 제목과 경로 표시줄을, 아래에 지금 폴더의 목록과 취소·추가 버튼을 보인다. 폴더를 누르면 들어가고, 파일을 누르면 고른다.
 * 길게 눌러 끌면 폴더까지 고르며(다중 선택), 다중 선택 중에는 제목 자리에 모두 선택 바를 겹쳐 그리고 폴더를 옮기지 않는다(#519).
 * @param modifier 화면의 배치를 정하는 Modifier
 * @param viewModel 열림·폴더 탐색·선택을 맡는 ViewModel
 * @param canAdd 지금 압축 파일에 항목을 넣을 수 있으면 true(내용 보기가 목록을 다 읽었을 때). false면 추가 버튼을 비활성으로 보인다
 * @param onConfirm 추가 버튼이 부르는 콜백 — 고른 항목을 꺼내 압축 파일에 넣는다
 */
@Composable
fun ArchiveAddPickerFrag(
    modifier: Modifier,
    viewModel: ArchiveAddPickerViewModel,
    canAdd: Boolean,
    onConfirm: () -> Unit,
) {
    val listing by viewModel.listing.collectAsStateWithLifecycle()
    val selection = viewModel.selectionState.collectAsStateWithLifecycle()
    val dirTree by viewModel.dirTreeStateHolder.dirTree.collectAsStateWithLifecycle()
    // selection을 추적하되 모드(기본 / 다중 선택)가 바뀔 때만 이 화면을 리컴포지션 — 선택 목록만 바뀐 경우는 행·버튼만 다시 그린다
    val selectMode by remember(selection) { derivedStateOf { selection.value.pickerMode() } }
    /** 부를 때의 선택 목록을 읽는 함수. selection State를 읽어, 목록 행과 모두 선택 바의 derivedStateOf가 선택 변경을 추적한다. */
    val selectedList: () -> List<FileHolderItem> = { selection.value.selected }
    /** 목록 스크롤 상태. 폴더마다 맨 위부터 새로 시작한다. */
    val listState = rememberListingScrollState(listing)

    Column(modifier = modifier.fillMaxSize()) {
        // 다중 선택 중에는 제목 자리 위에 모두 선택 바를 같은 크기로 겹친다 — 목록 위에 새 줄을 더하면 목록이 밀려 진행 중인 드래그 선택이 어긋난다(#520)
        Box(modifier = Modifier.fillMaxWidth()) {
            PickerHeader(
                modifier = if (selectMode == SelectMode.MultiSelect) Modifier.clearAndSetSemantics {} else Modifier,
                archiveName = viewModel.archiveName,
                dirTree = dirTree,
                // 다중 선택 중에는 눌러도 이동하지 않는다 — 다중 선택은 지금 폴더 안에서만 한다(#519)
                onDirClick = { entry -> if (selectMode.allowsPathBarNavigation) viewModel.dirTreeStateHolder.onDirClick(entry) }
            )
            if (selectMode == SelectMode.MultiSelect) {
                SelectAllBar(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.background),
                    items = selectAllTargetsOf(listing),
                    keyOf = ::fileListKeyOf,
                    selectedList = selectedList,
                    onReplaceSelection = viewModel::replaceSelection
                )
            }
        }
        HorizontalDivider()
        ListWithBottomButtons(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            buttons = {
                PickerButtons(
                    modifier = Modifier,
                    selectedCount = selection.value.selected.size,
                    canAdd = canAdd,
                    onCancel = viewModel::close,
                    onConfirm = onConfirm
                )
            }
        ) { buttonsPadding ->
            DirectoryListingContent(
                modifier = Modifier.fillMaxSize(),
                listing = listing,
                onRetry = viewModel::retryListing
            ) { loaded ->
                FileListView(
                    modifier = Modifier.fillMaxSize(),
                    listState = listState,
                    contentPadding = buttonsPadding,
                    fileHolderItemList = loaded.items,
                    // 하위 항목 수는 세지 않는다 — 고르는 데 필요한 정보가 아니다
                    childCounts = null,
                    selectedList = selectedList,
                    selectMode = selectMode,
                    clickActionOf = { pickerClickActionOf(selectMode == SelectMode.MultiSelect, it) },
                    onOpen = viewModel::openFolder,
                    onStartMultiSelect = viewModel::startMultiSelect,
                    onReplaceSelection = viewModel::replaceSelection,
                    addToList = viewModel::select,
                    removeFromList = viewModel::deselect
                )
            }
        }
    }
}

/**
 * 고르는 화면에서 [item]을 눌렀을 때 할 일을 정한다. 다중 선택 중에는 선택을 토글하고, 아니면 폴더는 들어가고 파일은 고른다.
 * @param isMultiSelect 다중 선택 중이면 true
 * @param item 누른 항목
 * @return 누른 항목에 할 일
 */
internal fun pickerClickActionOf(isMultiSelect: Boolean, item: FileHolderItem): ItemClickAction = when {
    isMultiSelect -> ItemClickAction.TOGGLE
    item.isDirectory -> ItemClickAction.OPEN
    else -> ItemClickAction.TOGGLE
}

/**
 * 고르는 화면의 제목(넣을 압축 파일)과 지금 폴더의 경로 표시줄.
 * @param modifier 배치를 정하는 Modifier
 * @param archiveName 넣을 압축 파일의 이름
 * @param dirTree 저장소 볼륨 루트부터 지금 폴더까지
 * @param onDirClick 경로 표시줄 항목을 눌렀을 때의 콜백
 */
@Composable
private fun PickerHeader(
    modifier: Modifier,
    archiveName: String,
    dirTree: List<DirectoryItem>,
    onDirClick: (DirectoryItem) -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.archive_add_picker_title, archiveName),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp)
        )
        PathBreadcrumb(dirTree = dirTree, onDirClick = onDirClick)
    }
}

/**
 * 고르는 화면의 목록 아래에 겹쳐 그리는 취소·추가 버튼. 높이는 내용 보기의 메뉴 줄과 같다.
 * @param modifier 배치를 정하는 Modifier
 * @param selectedCount 고른 항목 수
 * @param canAdd 지금 압축 파일에 항목을 넣을 수 있으면 true
 * @param onCancel 취소 버튼이 부르는 콜백 — 고르는 화면을 닫는다
 * @param onConfirm 추가 버튼이 부르는 콜백
 */
@Composable
private fun PickerButtons(
    modifier: Modifier,
    selectedCount: Int,
    canAdd: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.btn_cancel))
        }
        TextButton(onClick = onConfirm, enabled = canAdd && selectedCount > 0, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.btn_add_picked, selectedCount))
        }
    }
}
