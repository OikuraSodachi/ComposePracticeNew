package com.todokanai.composepracticenew.compose.frag

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todokanai.composepracticenew.compose.listview.FileListView
import com.todokanai.composepracticenew.viewmodel.BottomButtonsViewModel
import com.todokanai.composepracticenew.viewmodel.FileListViewModel
import java.io.File

@Composable
fun FileListFrag(
    modifier: Modifier,
    viewModel: FileListViewModel,
    bViewModel:BottomButtonsViewModel
) {
    val context = LocalContext.current
    val selectedList = remember{mutableListOf<File>()}
    val fileHolderItemList = viewModel.fileHolderItemList.collectAsStateWithLifecycle()

    val progressFlow = viewModel.progressState

    val progressCollected = viewModel.progressState.collectAsStateWithLifecycle()
    var progressDialog by remember{ mutableStateOf(true) }


    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        if (fileHolderItemList.value.isEmpty()) {
            /** 비어있는 경로일 경우 */
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .wrapContentSize(),
                text = "Empty Directory"
            )
        } else {
            /*
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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
                                            selectedList.remove(file)
                                        } else {
                                            isSelected = true
                                            selectedList.add(file)
                                        }
                                     //   println("recom selectedList: $selectedList")
                                    } else {
                                        viewModel.onItemClick(
                                            context, file
                                        )
                                    }
                                },
                                onLongClick = {
                                    if (selectMode.value == Constants.DEFAULT_MODE) {
                                        selectedList.clear()
                                        isSelected = true
                                        selectedList.add(file)
                                        viewModel.onItemLongClick()
                                    }
                                }
                            ),
                        file = fileHolderItem,
                        isSelected = isSelected
                    )

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


             */
            FileListView(
                modifier = Modifier
                    .weight(1f),
                fileHolderItemListFlow = viewModel.fileHolderItemList,
                selectModeFlow = viewModel.selectMode,
                onItemClick = {viewModel.onItemClick(context,it)},
                onItemLongClick = {viewModel.onItemLongClick()},
                addToList = {viewModel.addToList(it)},
                removeFromList = {viewModel.removeFromList(it)},
                clearList = {viewModel.clearList()}
            )
        }

        BottomButtonsFrag(
            modifier = Modifier,
            selectedList = selectedList,
            viewModel = bViewModel
        )
    }

    /*
    if (progressDialog&& progressCollected.value.actionKey !=null) {
        ProgressDialog(
            modifier = Modifier,
            state = progressFlow,
            onConfirm = {progressDialog = false}
        )
    }

     */
    println("recomposition: FileListFrag")
}