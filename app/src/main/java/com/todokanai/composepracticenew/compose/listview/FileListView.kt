package com.todokanai.composepracticenew.compose.listview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.todokanai.composepracticenew.compose.holder.FileHolder
import com.todokanai.composepracticenew.compose.util.verticalScrollbar
import com.todokanai.composepracticenew.myobjects.AppConstants
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.data.R as DataR

/** 스크롤바 터치 영역 너비 — Material 최소 터치 타겟(48dp), thumb 시각 너비(8dp)와 별개 */
private val ScrollbarTouchTargetWidth = 48.dp

private fun resolveExtensionAndIcon(
    name: String,
    isDirectory: Boolean,
    folderIcon: Painter,
    pdfIcon: Painter,
    fileIcon: Painter,
): Pair<String, Painter> {
    val ext = name.substringAfterLast('.', "")
    return ext to when {
        isDirectory  -> folderIcon
        ext == "pdf" -> pdfIcon
        else         -> fileIcon
    }
}

/**
 * 파일 목록을 스크롤바와 함께 표시하고, longPress 드래그 다중 선택을 지원한다.
 * @param selectedList 선택 목록을 반환하는 함수 — 항목별 선택 여부를 derivedStateOf로 구독해, 선택이 바뀐 행만 리컴포지션한다
 */
@Composable
fun FileListView(
    modifier: Modifier,
    fileHolderItemList: List<FileHolderItem>,
    selectedList: () -> List<FileHolderItem>,
    selectMode: SelectMode,
    onItemClick: (FileHolderItem) -> Unit,
    onStartMultiSelect: (FileHolderItem) -> Unit,
    onRangeSelect: (List<FileHolderItem>) -> Unit,
    addToList: (FileHolderItem) -> Unit,
    removeFromList: (FileHolderItem) -> Unit
) {
    // items{} 밖에서 1회 생성 — 스크롤 시 painterResource가 item 수만큼 호출되는 것을 방지
    val folderIcon = painterResource(DataR.drawable.ic_baseline_folder_24)
    val fileIcon = painterResource(DataR.drawable.ic_baseline_insert_drive_file_24)
    val pdfIcon = painterResource(DataR.drawable.ic_pdf)

    // LazyColumn과 verticalScrollbar가 동일한 스크롤 상태를 공유해야 하므로 remember로 1회 생성 — 리컴포지션 간 스크롤 위치 보존
    val listState = rememberLazyListState()

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

                // name 또는 isDirectory가 바뀔 때만 재계산 — extension과 icon을 단일 블록으로 통합
                val (extension, icon) = remember(fileHolderItem.name, fileHolderItem.isDirectory) {
                    resolveExtensionAndIcon(fileHolderItem.name, fileHolderItem.isDirectory, folderIcon, pdfIcon, fileIcon)
                }

                FileHolder(
                    modifier = Modifier,
                    file = fileHolderItem,
                    isSelected = isSelected,
                    icon = icon,
                    isAsyncImage = extension.lowercase() in AppConstants.ASYNC_IMAGE_EXTENSIONS,
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
