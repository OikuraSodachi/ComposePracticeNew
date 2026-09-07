package com.todokanai.composepracticenew.compose.listview

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.todokanai.composepracticenew.compose.holder.FileHolder
import com.todokanai.composepracticenew.myobjects.AppConstants
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.data.R as DataR

@Composable
fun FileListView(
    modifier: Modifier,
    fileHolderItemList: List<FileHolderItem>,
    selectedPaths: Set<String>,
    selectMode: Int,
    onItemClick: (FileHolderItem) -> Unit,
    onItemLongClick: (FileHolderItem) -> Unit,
    addToList: (FileHolderItem) -> Unit,
    removeFromList: (FileHolderItem) -> Unit,
    clearList: () -> Unit
) {
    // items{} 밖에서 1회 생성 — 스크롤 시 painterResource가 item 수만큼 호출되는 것을 방지
    val folderIcon = painterResource(DataR.drawable.ic_baseline_folder_24)
    val fileIcon = painterResource(DataR.drawable.ic_baseline_insert_drive_file_24)
    val pdfIcon = painterResource(DataR.drawable.ic_pdf)

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
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

            val onLongClick = remember(fileHolderItem, selectMode) {
                {
                    if (selectMode == AppConstants.DEFAULT_MODE) {
                        clearList()
                        addToList(fileHolderItem)
                        onItemLongClick(fileHolderItem)
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
                onLongClick = onLongClick
            )
        }
    }
}
