package com.todokanai.composepracticenew.compose.frag

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import com.todokanai.composepracticenew.compose.PendingRequestEffect
import com.todokanai.composepracticenew.compose.StorageMenuButtons
import com.todokanai.composepracticenew.compose.dialog.AddRemoteStorageDialog
import com.todokanai.composepracticenew.compose.dialog.BinaryTypeRefusedDialog
import com.todokanai.composepracticenew.compose.dialog.DeleteDialog
import com.todokanai.composepracticenew.compose.dialog.InvalidPortDialog
import com.todokanai.composepracticenew.compose.dialog.UnreadableServerDialog
import com.todokanai.composepracticenew.compose.holder.RemoteStorageHolder
import com.todokanai.composepracticenew.compose.holder.StorageHolder
import com.todokanai.composepracticenew.model.RemoteStorageInputError
import com.todokanai.composepracticenew.ui.model.ConnectFailureDialog
import com.todokanai.composepracticenew.ui.model.RemoteStorageInput
import com.todokanai.composepracticenew.ui.model.RemoteStorageItem
import com.todokanai.composepracticenew.ui.model.StorageExit
import com.todokanai.composepracticenew.viewmodel.StorageViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * 고른 저장소를 연 뒤의 목록 화면 이동 요청을 이 화면이 입력을 받는 동안(RESUMED) 처리한다(#534).
 * 백그라운드에 있는 동안 연결에 성공해도 요청이 남아, 화면이 다시 RESUMED가 되면 넘어간다. 연결 실패는 화면 호스트가 앱 전역에서 알린다.
 *
 * @param exitRequest 넘어갈 목록 화면. 없으면 null
 * @param onExitHandled 넘어갔다고 알리는 콜백
 * @param exitToLocalList 로컬 목록 화면으로 넘어가는 콜백
 * @param exitToRemoteList 원격 목록 화면으로 넘어가는 콜백
 */
@Composable
private fun StorageExitEffect(
    exitRequest: StateFlow<StorageExit?>,
    onExitHandled: (StorageExit) -> Unit,
    exitToLocalList: () -> Unit,
    exitToRemoteList: () -> Unit,
) {
    // RESUMED까지 기다린다 — 떠나는 중에 넘어가지 않고, 넘어가는 중에 누른 저장소는 클릭 시점에 버린다(dropUnlessResumed)
    PendingRequestEffect(exitRequest, Lifecycle.State.RESUMED, onExitHandled) { exit ->
        when (exit) {
            StorageExit.LOCAL_LIST -> exitToLocalList()
            StorageExit.REMOTE_LIST -> exitToRemoteList()
        }
    }
}

/**
 * dialogState 플래그에 따라 원격 스토리지 추가·편집·삭제 다이얼로그를 조건부 렌더링한다.
 *
 * @param dialogState 다이얼로그 open/close 상태 홀더
 * @param onAdd 추가 확인 시 위반한 규칙이 없는 접속 정보를 전달하는 콜백
 * @param onUpdate 편집 확인 시 위반한 규칙이 없는 접속 정보를 전달하는 콜백
 * @param remoteStorageList deletingId로 삭제 대상을 찾을 원격 스토리지 목록
 * @param onDelete 삭제 확인 시 대상 RemoteStorageItem을 전달하는 콜백
 * @param violations 추가·편집 입력값이 위반한 규칙을 반환하는 함수. 통과하면 빈 집합
 */
@Composable
private fun StorageDialogHost(
    dialogState: StorageDialogState,
    remoteStorageList: List<RemoteStorageItem>,
    onAdd: (RemoteStorageInput) -> Unit,
    onUpdate: (RemoteStorageInput) -> Unit,
    onDelete: (RemoteStorageItem) -> Unit,
    violations: (RemoteStorageInput) -> Set<RemoteStorageInputError>,
) {
    if (dialogState.showAddDialog) {
        AddRemoteStorageDialog(
            onConfirm = onAdd,
            onDismiss = { dialogState.dismissAdd() },
            violations = violations
        )
    }
    remoteStorageList.findById(dialogState.editingId)?.let { target ->
        AddRemoteStorageDialog(
            initialValue = target,
            onConfirm = onUpdate,
            onDismiss = { dialogState.dismissEdit() },
            violations = violations
        )
    }
    remoteStorageList.findById(dialogState.deletingId)?.let { target ->
        DeleteDialog(
            onConfirm = { onDelete(target) },
            onDismiss = { dialogState.dismissDelete() }
        )
    }
}

/** 다이얼로그 상태가 보관한 id로 대상 원격 스토리지를 찾는다. id가 null이거나 목록에 없으면 null. */
private fun List<RemoteStorageItem>.findById(id: Long?): RemoteStorageItem? =
    id?.let { target -> find { it.id == target } }

/**
 * 스토리지 선택 화면. 로컬/원격 스토리지 목록을 표시하고 탐색 진입점을 제공한다.
 * @param modifier 레이아웃 수정자
 * @param exitStorageFrag 고른 로컬 스토리지 루트로 이동한 뒤 파일 목록 화면으로 넘어가는 콜백
 * @param exitToRemoteFileFrag 원격 스토리지 연결 성공 후 원격 파일 목록 화면으로 이동하는 콜백
 * @param onExit 앱을 완전히 종료한다(원격 연결 종료를 요청한 뒤 태스크 제거).
 * @param viewModel 스토리지 선택 화면의 ViewModel
 */
@Composable
fun StorageFrag(
    modifier: Modifier,
    exitStorageFrag: () -> Unit,
    exitToRemoteFileFrag: () -> Unit,
    onExit: () -> Unit,
    viewModel: StorageViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val isConnecting by viewModel.isConnecting.collectAsStateWithLifecycle()
    val connectFailureDialog by viewModel.connectFailureDialog.collectAsStateWithLifecycle()
    // rememberSaveable로 기억 — 다이얼로그의 대상이 저장된 서버 id라 회전·프로세스 종료 뒤에도 열린 다이얼로그를 복원한다(#273)
    val dialogState = rememberStorageDialogState()

    StorageExitEffect(
        exitRequest = viewModel.exitRequest,
        onExitHandled = viewModel::onExitHandled,
        exitToLocalList = exitStorageFrag,
        exitToRemoteList = exitToRemoteFileFrag,
    )
    StorageDialogHost(
        dialogState = dialogState,
        remoteStorageList = uiState.value.remoteStorageList,
        onAdd    = { viewModel.addRemoteStorage(it) },
        onUpdate = { viewModel.updateRemoteStorage(it) },
        onDelete = { viewModel.deleteRemoteStorage(it) },
        violations = viewModel::remoteStorageInputViolations,
    )
    when (connectFailureDialog) {
        ConnectFailureDialog.UNREADABLE_SERVER -> UnreadableServerDialog(onDismiss = viewModel::dismissConnectFailureDialog)
        ConnectFailureDialog.BINARY_TYPE_REFUSED -> BinaryTypeRefusedDialog(onDismiss = viewModel::dismissConnectFailureDialog)
        ConnectFailureDialog.INVALID_PORT -> InvalidPortDialog(onDismiss = viewModel::dismissConnectFailureDialog)
        null -> Unit
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column {
            StorageMenuButtons(
                modifier = Modifier,
                onAddRemoteStorage = { dialogState.openAdd() },
                exit = onExit
            )
            // 연결하는 동안에는 목록 입력(로컬 열기, 원격 연결·수정·삭제)을 막는다 — 진행 중인 연결과 다른 연결·수정·삭제가 엇갈려
            // 삭제한 서버로 연결이 이어지거나 삭제된 서버 id가 마지막 연결 서버로 저장되는 것을 막는다
            val listEnabled = !isConnecting
            // 저장소 열기는 이 화면이 RESUMED일 때만 받는다(#542) — 목록 화면으로 넘어가는 중에 누른 저장소가 경로·연결을 바꾸고,
            // 그 이동 요청이 남아 저장소 화면으로 돌아오자마자 다시 넘어가지 않도록
            LazyColumn(modifier = modifier.fillMaxSize()) {
                items(uiState.value.storageList, key = { it.absolutePath }) { storage ->
                    StorageHolder(
                        modifier = Modifier.clickable(enabled = listEnabled, onClick = dropUnlessResumed { viewModel.openLocalStorage(storage.absolutePath) }),
                        storage = storage
                    )
                }
                items(uiState.value.remoteStorageList, key = { it.id }) { remote ->
                    RemoteStorageHolder(
                        modifier = Modifier.clickable(enabled = listEnabled, onClick = dropUnlessResumed { viewModel.setPath(remote) }),
                        item = remote,
                        enabled = listEnabled,
                        onEdit   = { dialogState.openEdit(remote) },
                        onDelete = { dialogState.openDelete(remote) }
                    )
                }
            }
        }
        if (isConnecting) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}
