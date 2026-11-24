package com.todokanai.composepracticenew.compose.listview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todokanai.composepracticenew.compose.holder.FileHolder
import com.todokanai.composepracticenew.compose.util.verticalScrollbar
import com.todokanai.composepracticenew.ui.model.ChildCountInfo
import com.todokanai.composepracticenew.ui.model.DragSelection
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.ItemClickAction
import com.todokanai.composepracticenew.ui.model.ListingUiState
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.viewmodel.ChildCountSource

/** 스크롤바 터치 영역 너비 — Material 최소 터치 타겟(48dp), thumb 시각 너비(8dp)와 별개 */
private val ScrollbarTouchTargetWidth = 48.dp

/**
 * 파일 목록 행의 LazyColumn 키. 드래그 선택(dragSelectGesture)이 손가락 아래 행을 이 키로 찾으므로 목록과 제스처가 함께 쓴다.
 * @param item 행의 항목
 * @return 항목의 경로 — 목록 안에서 항목마다 하나다
 */
internal fun fileListKeyOf(item: FileHolderItem): Any = item.path

/**
 * 보이는 목록의 경로마다 스크롤 위치를 하나씩 만든다. 목록(FileListView)을 화면에서 빼는 분기(검색, 조회 중 표시 등)보다 위인 화면 단위에서 불러야 한다 —
 * 그래야 목록이 잠시 빠졌다 돌아와도 같은 경로면 위치가 남는다.
 * @param listing 지금 목록 상태. 항목을 보여 주는 상태가 아니면(조회 중·실패) 위치를 버린다
 * @return 지금 경로의 목록 스크롤 상태
 */
@Composable
fun rememberListingScrollState(listing: ListingUiState): LazyListState {
    /** 지금 보이는 목록의 경로. 목록을 보여 주지 않는 상태(조회 중·실패)면 null. */
    val listedPath = (listing as? ListingUiState.Loaded)?.key?.path
    // 보이는 목록의 경로를 키로 — 다른 경로가 되거나 목록이 없어지면 맨 위부터 새로 시작하고, 같은 경로를 다시 조회하는 동안(직전 목록 표시)은 위치를 유지한다.
    // rememberSaveable이라 화면 회전 뒤에도 같은 경로의 위치가 복원된다
    return rememberSaveable(listedPath, saver = LazyListState.Saver) { LazyListState() }
}

/**
 * 파일 목록을 스크롤바와 함께 표시하고, longPress 드래그 다중 선택을 지원한다. 길게 누르기는 행이 아니라 목록(dragSelectGesture)만 받는다.
 * @param listState 목록과 스크롤바가 함께 쓰는 스크롤 상태. 목록이 화면에서 빠져도 위치가 남도록 호출부가 [rememberListingScrollState]로 만들어 넘긴다
 * @param contentPadding 목록 안쪽 여백. 목록 위에 겹친 하단 버튼의 높이를 받아, 마지막 행과 스크롤바 끝이 버튼에 가리지 않게 한다
 * @param selectedList 선택 목록을 반환하는 함수 — 항목별 선택 여부를 derivedStateOf로 구독해, 선택이 바뀐 행만 리컴포지션한다
 * @param childCounts [fileHolderItemList]의 폴더별 하위 항목 수. 폴더 행이 화면에 있는 동안에만 센다. null이면 세지 않는다(원격 목록)
 * @param clickActionOf 누른 항목에 할 일을 정하는 함수 — 화면의 선택 모드 규칙
 * @param onOpen 항목을 여는 콜백(항목, 압축 파일만 여는지). [clickActionOf]가 열기로 정한 항목에만 부른다
 * @param onReplaceSelection 다중 선택 목록을 바꾸는 콜백 — 드래그 범위 선택에 쓴다
 */
