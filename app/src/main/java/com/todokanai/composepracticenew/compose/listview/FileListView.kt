package com.todokanai.composepracticenew.compose.listview

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.todokanai.composepracticenew.compose.holder.FileHolder
import com.todokanai.composepracticenew.compose.util.verticalScrollbar
import com.todokanai.composepracticenew.myobjects.AppConstants
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.todokanai.composepracticenew.data.R as DataR

/** 스크롤바 터치 영역 너비 — Material 최소 터치 타겟(48dp), thumb 시각 너비(8dp)와 별개 */
private val ScrollbarTouchTargetWidth = 48.dp

@Composable
fun FileListView(
    modifier: Modifier,
    fileHolderItemList: List<FileHolderItem>,
    selectedPaths: Set<String>,
    selectMode: Int,
    onItemClick: (FileHolderItem) -> Unit,
    onSelectModeChange: (Int) -> Unit,
    onRangeSelect: (List<FileHolderItem>) -> Unit,
    addToList: (FileHolderItem) -> Unit,
    removeFromList: (FileHolderItem) -> Unit,
    clearList: () -> Unit
) {
    // items{} 밖에서 1회 생성 — 스크롤 시 painterResource가 item 수만큼 호출되는 것을 방지
    val folderIcon = painterResource(DataR.drawable.ic_baseline_folder_24)
    val fileIcon = painterResource(DataR.drawable.ic_baseline_insert_drive_file_24)
    val pdfIcon = painterResource(DataR.drawable.ic_pdf)

    // LazyColumn과 verticalScrollbar가 동일한 스크롤 상태를 공유해야 하므로 remember로 1회 생성 — 리컴포지션 간 스크롤 위치 보존
    val listState = rememberLazyListState()

    // anchor~current 범위 계산 기준 인덱스
    var anchorIndex     by remember { mutableStateOf(0) }
    var isDragSelecting by remember { mutableStateOf(false) }
    // 가장 최근 드래그 Y 좌표 — 엣지 스크롤 후 항목 재평가에 사용
    var currentFingerY  by remember { mutableStateOf(0f) }
    // 0f=정지, 음수=위, 양수=아래 — 경계 근접도에 비례
    var scrollSpeed     by remember { mutableStateOf(0f) }

    // pointerInput(Unit) 클로저 내 stale 캡처 방지 — Composable 파라미터는 재호출 시 새 값이 오므로 State로 감쌈
    val latestFileList   by rememberUpdatedState(fileHolderItemList)
    val latestSelectMode by rememberUpdatedState(selectMode)

    // isDragSelecting이 true인 동안 실행 — 스크롤 후 currentFingerY 기준으로 범위 재계산
    LaunchedEffect(isDragSelecting) {
        if (!isDragSelecting) return@LaunchedEffect
        while (isActive) {
            if (scrollSpeed != 0f) {
                listState.scrollBy(scrollSpeed)
                listState.itemIndexAtOffset(currentFingerY)
                    ?.coerceIn(0, latestFileList.lastIndex)
                    ?.let { idx ->
                        onRangeSelect(
                            latestFileList.subList(minOf(anchorIndex, idx), maxOf(anchorIndex, idx) + 1)
                        )
                    }
            }
            delay(16)
        }
    }

    // long-press는 Box 레벨 pointerInput에서 처리 — combinedClickable 이중 발화 방지
    val noOpLongClick: () -> Unit = remember { {} }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                val edgePx     = 80.dp.toPx()
                val maxSpeedPx = 15.dp.toPx()

                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        if (latestSelectMode != AppConstants.DEFAULT_MODE) return@detectDragGesturesAfterLongPress
                        val idx = listState.itemIndexAtOffset(offset.y)
                            ?.coerceIn(0, latestFileList.lastIndex)
                            ?: return@detectDragGesturesAfterLongPress
                        anchorIndex     = idx
                        currentFingerY  = offset.y
                        isDragSelecting = true
                        onRangeSelect(listOf(latestFileList[idx]))
                        onSelectModeChange(AppConstants.MULTI_SELECT_MODE)
                    },
                    onDrag = { change, _ ->
                        if (isDragSelecting) {
                            change.consume()
                            val y         = change.position.y
                            val viewportH = listState.layoutInfo.viewportSize.height.toFloat()
                            currentFingerY = y

                            // 경계 근접도에 비례한 스크롤 속도 (선형 보간)
                            scrollSpeed = when {
                                y < edgePx             -> -maxSpeedPx * (1f - y / edgePx)
                                y > viewportH - edgePx ->  maxSpeedPx * (1f - (viewportH - y) / edgePx)
                                else                   -> 0f
                            }

                            // 엣지 외 영역: 즉시 선택 범위 갱신 (엣지 영역은 LaunchedEffect가 담당)
                            if (scrollSpeed == 0f) {
                                listState.itemIndexAtOffset(y)
                                    ?.coerceIn(0, latestFileList.lastIndex)
                                    ?.let { idx ->
                                        onRangeSelect(
                                            latestFileList.subList(minOf(anchorIndex, idx), maxOf(anchorIndex, idx) + 1)
                                        )
                                    }
                            }
                        }
                    },
                    onDragEnd    = { isDragSelecting = false; scrollSpeed = 0f },
                    onDragCancel = { isDragSelecting = false; scrollSpeed = 0f }
                )
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth()
        ) {
            items(fileHolderItemList, key = { it.path }) { fileHolderItem ->

                val isSelected = fileHolderItem.path in selectedPaths

                // name 또는 isDirectory가 바뀔 때만 재계산 — extension과 icon을 단일 블록으로 통합
                val (extension, icon) = remember(fileHolderItem.name, fileHolderItem.isDirectory) {
                    val ext = fileHolderItem.name.substringAfterLast('.', "")
                    val ico = when {
                        fileHolderItem.isDirectory -> folderIcon
                        ext == "pdf" -> pdfIcon
                        else -> fileIcon
                    }
                    ext to ico
                }

                val onClick = remember(fileHolderItem, selectMode, isSelected) {
                    {
                        if (selectMode == AppConstants.MULTI_SELECT_MODE) {
                            if (isSelected) removeFromList(fileHolderItem)
                            else addToList(fileHolderItem)
                        } else {
                            onItemClick(fileHolderItem)
                        }
                    }
                }

                FileHolder(
                    modifier = Modifier,
                    file = fileHolderItem,
                    isSelected = isSelected,
                    icon = icon,
                    isAsyncImage = extension.lowercase() in AppConstants.ASYNC_IMAGE_EXTENSIONS,
                    onClick = onClick,
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

// 픽셀 Y 좌표에 해당하는 실제 리스트 인덱스 반환
private fun LazyListState.itemIndexAtOffset(y: Float): Int? =
    layoutInfo.visibleItemsInfo
        .firstOrNull { it.offset <= y && y < it.offset + it.size }
        ?.index
