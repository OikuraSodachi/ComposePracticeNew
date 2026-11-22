package com.todokanai.composepracticenew.compose.listview

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
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
    /** 드래그를 시작한 항목의 목록 키 — 범위 선택의 고정 끝점. 위치가 아니라 항목으로 기억해, 드래그 중 목록이 바뀌어도 같은 항목을 가리킨다 */
    var anchorKey: Any? = null
    /** 가장 최근 드래그 Y 좌표 — 엣지 스크롤 후 항목 재평가에 사용 */
    var currentFingerY = 0f
    /** 0f=정지, 음수=위, 양수=아래 — 경계 근접도에 비례 */
    var scrollSpeed = 0f
    /** 마지막으로 범위를 보고한 손가락 아래 항목의 키 — 같은 목록에서 같은 항목의 중복 보고를 막는다 */
    var lastReportedKey: Any? = null
    /** 마지막으로 범위를 보고한 목록 — 목록이 바뀌면 손가락이 같은 항목에 있어도 범위가 달라지므로 다시 보고한다 */
    var lastReportedList: List<FileHolderItem>? = null
    /** 이번 드래그의 범위 선택 규칙 — 드래그를 시작할 때 정한다 */
    var selection = DragSelection.Fresh
    /** 시작한 항목이 목록에서 사라져 이번 드래그의 범위 갱신을 멈췄는지 — 손을 뗄 때까지 입력은 계속 독점한다 */
    var rangeStopped = false
}

/** 드래그 범위를 지금 목록에서 찾은 결과. */
internal sealed interface DragRange {
    /**
     * 시작한 항목부터 손가락 아래 항목까지의 범위.
     * @param items 지금 목록에서 두 항목 사이(양 끝 포함)의 항목
     */
    data class Found(val items: List<FileHolderItem>) : DragRange

    /** 시작한 항목이 지금 목록에 없다 — 범위를 정할 끝점이 없으므로 이번 드래그의 범위 갱신을 멈춘다. */
    data object AnchorGone : DragRange

    /** 손가락 아래 항목이 지금 목록에 없다(화면 배치가 목록보다 한 프레임 늦음) — 이번 보고만 건너뛴다. */
    data object FingerUnresolved : DragRange
}

/**
 * 시작한 항목과 손가락 아래 항목을 키로 지금 목록에서 찾아 그 사이를 범위로 정한다.
 * @param list 지금 목록
 * @param anchorKey 드래그를 시작한 항목의 키([fileListKeyOf])
 * @param fingerKey 손가락 아래 항목의 키
 * @return 범위, 또는 끝점을 지금 목록에서 찾지 못한 이유
 */
