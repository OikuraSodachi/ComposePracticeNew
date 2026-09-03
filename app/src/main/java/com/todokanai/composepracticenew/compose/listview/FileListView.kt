package com.todokanai.composepracticenew.compose.listview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.todokanai.composepracticenew.compose.holder.FileHolder
import com.todokanai.composepracticenew.myobjects.Constants
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.data.R as DataR

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListView(
    modifier: Modifier,
    fileHolderItemList: List<FileHolderItem>,
    selectedList: List<FileHolderItem>,
    selectMode: Int,
    onItemClick: (FileHolderItem) -> Unit,
    onItemLongClick: (FileHolderItem) -> Unit,
    addToList: (FileHolderItem) -> Unit,
    removeFromList: (FileHolderItem) -> Unit,
    clearList: () -> Unit
) {
    val selectedPaths = remember(selectedList) { selectedList.mapTo(HashSet()) { it.path } }

    val folderIcon = painterResource(DataR.drawable.ic_baseline_folder_24)
    val fileIcon = painterResource(DataR.drawable.ic_baseline_insert_drive_file_24)
    val pdfIcon = painterResource(DataR.drawable.ic_pdf)

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
    ) {
        items(fileHolderItemList, key = { it.path }) { fileHolderItem ->

            val isSelected = fileHolderItem.path in selectedPaths

            // name이 바뀔 때만 재계산 — icon 선택과 isAsyncImage 판별에 공유 사용
            val extension = remember(fileHolderItem.name) { fileHolderItem.name.substringAfterLast('.', "") }

            val icon = when {
                fileHolderItem.isDirectory -> folderIcon
                extension == "pdf" -> pdfIcon
                else -> fileIcon
            }

            val onClick = remember(fileHolderItem, selectMode, isSelected) {
                {
                    if (selectMode == Constants.MULTI_SELECT_MODE) {
                        if (isSelected) removeFromList(fileHolderItem)
                        else addToList(fileHolderItem)
                    } else {
                        onItemClick(fileHolderItem)
                    }
                }
            }

            val onLongClick = remember(fileHolderItem, selectMode) {
                {
                    if (selectMode == Constants.DEFAULT_MODE) {
                        clearList()
                        addToList(fileHolderItem)
                        onItemLongClick(fileHolderItem)
                    }
                }
            }

            // onClick/onLongClick 참조가 바뀔 때만 Modifier 재생성 — 상위 리컴포지션 시 FileHolder 강제 리컴포지션 방지
            val clickableModifier = remember(onClick, onLongClick) {
                Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
            }

            FileHolder(
                modifier = clickableModifier,
                file = fileHolderItem,
                isSelected = isSelected,
                icon = icon,
                isAsyncImage = extension == "jpg"
            )
        }
    }
}
