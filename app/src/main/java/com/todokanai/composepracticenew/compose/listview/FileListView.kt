package com.todokanai.composepracticenew.compose.listview

import androidx.annotation.DrawableRes
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todokanai.composepracticenew.compose.holder.FileHolder
import com.todokanai.composepracticenew.compose.util.verticalScrollbar
import com.todokanai.composepracticenew.ui.model.ChildCountInfo
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.FileType
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.viewmodel.ChildCountSource
import com.todokanai.composepracticenew.R

/** 스크롤바 터치 영역 너비 — Material 최소 터치 타겟(48dp), thumb 시각 너비(8dp)와 별개 */
private val ScrollbarTouchTargetWidth = 48.dp

/** 항목 종류별 placeholder 아이콘 리소스. */
@DrawableRes
private fun FileType.iconRes(): Int = when (this) {
    FileType.Folder -> R.drawable.ic_baseline_folder_24
    FileType.Image -> R.drawable.ic_file_image
    FileType.Video -> R.drawable.ic_file_video
    FileType.Audio -> R.drawable.ic_file_audio
    FileType.Document -> R.drawable.ic_file_document
    FileType.Pdf -> R.drawable.ic_pdf
    FileType.Archive -> R.drawable.ic_file_archive
    FileType.Apk -> R.drawable.ic_file_apk
    FileType.Other -> R.drawable.ic_baseline_insert_drive_file_24
}

/**
 * 파일 목록을 스크롤바와 함께 표시하고, longPress 드래그 다중 선택을 지원한다.
 * @param selectedList 선택 목록을 반환하는 함수 — 항목별 선택 여부를 derivedStateOf로 구독해, 선택이 바뀐 행만 리컴포지션한다
 * @param childCounts [fileHolderItemList]의 폴더별 하위 항목 수. 폴더 행이 화면에 있는 동안에만 센다. null이면 세지 않는다(원격 목록)
 */
@Composable
fun FileListView(
    modifier: Modifier,
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
    // items{} 밖에서 종류별로 한 번씩 호출 — 행마다 Painter를 만들지 않고 모든 행이 공유한다
    val typePainters = FileType.entries.map { painterResource(it.iconRes()) }
    // Painter 인스턴스들을 키로 — painterResource는 리컴포지션마다 같은 Painter를 돌려주므로 묶음도 같은 인스턴스로 유지되어,
    // 행(FileHolder)이 Strong Skipping의 인스턴스 비교로 생략된다. 테마·밀도 변경으로 Painter가 바뀔 때만 다시 묶는다
    val typeIcons = remember(*typePainters.toTypedArray()) { FileType.entries.zip(typePainters).toMap() }

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
