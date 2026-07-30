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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todokanai.composepracticenew.compose.holder.FileHolder
import com.todokanai.composepracticenew.data.dataclass.FileHolderItem
import com.todokanai.composepracticenew.myobjects.Constants
import kotlinx.coroutines.flow.StateFlow
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListView(
    modifier: Modifier,
    fileHolderItemListFlow: StateFlow<List<FileHolderItem>>,
    selectMode: Int,
    onItemClick: (File) -> Unit,
    onItemLongClick: () -> Unit,
    addToList: (File) -> Unit,
    removeFromList: (File) -> Unit,
    clearList: () -> Unit
) {
    val fileHolderItemList = fileHolderItemListFlow.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
    ) {
        items(fileHolderItemList.value.size) { index ->
            val fileHolderItem = fileHolderItemList.value[index]
            val file = fileHolderItem.file

            var isSelected by remember { mutableStateOf(false) }

            FileHolder(
                modifier = Modifier
                    .combinedClickable(
                        onClick = {
                            if (selectMode == Constants.MULTI_SELECT_MODE) {
                                if (isSelected) {
                                    isSelected = false
                                    removeFromList(file)
                                } else {
                                    isSelected = true
                                    addToList(file)
                                }
                            } else {
                                onItemClick(file)
                            }
                        },
                        onLongClick = {
                            if (selectMode == Constants.DEFAULT_MODE) {
                                clearList()
                                isSelected = true
                                addToList(file)
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
