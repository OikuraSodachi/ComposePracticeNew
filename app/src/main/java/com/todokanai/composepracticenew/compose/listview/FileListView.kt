package com.todokanai.composepracticenew.compose.listview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.asLiveData
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
    selectModeFlow:StateFlow<Int>,
    onItemClick:(File)->Unit,
    onItemLongClick:()->Unit,
    addToList:(File)->Unit,
    removeFromList:(File)->Unit,
    clearList:()->Unit

){
   // val selectedList = remember{mutableListOf<File>()}
    val lifeCycleOwner = LocalLifecycleOwner.current
    val fileHolderItemList = fileHolderItemListFlow.collectAsStateWithLifecycle()
    val selectMode = selectModeFlow.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
           // .weight(1f)
    ) {
        items(fileHolderItemList.value.size) { index ->
            val fileHolderItem = fileHolderItemList.value[index]
            val file= fileHolderItem.file

            var isSelected by remember { mutableStateOf(false) }

            FileHolder(
                modifier = Modifier
                    .combinedClickable(
                        onClick = {
                            if (selectMode.value == Constants.MULTI_SELECT_MODE) {
                                if (isSelected) {
                                    isSelected = false
                                   // selectedList.remove(file)
                                    removeFromList(file)
                                } else {
                                    isSelected = true
                                  //  selectedList.add(file)
                                    addToList(file)
                                }

                                //   println("recom selectedList: $selectedList")
                            } else {
                                //viewModel.onItemClick(context, file)
                                onItemClick(file)
                            }
                        },
                        onLongClick = {
                            if (selectMode.value == Constants.DEFAULT_MODE) {
                                //selectedList.clear()
                                clearList()
                                isSelected = true
                                //selectedList.add(file)
                                addToList(file)
                               // viewModel.onItemLongClick()
                                onItemLongClick()
                            }
                        }
                    ),
                file = fileHolderItem,
                isSelected = isSelected
            )

            /** Default Mode로 변경시 isSelected 정보 리셋 부분 */

            /** Default Mode로 변경시 isSelected 정보 리셋 부분 */
            SideEffect {
                selectModeFlow.asLiveData().observe(lifeCycleOwner) { mode ->
                    if (mode != Constants.MULTI_SELECT_MODE) {
                        isSelected = false
                    }
                }
            }
        }
    }
}