package com.todokanai.composepracticenew.compose.frag

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todokanai.composepracticenew.R
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
    exitToRemoteFileFrag: () -> Unit,
    setInitialPath: (File) -> Unit,
    viewModel: StorageViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val isConnecting by viewModel.isConnecting.collectAsStateWithLifecycle()
    var showAddRemoteStorageDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<com.todokanai.composepracticenew.model.RemoteStorageItem?>(null) }

    LaunchedEffect(Unit) {
        viewModel.connectionFailed.collect {
            Toast.makeText(activity, activity.getString(R.string.toast_connection_failed), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.connectionSucceeded.collect {
            exitToRemoteFileFrag()
        }
    }

    if (showAddRemoteStorageDialog) {
        AddRemoteStorageDialog(
            onConfirm = { name, address, port, id, password -> viewModel.addRemoteStorage(name, address, port, id, password) },
            onCancel = { showAddRemoteStorageDialog = false }
        )
    }

    editingItem?.let { target ->
        AddRemoteStorageDialog(
            initialValue = target,
            onConfirm = { name, address, port, id, password ->
                viewModel.updateRemoteStorage(target.copy(name = name, address = address, port = port, userId = id, password = password))
            },
            onCancel = { editingItem = null }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                                viewModel.setInitialPath({ setInitialPath(File(storage.absolutePath)) })
                                exitStorageFrag()
                            },
                        storage = storage
                    )
                }
                items(uiState.value.remoteStorageList, key = { it.id }) { remote ->
                    RemoteStorageHolder(
                        modifier = Modifier.clickable {
                            viewModel.setPath(remote)
                        },
                        item = remote,
                        onEdit = { editingItem = remote },
                        onDelete = { viewModel.deleteRemoteStorage(remote) }
                    )
                }
            }
        }

        if (isConnecting) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }

}
