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
import com.todokanai.composepracticenew.viewmodel.StorageViewModel
import java.io.File
import kotlinx.coroutines.launch

// TODO(#143-review-4): RemoteFileListFrag.RemoteFileListConnectionEffects와 구조 동일 — Flow를 직접 받는 TwoFlowEffects 공통 함수로 추출 고려해볼 것
/**
 * 원격 스토리지 연결 결과 Flow를 수집하고 부수 효과를 실행한다.
 *
 * @param viewModel 연결 결과 Flow를 제공하는 ViewModel
 * @param onConnectionFailed 연결 실패 시 실행할 콜백
 * @param onConnectionSucceeded 연결 성공 시 실행할 콜백
 */
@Composable
private fun StorageConnectionEffects(
    viewModel: StorageViewModel,
    onConnectionFailed: () -> Unit,
    onConnectionSucceeded: () -> Unit,
) {
    LaunchedEffect(Unit) {
        launch { viewModel.connectionFailed.collect    { onConnectionFailed()    } }
        launch { viewModel.connectionSucceeded.collect { onConnectionSucceeded() } }
    }
}

/**
 * dialogState 플래그에 따라 원격 스토리지 추가·편집 다이얼로그를 조건부 렌더링한다.
 *
 * @param dialogState 다이얼로그 open/close 상태 홀더
 * @param onAdd 추가 확인 시 실행할 콜백
 * @param onUpdate 편집 확인 시 수정된 RemoteStorageItem을 전달하는 콜백
 */
@Composable
private fun StorageDialogHost(
    dialogState: StorageDialogState,
    onAdd: (String, String, Int, String, String, String) -> Unit,
    onUpdate: (RemoteStorageItem) -> Unit,
) {
    if (dialogState.showAddDialog) {
        AddRemoteStorageDialog(
            onConfirm = { name, address, port, id, password, encoding ->
                onAdd(name, address, port, id, password, encoding)
            },
            onCancel = { dialogState.dismissAdd() }
        )
    }
    dialogState.editingItem?.let { target ->
        AddRemoteStorageDialog(
            initialValue = target,
            onConfirm = { name, address, port, id, password, encoding ->
                onUpdate(target.copy(name = name, address = address, port = port, userId = id, password = password, encoding = encoding))
            },
            onCancel = { dialogState.dismissEdit() }
        )
    }
}

/**
 * 스토리지 선택 화면. 로컬/원격 스토리지 목록을 표시하고 탐색 진입점을 제공한다.
 * @param modifier 레이아웃 수정자
 * @param exitStorageFrag 로컬 스토리지 선택 후 파일 목록 화면으로 이동하는 콜백
 * @param exitToRemoteFileFrag 원격 스토리지 연결 성공 후 원격 파일 목록 화면으로 이동하는 콜백
 * @param setInitialPath 선택한 로컬 스토리지의 루트 경로를 설정하는 콜백
 * @param onExit 앱을 완전히 종료한다(FTP 서비스 중단 후 태스크 제거).
 * @param viewModel 스토리지 선택 화면의 ViewModel
 */
@Composable
fun StorageFrag(
    modifier: Modifier,
    exitStorageFrag: () -> Unit,
    exitToRemoteFileFrag: () -> Unit,
    setInitialPath: (File) -> Unit,
    onExit: () -> Unit,
    viewModel: StorageViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val isConnecting by viewModel.isConnecting.collectAsStateWithLifecycle()
    val dialogState = rememberStorageDialogState()

    StorageConnectionEffects(
        viewModel = viewModel,
        onConnectionFailed    = { Toast.makeText(context, R.string.toast_connection_failed, Toast.LENGTH_SHORT).show() },
        onConnectionSucceeded = exitToRemoteFileFrag,
    )
    StorageDialogHost(
        dialogState = dialogState,
        onAdd    = { name, address, port, id, password, encoding ->
            viewModel.addRemoteStorage(name, address, port, id, password, encoding)
        },
        onUpdate = { viewModel.updateRemoteStorage(it) },
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column {
            StorageMenuButtons(
                modifier = Modifier,
                onAddRemoteStorage = { dialogState.openAdd() },
                exit = onExit
            )
            LazyColumn(modifier = modifier.fillMaxSize()) {
                items(uiState.value.storageList, key = { it.absolutePath }) { storage ->
                    StorageHolder(
                        modifier = Modifier.clickable {
                            viewModel.setInitialPath({ setInitialPath(File(storage.absolutePath)) })
                            exitStorageFrag()
                        },
                        storage = storage
                    )
                }
                items(uiState.value.remoteStorageList, key = { it.id }) { remote ->
                    RemoteStorageHolder(
                        modifier = Modifier.clickable { viewModel.setPath(remote) },
                        item = remote,
                        onEdit   = { dialogState.openEdit(remote) },
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