internal fun dragRangeIn(list: List<FileHolderItem>, anchorKey: Any, fingerKey: Any): DragRange {
    val anchor = list.indexOfFirst { fileListKeyOf(it) == anchorKey }
    if (anchor < 0) return DragRange.AnchorGone
    val finger = list.indexOfFirst { fileListKeyOf(it) == fingerKey }
    if (finger < 0) return DragRange.FingerUnresolved
    return DragRange.Found(list.subList(minOf(anchor, finger), maxOf(anchor, finger) + 1))
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

    // 시작한 항목이 사라졌을 때 이번 드래그의 범위 갱신을 멈춘다 — 자동 스크롤도 멈추고, 그때까지의 선택은 그대로 둔다
    fun stopRange() {
        progress.rangeStopped = true
        progress.scrollSpeed = 0f
        isDragSelecting = false
    }

    // 손가락 위치의 항목까지 범위를 선택한다 — 같은 목록에서 같은 항목이면 보고하지 않는다.
    // 손가락이 행 위에 없어도(목록 밖, 빈 곳) 가장 가까운 행까지 선택해, 자동 스크롤 중에도 범위가 따라오고 빠르게 쓸어 건너뛴 행도 빠지지 않는다(#521)
    fun selectRangeAt(y: Float) {
        if (progress.rangeStopped) return
        val list = latestFileList
        val anchorKey = progress.anchorKey ?: return
        val layout = listState.layoutInfo
        val fingerKey = dragTargetKeyIn(layout.visibleItemsInfo, layout.viewportStartOffset, y) ?: return
        if (fingerKey == progress.lastReportedKey && list === progress.lastReportedList) return
        when (val range = dragRangeIn(list, anchorKey, fingerKey)) {
            is DragRange.Found -> {
                progress.lastReportedKey = fingerKey
                progress.lastReportedList = list
                latestOnReplaceSelection(progress.selection.selectionWith(range.items))
            }
            DragRange.AnchorGone -> stopRange()
            DragRange.FingerUnresolved -> Unit
        }
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
                val anchorKey = listState.itemKeyAtOffset(offset.y) ?: return@detectOwnedDragAfterLongPress false
                val anchorItem = list.firstOrNull { fileListKeyOf(it) == anchorKey } ?: return@detectOwnedDragAfterLongPress false
                val selection = startDragSelection(latestSelectMode, anchorItem, latestSelectedList, latestOnStartMultiSelect)
                    ?: return@detectOwnedDragAfterLongPress false
                progress.anchorKey        = anchorKey
                progress.lastReportedKey  = anchorKey
                progress.lastReportedList = list
                progress.currentFingerY   = offset.y
                progress.selection        = selection
                progress.rangeStopped     = false
                isDragSelecting = true
                latestOnReplaceSelection(selection.selectionWith(listOf(anchorItem)))
                true
            },
            onDrag = { position ->
                if (progress.rangeStopped) return@detectOwnedDragAfterLongPress
                val y         = position.y
                val viewportH = listState.layoutInfo.viewportSize.height.toFloat()
                progress.currentFingerY = y

                progress.scrollSpeed = edgeScrollSpeed(y, viewportH, edgePx, maxSpeedPx)

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

/**
 * 드래그 중 손가락 위치에 따른 자동 스크롤 속도를 정한다. 가장자리 영역 안에서는 끝에 가까울수록 빨라지고, 영역 끝과 목록 밖에서는 최대 속도다(#521).
 * 드래그는 손을 뗄 때까지 입력을 독점해 손가락이 목록 밖으로 나가도 위치가 계속 오므로, 배율을 0~1로 제한해야 목록에서 멀어질수록 끝없이 빨라지지 않는다.
 * @param y 목록 위쪽 끝 기준 손가락 Y 좌표. 목록 밖이면 음수이거나 [viewportHeight]보다 크다
 * @param viewportHeight 목록 높이
 * @param edgePx 자동 스크롤이 시작되는 가장자리 영역의 높이
 * @param maxSpeedPx 프레임마다 스크롤할 최대 거리
 * @return 프레임마다 스크롤할 거리. 0이면 멈춤, 음수면 위로, 양수면 아래로
 */
internal fun edgeScrollSpeed(y: Float, viewportHeight: Float, edgePx: Float, maxSpeedPx: Float): Float = when {
    y < edgePx                  -> -maxSpeedPx * edgeProximity(edgePx - y, edgePx)
    y > viewportHeight - edgePx ->  maxSpeedPx * edgeProximity(y - (viewportHeight - edgePx), edgePx)
    else                        -> 0f
}

// 가장자리 영역에 들어간 깊이를 0~1 배율로 바꾼다 — 영역을 지나 목록 밖까지 가도 1을 넘지 않는다
private fun edgeProximity(depth: Float, edgePx: Float): Float = (depth / edgePx).coerceIn(0f, 1f)

/**
 * 드래그 범위의 끝점으로 삼을, 손가락 위치에 가장 가까운 행의 목록 키를 찾는다.
 * 손가락보다 위에서 시작하는 마지막 행을 고르고, 모든 행보다 위면 첫 행을 고른다 — 손가락이 목록 밖이나 행이 없는 빈 곳에 있어도 범위가 그 방향 끝에 보이는 행까지 따라오게 한다(#521).
 * @param visibleItems 화면에 배치된 행. 위에서 아래 순서다
 * @param viewportStartOffset 목록 위쪽 끝의 배치 좌표(위쪽 안쪽 여백만큼 음수)
 * @param y 목록 위쪽 끝 기준 손가락 Y 좌표
 * @return 행의 목록 키. 배치된 행이 없으면 null
 */
internal fun dragTargetKeyIn(visibleItems: List<LazyListItemInfo>, viewportStartOffset: Int, y: Float): Any? =
    (visibleItems.lastOrNull { it.offset - viewportStartOffset <= y } ?: visibleItems.firstOrNull())?.key

// 픽셀 Y 좌표에 있는 항목의 목록 키 반환 — 위치 번호는 마지막 배치 때의 목록 기준이라 지금 목록과 어긋날 수 있으므로 키로 돌려준다.
// 길게 누른 행을 찾는 데 쓰며, 행 위가 아니면 드래그를 시작하지 않도록 null을 돌려준다.
// viewportStartOffset 보정으로 contentPadding과 무관하게 동작
private fun LazyListState.itemKeyAtOffset(y: Float): Any? {
    val start = layoutInfo.viewportStartOffset
    return layoutInfo.visibleItemsInfo
        .firstOrNull { (it.offset - start) <= y && y < (it.offset - start) + it.size }
        ?.key
}
