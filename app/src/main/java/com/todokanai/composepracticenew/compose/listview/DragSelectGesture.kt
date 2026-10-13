package com.todokanai.composepracticenew.compose.listview

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.todokanai.composepracticenew.myobjects.AppConstants
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * longPress 후 드래그로 항목 범위를 선택하고, 상·하단 경계 근접 시 자동 스크롤한다.
 *
 * @param listState 대상 LazyColumn의 스크롤 상태
 * @param fileList 현재 표시 중인 파일 항목 목록
 * @param selectMode 현재 선택 모드 — DEFAULT_MODE일 때만 드래그 선택이 시작된다
 * @param onSelectModeChange 드래그 시작 시 MULTI_SELECT_MODE로 전환 요청
 * @param onRangeSelect anchor~현재 인덱스 범위의 항목 목록을 전달
 */
@Composable
fun Modifier.dragSelectGesture(
    listState: LazyListState,
    fileList: List<FileHolderItem>,
    selectMode: Int,
    onSelectModeChange: (Int) -> Unit,
    onRangeSelect: (List<FileHolderItem>) -> Unit
): Modifier {
    var anchorIndex     by remember { mutableStateOf(0) }
    var isDragSelecting by remember { mutableStateOf(false) }
    // 가장 최근 드래그 Y 좌표 — 엣지 스크롤 후 항목 재평가에 사용
    var currentFingerY  by remember { mutableStateOf(0f) }
    // 0f=정지, 음수=위, 양수=아래 — 경계 근접도에 비례
    var scrollSpeed     by remember { mutableStateOf(0f) }

    // pointerInput(Unit) 클로저 내 stale 캡처 방지 — Composable 파라미터는 재호출 시 새 값이 오므로 State로 감쌈
    val latestFileList   by rememberUpdatedState(fileList)
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

    return pointerInput(Unit) {
        val edgePx     = 80.dp.toPx()
        val maxSpeedPx = 15.dp.toPx()

        val onDragReset = { isDragSelecting = false; scrollSpeed = 0f }

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
            onDragEnd    = onDragReset,
            onDragCancel = onDragReset
        )
    }
}

// 픽셀 Y 좌표에 해당하는 실제 리스트 인덱스 반환
private fun LazyListState.itemIndexAtOffset(y: Float): Int? =
    layoutInfo.visibleItemsInfo
        .firstOrNull { it.offset <= y && y < it.offset + it.size }
        ?.index
