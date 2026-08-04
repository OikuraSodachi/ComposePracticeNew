package com.todokanai.composepracticenew.compose.frag

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todokanai.composepracticenew.compose.StorageMenuButtons
import com.todokanai.composepracticenew.compose.activity.MainActivity
import com.todokanai.composepracticenew.compose.dialog.AddRemoteStorageDialog
import com.todokanai.composepracticenew.compose.holder.RemoteStorageHolder
import com.todokanai.composepracticenew.compose.holder.StorageHolder
import com.todokanai.composepracticenew.viewmodel.StorageViewModel
import java.io.File

@Composable
fun StorageFrag(
    modifier: Modifier,
    activity: MainActivity,
    exitStorageFrag: () -> Unit,
    setInitialPath: (File) -> Unit,
    viewModel: StorageViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    var showAddRemoteStorageDialog by remember { mutableStateOf(false) }

    if (showAddRemoteStorageDialog) {
        AddRemoteStorageDialog(
            onConfirm = { name, address, port, id, password -> viewModel.addRemoteStorage(name, address, port, id, password) },
            onCancel = { showAddRemoteStorageDialog = false }
        )
    }

    Column {
        StorageMenuButtons(
            modifier = Modifier,
            onAddRemoteStorage = { showAddRemoteStorageDialog = true },
            exit = { viewModel.exit(activity) }
        )

        LazyColumn(
            modifier = modifier
                .fillMaxSize()
        ) {
            items(uiState.value.storageList.size) {
                val storage = uiState.value.storageList[it]
                StorageHolder(
                    modifier = Modifier
                        .clickable {
                            viewModel.setInitialPath({ setInitialPath(storage.storage) })
                            exitStorageFrag()
                        },
                    storage = storage
                )
            }
            items(uiState.value.remoteStorageList, key = { it.id }) { remote ->
                RemoteStorageHolder(
                    modifier = Modifier.clickable {
                        viewModel.setPath(remote)
                        exitStorageFrag()
                    },
                    item = remote
                )
            }
        }
    }

    println("Recomposition: StorageFrag")
}
