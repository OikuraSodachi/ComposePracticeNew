package com.todokanai.composepracticenew.compose.frag

import android.widget.Toast
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
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
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.StorageMenuButtons
import com.todokanai.composepracticenew.compose.dialog.AddRemoteStorageDialog
import com.todokanai.composepracticenew.compose.holder.RemoteStorageHolder
import com.todokanai.composepracticenew.compose.holder.StorageHolder
import com.todokanai.composepracticenew.ui.model.RemoteStorageItem
import com.todokanai.composepracticenew.service.FtpForegroundService
import com.todokanai.composepracticenew.viewmodel.StorageViewModel
import android.content.Intent
import java.io.File

@Composable
fun StorageFrag(
    modifier: Modifier,
    exitStorageFrag: () -> Unit,
    exitToRemoteFileFrag: () -> Unit,
    setInitialPath: (File) -> Unit,
    viewModel: StorageViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val isConnecting by viewModel.isConnecting.collectAsStateWithLifecycle()
    var showAddRemoteStorageDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<RemoteStorageItem?>(null) }

    LaunchedEffect(Unit) {
        launch {
            viewModel.connectionFailed.collect {
                Toast.makeText(context, context.getString(R.string.toast_connection_failed), Toast.LENGTH_SHORT).show()
            }
        }
        launch {
            viewModel.connectionSucceeded.collect {
                exitToRemoteFileFrag()
            }
        }
    }

    if (showAddRemoteStorageDialog) {
        AddRemoteStorageDialog(
            onConfirm = { name, address, port, id, password, encoding ->
                viewModel.addRemoteStorage(name, address, port, id, password, encoding)
            },
            onCancel = { showAddRemoteStorageDialog = false }
        )
    }

    editingItem?.let { target ->
        AddRemoteStorageDialog(
            initialValue = target,
            onConfirm = { name, address, port, id, password, encoding ->
                viewModel.updateRemoteStorage(target.copy(name = name, address = address, port = port, userId = id, password = password, encoding = encoding))
            },
            onCancel = { editingItem = null }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column {
            StorageMenuButtons(
                modifier = Modifier,
                onAddRemoteStorage = { showAddRemoteStorageDialog = true },
                exit = { viewModel.exit(context, Intent(context, FtpForegroundService::class.java)) }
            )

            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
            ) {
                items(uiState.value.storageList, key = { it.absolutePath }) { storage ->
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
