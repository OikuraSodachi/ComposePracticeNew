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
import com.todokanai.composepracticenew.myobjects.Constants
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.data.R as DataR

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

            // name이 바뀔 때만 재계산 — icon 선택과 isAsyncImage 판별에 공유 사용
            val extension = remember(fileHolderItem.name) { fileHolderItem.name.substringAfterLast('.', "") }

            // fileHolderItem.isDirectory 또는 extension이 바뀔 때만 재선택
            val icon = remember(fileHolderItem.isDirectory, extension) {
                when {
                    fileHolderItem.isDirectory -> folderIcon
                    extension == "pdf" -> pdfIcon
                    else -> fileIcon
                }
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
