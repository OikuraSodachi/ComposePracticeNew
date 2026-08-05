package com.todokanai.composepracticenew.compose.frag

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.listview.FileListView
import com.todokanai.composepracticenew.myobjects.Constants
import com.todokanai.composepracticenew.viewmodel.RemoteFileListViewModel

/** 원격 스토리지의 파일 목록을 표시하는 화면. FileListFrag와 동일한 UI를 사용하며 파일 조작 기능은 제공하지 않는다. */
@Composable
fun RemoteFileListFrag(
    modifier: Modifier,
    viewModel: RemoteFileListViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

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
                text = stringResource(R.string.empty_directory)
            )
        } else {
            FileListView(
                modifier = Modifier.weight(1f),
                fileHolderItemList = uiState.value.fileHolderItemList,
                selectMode = Constants.DEFAULT_MODE,
                onItemClick = { viewModel.onItemClick(it) },
                onItemLongClick = {},
                addToList = {},
                removeFromList = {},
                clearList = {}
            )
        }
    }

    println("recomposition: RemoteFileListFrag")
}
