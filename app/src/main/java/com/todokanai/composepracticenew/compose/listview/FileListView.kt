package com.todokanai.composepracticenew.compose.listview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.ListingUiState
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.viewmodel.ChildCountSource

/** 스크롤바 터치 영역 너비 — Material 최소 터치 타겟(48dp), thumb 시각 너비(8dp)와 별개 */
private val ScrollbarTouchTargetWidth = 48.dp

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
 * 파일 목록을 스크롤바와 함께 표시하고, longPress 드래그 다중 선택을 지원한다.
 * @param listState 목록과 스크롤바가 함께 쓰는 스크롤 상태. 목록이 화면에서 빠져도 위치가 남도록 호출부가 [rememberListingScrollState]로 만들어 넘긴다
 * @param selectedList 선택 목록을 반환하는 함수 — 항목별 선택 여부를 derivedStateOf로 구독해, 선택이 바뀐 행만 리컴포지션한다
 * @param childCounts [fileHolderItemList]의 폴더별 하위 항목 수. 폴더 행이 화면에 있는 동안에만 센다. null이면 세지 않는다(원격 목록)
 */
@Composable
fun FileListView(
    modifier: Modifier,
    listState: LazyListState,
    fileHolderItemList: List<FileHolderItem>,
    childCounts: ChildCountSource?,
    selectedList: () -> List<FileHolderItem>,
    selectMode: SelectMode,
    onItemClick: (FileHolderItem) -> Unit,
    onStartMultiSelect: (FileHolderItem) -> Unit,
    onRangeSelect: (List<FileHolderItem>) -> Unit,
    addToList: (FileHolderItem) -> Unit,
    removeFromList: (FileHolderItem) -> Unit
) {
    // items{} 밖에서 한 번 만들어 모든 행(FileHolder)이 같은 인스턴스를 공유 — 행이 Strong Skipping의 인스턴스 비교로 생략된다
    val typeIcons = rememberTypeIcons()

    // long-press는 Box 레벨 dragSelectGesture에서 처리 — combinedClickable 이중 발화 방지
    val noOpLongClick: () -> Unit = remember { {} }

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
                fileList = fileHolderItemList,
                selectMode = selectMode,
                onStartMultiSelect = onStartMultiSelect,
                onRangeSelect = onRangeSelect
            )
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth()
        ) {
            items(fileHolderItemList, key = { it.path }) { fileHolderItem ->

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
                        if (selectMode == SelectMode.MultiSelect) {
                            if (isSelected) removeFromList(fileHolderItem)
                            else addToList(fileHolderItem)
                        } else {
                            onItemClick(fileHolderItem)
                        }
                    },
                    onLongClick = noOpLongClick
                )
            }
        }
        // 스크롤바 전용 strip — 오른쪽 끝만 커버하여 목록 터치 이벤트와 hit-testing 충돌 없음
        Spacer(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(ScrollbarTouchTargetWidth)
                .verticalScrollbar(listState)
        )
    }
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
