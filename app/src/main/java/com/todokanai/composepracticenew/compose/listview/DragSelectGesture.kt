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
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 드래그 선택 진행 중 값 — composition에서 읽지 않으므로 State가 아닌 일반 필드로 둬 포인터 이벤트마다 snapshot write가 생기지 않게 한다.
 */
private class DragSelectProgress {
    var anchorIndex = 0
    /** 가장 최근 드래그 Y 좌표 — 엣지 스크롤 후 항목 재평가에 사용 */
    var currentFingerY = 0f
    /** 0f=정지, 음수=위, 양수=아래 — 경계 근접도에 비례 */
    var scrollSpeed = 0f
    /** 마지막으로 범위를 보고한 인덱스 — 같은 인덱스의 중복 보고를 막는다. -1은 "보고 없음" */
    var lastReportedIndex = -1
}

/**
 * longPress 후 드래그로 항목 범위를 선택하고, 상·하단 경계 근접 시 자동 스크롤한다.
 *
 * @param listState 대상 LazyColumn의 스크롤 상태
 * @param fileList 현재 표시 중인 파일 항목 목록
 * @param selectMode 현재 선택 모드 — SelectMode.Default일 때만 드래그 선택이 시작된다
 * @param onStartMultiSelect 드래그 시작 시 다중 선택 모드로 전환을 요청하는 콜백
 * @param onRangeSelect anchor~현재 인덱스 범위의 항목 목록을 전달
 */
@Composable
fun Modifier.dragSelectGesture(
    listState: LazyListState,
    fileList: List<FileHolderItem>,
    selectMode: SelectMode,
    onStartMultiSelect: () -> Unit,
    onRangeSelect: (List<FileHolderItem>) -> Unit
): Modifier {
    // isDragSelecting만 LaunchedEffect 키로 쓰이므로 State로 유지 — 키 없이 1회 생성해 리컴포지션 간 드래그 상태 보존
    var isDragSelecting by remember { mutableStateOf(false) }
    // 키 없이 1회 생성 — 포인터 콜백과 LaunchedEffect가 같은 인스턴스를 공유해야 하며, State가 아니므로 갱신이 리컴포지션을 일으키지 않는다
    val progress = remember { DragSelectProgress() }

    // pointerInput(Unit) 클로저 내 stale 캡처 방지 — Composable 파라미터는 재호출 시 새 값이 오므로 State로 감쌈
    val latestFileList           by rememberUpdatedState(fileList)
    val latestSelectMode         by rememberUpdatedState(selectMode)
    val latestOnStartMultiSelect by rememberUpdatedState(onStartMultiSelect)
    val latestOnRangeSelect      by rememberUpdatedState(onRangeSelect)

    // 손가락 위치의 항목까지 범위를 선택한다 — 인덱스가 바뀌지 않았으면 보고하지 않는다
    fun selectRangeAt(y: Float) {
        val list = latestFileList
        if (list.isEmpty()) return
        val idx = listState.itemIndexAtOffset(y)?.coerceIn(0, list.lastIndex) ?: return
        if (idx == progress.lastReportedIndex) return
        progress.lastReportedIndex = idx
        val anchor = progress.anchorIndex
        latestOnRangeSelect(list.subList(minOf(anchor, idx), maxOf(anchor, idx) + 1))
    }

    // isDragSelecting이 true인 동안 실행 — 스크롤 후 currentFingerY 기준으로 범위 재계산
    LaunchedEffect(isDragSelecting) {
        if (!isDragSelecting) return@LaunchedEffect
        while (isActive) {
            if (progress.scrollSpeed != 0f && latestFileList.isNotEmpty()) {
                listState.scrollBy(progress.scrollSpeed)
                selectRangeAt(progress.currentFingerY)
            }
            delay(16)
        }
    }

    return pointerInput(Unit) {
        val edgePx     = 80.dp.toPx()
        val maxSpeedPx = 15.dp.toPx()

        val onDragReset = { isDragSelecting = false; progress.scrollSpeed = 0f }

        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                if (latestSelectMode != SelectMode.Default) return@detectDragGesturesAfterLongPress
                val list = latestFileList
                val idx = listState.itemIndexAtOffset(offset.y)
                    ?.coerceIn(0, list.lastIndex)
                    ?: return@detectDragGesturesAfterLongPress
                progress.anchorIndex       = idx
                progress.lastReportedIndex = idx
                progress.currentFingerY    = offset.y
                isDragSelecting = true
                latestOnRangeSelect(listOf(list[idx]))
                latestOnStartMultiSelect()
            },
            onDrag = { change, _ ->
                if (isDragSelecting) {
                    change.consume()
                    val y         = change.position.y
                    val viewportH = listState.layoutInfo.viewportSize.height.toFloat()
                    progress.currentFingerY = y

                    // 경계 근접도에 비례한 스크롤 속도 (선형 보간)
                    progress.scrollSpeed = when {
                        y < edgePx             -> -maxSpeedPx * (1f - y / edgePx)
                        y > viewportH - edgePx ->  maxSpeedPx * (1f - (viewportH - y) / edgePx)
                        else                   -> 0f
                    }

                    // 엣지 외 영역: 즉시 선택 범위 갱신 (엣지 영역은 LaunchedEffect가 담당)
                    if (progress.scrollSpeed == 0f) selectRangeAt(y)
                }
            },
            onDragEnd    = onDragReset,
            onDragCancel = onDragReset
        )
    }
}

// 픽셀 Y 좌표에 해당하는 실제 리스트 인덱스 반환 — viewportStartOffset 보정으로 contentPadding과 무관하게 동작
private fun LazyListState.itemIndexAtOffset(y: Float): Int? {
    val start = layoutInfo.viewportStartOffset
    return layoutInfo.visibleItemsInfo
        .firstOrNull { (it.offset - start) <= y && y < (it.offset - start) + it.size }
        ?.index
}
