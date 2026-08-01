package com.todokanai.composepracticenew.compose.frag

import androidx.activity.compose.BackHandler
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todokanai.composepracticenew.compose.listview.BottomButtonListView
import com.todokanai.composepracticenew.compose.listview.FileListView
import com.todokanai.composepracticenew.myobjects.Constants
import com.todokanai.composepracticenew.viewmodel.FileListViewModel
import java.io.File

@Composable
fun FileListFrag(
    modifier: Modifier,
    viewModel: FileListViewModel
) {
    var selectedList by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectMode by remember { mutableStateOf(Constants.DEFAULT_MODE) }
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = selectMode != Constants.DEFAULT_MODE) {
        selectMode = Constants.DEFAULT_MODE
        selectedList = emptyList()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        if (uiState.value.fileHolderItemList.isEmpty()) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .wrapContentSize(),
                text = "Empty Directory"
            )
        } else {
            FileListView(
                modifier = Modifier
                    .weight(1f),
                fileHolderItemList = uiState.value.fileHolderItemList,
                selectMode = selectMode,
                onItemClick = { viewModel.onItemClick(it, selectMode) },
                onItemLongClick = { selectMode = Constants.MULTI_SELECT_MODE },
                addToList = { selectedList = selectedList + it },
                removeFromList = { selectedList = selectedList - it },
                clearList = { selectedList = emptyList() }
            )
        }

        BottomButtonListView(
            modifier = Modifier,
            selectMode = selectMode,
            onSelectModeChange = { selectMode = it },
            onClearSelection = { selectedList = emptyList() },
            selectedList = selectedList
        )
    }

    println("recomposition: FileListFrag")
}
