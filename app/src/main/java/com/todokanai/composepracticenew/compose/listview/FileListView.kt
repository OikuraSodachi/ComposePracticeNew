package com.todokanai.composepracticenew.compose.listview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.todokanai.composepracticenew.compose.holder.FileHolder
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.myobjects.Constants

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListView(
    modifier: Modifier,
    fileHolderItemList: List<FileHolderItem>,
    selectMode: Int,
    onItemClick: (FileHolderItem) -> Unit,
    onItemLongClick: () -> Unit,
    addToList: (FileHolderItem) -> Unit,
    removeFromList: (FileHolderItem) -> Unit,
    clearList: () -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
    ) {
        items(fileHolderItemList.size) { index ->
            val fileHolderItem = fileHolderItemList[index]

            var isSelected by remember { mutableStateOf(false) }

            FileHolder(
                modifier = Modifier
                    .combinedClickable(
                        onClick = {
                            if (selectMode == Constants.MULTI_SELECT_MODE) {
                                if (isSelected) {
                                    isSelected = false
                                    removeFromList(fileHolderItem)
                                } else {
                                    isSelected = true
                                    addToList(fileHolderItem)
                                }
                            } else {
                                onItemClick(fileHolderItem)
                            }
                        },
                        onLongClick = {
                            if (selectMode == Constants.DEFAULT_MODE) {
                                clearList()
                                isSelected = true
                                addToList(fileHolderItem)
                                onItemLongClick()
                            }
                        }
                    ),
                file = fileHolderItem,
                isSelected = isSelected
            )

            LaunchedEffect(selectMode) {
                if (selectMode != Constants.MULTI_SELECT_MODE) {
                    isSelected = false
                }
            }
        }
    }
}
