package com.todokanai.composepracticenew.compose.listview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.todokanai.composepracticenew.compose.holder.FileHolder
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.myobjects.Constants

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
    val selectedSet = remember(selectedList) { selectedList.toHashSet() }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
    ) {
        items(fileHolderItemList, key = { it.path }) { fileHolderItem ->

            val isSelected = fileHolderItem in selectedSet

            val onClick = remember(fileHolderItem, selectMode) {
                {
                    if (selectMode == Constants.MULTI_SELECT_MODE) {
                        if (fileHolderItem in selectedList) removeFromList(fileHolderItem)
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
                modifier = Modifier.combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
                file = fileHolderItem,
                isSelected = isSelected
            )
        }
    }
}
