package com.todokanai.composepracticenew.compose.listview

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.todokanai.composepracticenew.ui.model.DragSelection
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.SelectMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 드래그 선택 진행 중 값 — composition에서 읽지 않으므로 State가 아닌 일반 필드로 둬 포인터 이벤트마다 snapshot write가 생기지 않게 한다.
 */
private class DragSelectProgress {
    /** 드래그를 시작한 항목 인덱스 — 범위 선택의 고정 끝점 */
    var anchorIndex = 0
    /** 가장 최근 드래그 Y 좌표 — 엣지 스크롤 후 항목 재평가에 사용 */
    var currentFingerY = 0f
    /** 0f=정지, 음수=위, 양수=아래 — 경계 근접도에 비례 */
    var scrollSpeed = 0f
    /** 마지막으로 범위를 보고한 인덱스 — 같은 인덱스의 중복 보고를 막는다. -1은 "보고 없음" */
    var lastReportedIndex = -1
    /** 이번 드래그의 범위 선택 규칙 — 드래그를 시작할 때 정한다 */
    var selection = DragSelection.Fresh
}

/**
 * longPress 후 드래그로 항목 범위를 선택하고, 상·하단 경계 근접 시 자동 스크롤한다.
 * 기본 모드에서 시작하면 다중 선택으로 들어가 범위를 선택하고, 다중 선택 중에 시작하면 시작 항목의 선택 여부에 따라 범위를 선택하거나 해제한다([DragSelection]).
 *
 * @param listState 대상 LazyColumn의 스크롤 상태
 * @param fileList 현재 표시 중인 파일 항목 목록
 * @param selectMode 현재 선택 모드 — 기본·다중 선택 모드에서만 드래그 선택이 시작된다
 * @param selectedList 선택 목록을 반환하는 함수 — 다중 선택 중 드래그를 시작할 때의 선택을 읽는다
 * @param onStartMultiSelect 기본 모드에서 드래그 시작 시 anchor 항목을 선택한 채로 다중 선택 모드로 전환을 요청하는 콜백
 * @param onReplaceSelection 드래그 범위를 반영한 선택 목록을 전달
 */
@Composable
fun Modifier.dragSelectGesture(
    listState: LazyListState,
    fileList: List<FileHolderItem>,
    selectMode: SelectMode,
    selectedList: () -> List<FileHolderItem>,
    onStartMultiSelect: (FileHolderItem) -> Unit,
    onReplaceSelection: (List<FileHolderItem>) -> Unit
): Modifier {
    // isDragSelecting만 LaunchedEffect 키로 쓰이므로 State로 유지 — 키 없이 1회 생성해 리컴포지션 간 드래그 상태 보존
    var isDragSelecting by remember { mutableStateOf(false) }
    // 키 없이 1회 생성 — 포인터 콜백과 LaunchedEffect가 같은 인스턴스를 공유해야 하며, State가 아니므로 갱신이 리컴포지션을 일으키지 않는다
    val progress = remember { DragSelectProgress() }

    // pointerInput(Unit) 클로저 내 stale 캡처 방지 — Composable 파라미터는 재호출 시 새 값이 오므로 State로 감쌈
    val latestFileList           by rememberUpdatedState(fileList)
    val latestSelectMode         by rememberUpdatedState(selectMode)
    val latestSelectedList       by rememberUpdatedState(selectedList)
    val latestOnStartMultiSelect by rememberUpdatedState(onStartMultiSelect)
    val latestOnReplaceSelection by rememberUpdatedState(onReplaceSelection)

    // 손가락 위치의 항목까지 범위를 선택한다 — 인덱스가 바뀌지 않았으면 보고하지 않는다
    fun selectRangeAt(y: Float) {
        val list = latestFileList
        if (list.isEmpty()) return
        val idx = listState.itemIndexAtOffset(y)?.coerceIn(0, list.lastIndex) ?: return
        if (idx == progress.lastReportedIndex) return
        progress.lastReportedIndex = idx
        val anchor = progress.anchorIndex
        latestOnReplaceSelection(progress.selection.selectionWith(list.subList(minOf(anchor, idx), maxOf(anchor, idx) + 1)))
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

        detectOwnedDragAfterLongPress(
            onLongPress = { offset ->
                val list = latestFileList
                val idx = listState.itemIndexAtOffset(offset.y)
                    ?.coerceIn(0, list.lastIndex)
                    ?: return@detectOwnedDragAfterLongPress false
                val anchorItem = list[idx]
                val selection = startDragSelection(latestSelectMode, anchorItem, latestSelectedList, latestOnStartMultiSelect)
                    ?: return@detectOwnedDragAfterLongPress false
                progress.anchorIndex       = idx
                progress.lastReportedIndex = idx
                progress.currentFingerY    = offset.y
                progress.selection         = selection
                isDragSelecting = true
                latestOnReplaceSelection(selection.selectionWith(listOf(anchorItem)))
                true
            },
            onDrag = { position ->
                val y         = position.y
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
            },
            onDragEnd = { isDragSelecting = false; progress.scrollSpeed = 0f }
        )
    }
}

/**
 * 길게 누른 뒤의 드래그를 감지하고, 드래그를 시작하면 손을 뗄 때까지 입력을 독점한다.
 * 길게 누르기 전에는 이벤트를 소비하지 않아 행의 탭과 목록 스크롤이 그대로 받고, 시작한 뒤에는 자식보다 먼저(Initial 패스) 모든 변화를 소비해
 * 행의 탭(손을 뗄 때)과 목록 스크롤(손가락이 움직일 때)이 그 입력을 해석하지 않고 취소되게 한다.
 * @param onLongPress 길게 누르기를 인식한 위치를 받아 드래그를 시작했는지 돌려준다. false면 입력을 가져가지 않는다
 * @param onDrag 드래그 중 손가락 위치를 받는다
 * @param onDragEnd 시작한 드래그가 손을 떼거나 취소돼 끝났을 때 부른다
 */
private suspend fun PointerInputScope.detectOwnedDragAfterLongPress(
    onLongPress: (Offset) -> Boolean,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
    if (!onLongPress(longPress.position)) return@awaitEachGesture
    try {
        var pointerId = longPress.id
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
            // 추적하던 손가락을 떼도 다른 손가락이 눌려 있으면 그 손가락으로 이어 간다
            val change = event.changes.firstOrNull { it.id == pointerId && it.pressed }
                ?: event.changes.firstOrNull { it.pressed }
                ?: break
            pointerId = change.id
            onDrag(change.position)
        }
    } finally {
        onDragEnd()
    }
}

/**
 * 선택 모드에 맞춰 드래그 선택을 시작하고 이번 드래그의 범위 선택 규칙을 정한다.
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
): DragSelection? = when (selectMode) {
    SelectMode.Default -> DragSelection.Fresh.also { onStartMultiSelect(anchor) }
    SelectMode.MultiSelect -> DragSelection.startingAt(anchor, selectedList())
    is SelectMode.LocalOperation, SelectMode.Download, SelectMode.Upload -> null
}

// 픽셀 Y 좌표에 해당하는 실제 리스트 인덱스 반환 — viewportStartOffset 보정으로 contentPadding과 무관하게 동작
private fun LazyListState.itemIndexAtOffset(y: Float): Int? {
    val start = layoutInfo.viewportStartOffset
    return layoutInfo.visibleItemsInfo
        .firstOrNull { (it.offset - start) <= y && y < (it.offset - start) + it.size }
        ?.index
}