@Composable
fun FileListView(
    modifier: Modifier,
    listState: LazyListState,
    contentPadding: PaddingValues,
    fileHolderItemList: List<FileHolderItem>,
    childCounts: ChildCountSource?,
    selectedList: () -> List<FileHolderItem>,
    selectMode: SelectMode,
    clickActionOf: (FileHolderItem) -> ItemClickAction,
    onOpen: (FileHolderItem, Boolean) -> Unit,
    onStartMultiSelect: (FileHolderItem) -> Unit,
    onReplaceSelection: (List<FileHolderItem>) -> Unit,
    addToList: (FileHolderItem) -> Unit,
    removeFromList: (FileHolderItem) -> Unit
) {
    // items{} 밖에서 한 번 만들어 모든 행(FileHolder)이 같은 인스턴스를 공유 — 행이 Strong Skipping의 인스턴스 비교로 생략된다
    val typeIcons = rememberTypeIcons()

    // selectedList 함수를 키로 — 함수가 바뀌면 새 출처를 추적해야 하므로 재생성. derivedStateOf는 selectedList()가 읽는 선택 목록 State를 추적하며,
    // 이 컴포저블 본문에서는 .value를 읽지 않으므로 선택 변경이 목록 전체 리컴포지션을 일으키지 않는다
    val selectedPathsState = remember(selectedList) {
        derivedStateOf { selectedList().mapTo(HashSet()) { it.path } }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .dragSelectGesture(
                listState = listState,
                items = fileHolderItemList,
                keyOf = ::fileListKeyOf,
                startSelection = { anchor -> startDragSelection(selectMode, anchor, selectedList, onStartMultiSelect) },
                onReplaceSelection = onReplaceSelection
            )
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = contentPadding
        ) {
            items(fileHolderItemList, key = ::fileListKeyOf) { fileHolderItem ->

                // path·selectedPathsState를 키로 — 행 재사용 또는 출처 교체 시 재생성. selectedPathsState를 추적하되 이 행의 선택 여부가 실제로 바뀔 때만 item 스코프를 무효화한다
                val isSelected by remember(fileHolderItem.path, selectedPathsState) {
                    derivedStateOf { fileHolderItem.path in selectedPathsState.value }
                }

                FileHolder(
                    modifier = Modifier,
                    file = fileHolderItem,
                    isSelected = isSelected,
                    typeIcons = typeIcons,
                    childCount = childCounts?.takeIf { fileHolderItem.isDirectory }?.let { childCountOf(it, fileHolderItem) },
                    // Strong Skipping이 캡처값 기준으로 람다를 memoize하므로 수동 remember 불필요
                    onClick = {
                        when (clickActionOf(fileHolderItem)) {
                            ItemClickAction.TOGGLE -> if (isSelected) removeFromList(fileHolderItem) else addToList(fileHolderItem)
                            ItemClickAction.OPEN -> onOpen(fileHolderItem, false)
                            ItemClickAction.OPEN_ARCHIVE -> onOpen(fileHolderItem, true)
                            ItemClickAction.IGNORE -> {}
                        }
                    }
                )
            }
        }
        // 스크롤바 전용 strip — 오른쪽 끝만 커버하여 목록 터치 이벤트와 hit-testing 충돌 없음. 목록 안쪽 여백만큼 줄여 끝까지 스크롤했을 때 thumb가 겹친 버튼 뒤로 가지 않게 한다
        Spacer(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())
                .fillMaxHeight()
                .width(ScrollbarTouchTargetWidth)
                .verticalScrollbar(listState)
        )
    }
}

/**
 * 선택 모드에 맞춰 드래그 선택을 시작하고 이번 드래그의 범위 선택 규칙을 정한다.
 * 기본 모드에서 시작하면 다중 선택으로 들어가 범위를 선택하고, 다중 선택 중에 시작하면 시작 항목의 선택 여부에 따라 범위를 선택하거나 해제한다.
 * @param selectMode 드래그를 시작할 때의 선택 모드
 * @param anchor 드래그를 시작한 항목
 * @param selectedList 선택 목록을 반환하는 함수
 * @param onStartMultiSelect anchor를 선택한 채로 다중 선택 모드로 전환을 요청하는 콜백 — 기본 모드에서만 부른다
 * @return 범위 선택 규칙. 목적지 확인 단계처럼 선택을 편집하지 않는 모드면 null이며 드래그 선택을 시작하지 않는다
 */
private fun startDragSelection(
    selectMode: SelectMode,
    anchor: FileHolderItem,
    selectedList: () -> List<FileHolderItem>,
    onStartMultiSelect: (FileHolderItem) -> Unit
): DragSelection<FileHolderItem>? = when (selectMode) {
    SelectMode.Default -> DragSelection.fresh(::fileListKeyOf).also { onStartMultiSelect(anchor) }
    SelectMode.MultiSelect -> DragSelection.startingAt(anchor, selectedList(), ::fileListKeyOf)
    is SelectMode.LocalOperation, SelectMode.AddToArchive, is SelectMode.Download, is SelectMode.ArchiveCopy, is SelectMode.Upload -> null
}

/**
 * 폴더 행 [directory]의 하위 항목 수를 [childCounts]에서 구독한다. 행이 composition을 벗어나거나(스크롤로 화면 밖) 화면이 STARTED 미만이면 세던 것을 멈춘다.
 * @param childCounts 행이 속한 목록 조회 결과의 하위 항목 수
 * @param directory 폴더 행의 항목
 * @return 지금의 표시 상태
 */
@Composable
private fun childCountOf(childCounts: ChildCountSource, directory: FileHolderItem): ChildCountInfo =
    // childCounts·directory 키(값 비교) — 정렬 변경 등으로 같은 조회 결과의 항목이 새로 만들어져도 키가 같아 구독을 이어 가고,
    // 행은 경로 키로 새 조회 결과에서도 재사용되므로 조회 결과가 바뀌면 구독 상태를 새로 만들어 이전 결과에서 센 값이 새 결과의 값으로 보이지 않게 한다
    key(childCounts, directory) {
        // 키 블록 안에서 1회 생성 — 리컴포지션마다 Flow를 새로 만들면 구독을 다시 시작한다
        val count = remember { childCounts.countOf(directory) }
        count.collectAsStateWithLifecycle(initialValue = childCounts.currentOf(directory)).value
    }
