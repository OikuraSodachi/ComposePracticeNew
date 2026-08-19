package com.todokanai.composepracticenew.compose.listview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
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
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
    ) {
        items(fileHolderItemList, key = { it.path }) { fileHolderItem ->

            val isSelected = fileHolderItem in selectedList

            FileHolder(
                modifier = Modifier
                    .combinedClickable(
                        onClick = {
                            if (selectMode == Constants.MULTI_SELECT_MODE) {
                                if (isSelected) {
                                    removeFromList(fileHolderItem)
                                } else {
                                    addToList(fileHolderItem)
                                }
                            } else {
                                onItemClick(fileHolderItem)
                            }
                        },
                        onLongClick = {
                            if (selectMode == Constants.DEFAULT_MODE) {
                                clearList()
                                addToList(fileHolderItem)
                                onItemLongClick(fileHolderItem)
                            }
                        }
                    ),
                file = fileHolderItem,
                isSelected = isSelected
            )
        }
    }
}
