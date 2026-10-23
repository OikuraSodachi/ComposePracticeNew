package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.RemoteStorageInputError
import com.todokanai.composepracticenew.service.ConnectionServiceController
import com.todokanai.composepracticenew.ui.model.RemoteStorageItem
import com.todokanai.composepracticenew.ui.model.StorageHolderItem
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.RemoteConnectionStateUseCase
import com.todokanai.composepracticenew.usecase.RemoteConnectionUseCase
import com.todokanai.composepracticenew.usecase.RemoteStorageUseCase
import com.todokanai.composepracticenew.usecase.StorageVolumeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 스토리지 선택 화면의 로컬·원격 스토리지 목록과 진입(로컬 경로 설정·원격 연결)을 담당하는 ViewModel.
 * @param storageVolumeUseCase 로컬 스토리지 볼륨 목록을 제공하는 UseCase
 * @param remoteStorageUseCase 원격 접속 정보를 관리하는 UseCase
 * @param remoteConnectionUseCase 원격 연결과 루트 이동을 수행하는 UseCase
 * @param connectionServiceController 연결 유지 ForegroundService를 제어하는 컨트롤러
 * @param connectionStateUseCase 원격 연결 진행 상태를 제공하는 UseCase
 * @param localNavigator 선택한 로컬 스토리지 루트로 이동하는 UseCase
 */
@HiltViewModel
class StorageViewModel @Inject constructor(
    storageVolumeUseCase: StorageVolumeUseCase,
    private val remoteStorageUseCase: RemoteStorageUseCase,
    private val remoteConnectionUseCase: RemoteConnectionUseCase,
    private val connectionServiceController: ConnectionServiceController,
    connectionStateUseCase: RemoteConnectionStateUseCase,
    private val localNavigator: FileNavigatorUseCase
) : ViewModel() {

    /**
     * 스토리지 선택 화면에 필요한 UI 상태를 담는 클래스.
     * @param storageList 로컬 스토리지 목록
     * @param remoteStorageList 원격 스토리지 목록
     */
    data class UiState(
        val storageList: List<StorageHolderItem> = emptyList(),
        val remoteStorageList: List<RemoteStorageItem> = emptyList()
    )

    /**
     * [setPath]가 시작한 연결 작업(연결·루트 이동·마지막 연결 저장)이 끝나지 않았으면 true.
     * FTP 세션의 연결 중 상태는 로그인 직후 false가 되어 루트 이동·저장 구간을 덮지 못하므로, 작업 전체를 이 값으로 따로 표시한다.
     */
    private val connectInProgress = MutableStateFlow(false)

    /** 원격 연결 진행 중 여부. 세션이 연결 중이거나 [setPath]의 연결 작업이 끝나지 않았으면 true이며, 이 동안 목록 입력을 막는다. */
    val isConnecting: StateFlow<Boolean> = combine(connectionStateUseCase.isConnecting, connectInProgress) { session, task -> session || task }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 연결 실패를 이유에 맞는 알림(이벤트·다이얼로그)으로 바꾼다. */
    private val connectFailureNotice = ConnectFailureNotice()

    /** 다시 시도하면 될 수 있는 원격 연결 실패 이벤트. */
    val connectionFailed: SharedFlow<Unit> = connectFailureNotice.connectionFailed

    /** 서버의 파일 목록을 읽을 수 없어 연결을 종료했음을 알리는 다이얼로그를 보여야 하면 true. */
    val unreadableServer: StateFlow<Boolean> = connectFailureNotice.unreadableServer

    /** 서버의 파일 목록을 읽을 수 없다는 다이얼로그를 닫는다. */
    fun dismissUnreadableServer() = connectFailureNotice.dismissUnreadableServer()

    private val _connectionSucceeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** 원격 연결 성공 이벤트. */
    val connectionSucceeded: SharedFlow<Unit> = _connectionSucceeded.asSharedFlow()

    val uiState: StateFlow<UiState> = combine(
        storageVolumeUseCase.storageList,
        remoteStorageUseCase.getAll()
    ) { storageList, remoteList ->
        UiState(
            storageList = storageList.map { StorageHolderItem.from(it) },
            remoteStorageList = remoteList.map { RemoteStorageItem.from(it) }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState()
    )

    /** 선택한 로컬 스토리지 루트([path])로 파일 탐색기의 현재 경로를 설정한다. */
    fun openLocalStorage(path: String) {
        viewModelScope.launch {
            localNavigator.setPath(path)
        }
    }

    /** 원격 스토리지 추가·수정 입력값을 검증해 위반한 규칙의 집합을 반환한다. 비어 있으면 유효한 입력이다. */
    fun validateRemoteStorageInput(name: String, address: String, port: String): Set<RemoteStorageInputError> =
        remoteStorageUseCase.validateInput(name, address, port)

    /** 원격 스토리지를 추가한다. */
    fun addRemoteStorage(name: String, address: String, port: Int, id: String, password: String, encoding: String) {
        viewModelScope.launch {
            remoteStorageUseCase.add(name, address, port, id, password, encoding)
        }
    }

    /** 기존 원격 스토리지 접속 정보를 수정한다. 연결 작업이 진행 중이면 무시한다. */
    fun updateRemoteStorage(item: RemoteStorageItem) {
        if (connectInProgress.value) return
        viewModelScope.launch {
            remoteStorageUseCase.update(item.toDomain())
        }
    }

    /** 원격 스토리지 접속 정보를 삭제한다. 연결 작업이 진행 중이면 무시한다 — 삭제한 서버로 연결이 이어지거나 그 id가 마지막 연결 서버로 저장되지 않게 한다. */
    fun deleteRemoteStorage(item: RemoteStorageItem) {
        if (connectInProgress.value) return
        viewModelScope.launch {
            remoteStorageUseCase.delete(item.toDomain())
            remoteStorageUseCase.clearLastConnectedIfMatches(item.id)
        }
    }

    /**
     * 원격 스토리지에 접속하여 파일 탐색기를 해당 스토리지 루트로 이동시킨다. 연결 성공 시 ForegroundService를 시작하고 connectionSucceeded를 emit한다.
     * 실패하면 이유에 맞게 [connectionFailed]를 emit하거나 [unreadableServer]를 true로 만든다.
     * 이미 연결 작업이 진행 중이면 무시한다. 작업이 끝날 때까지 [isConnecting]이 true로 유지된다.
     */
    fun setPath(item: RemoteStorageItem) {
        if (connectInProgress.value) return
        // launch 전에 설정 — 같은 프레임의 연속 입력도 위 확인에서 걸러진다
        connectInProgress.value = true
        viewModelScope.launch {
            try {
                when (val result = remoteConnectionUseCase.connectAndNavigate(item.toDomain())) {
                    ConnectResult.Connected -> {
                        remoteStorageUseCase.saveLastConnectedId(item.id)
                        connectionServiceController.start(item.name)
                        _connectionSucceeded.tryEmit(Unit)
                    }
                    is ConnectResult.Failed -> connectFailureNotice.report(result.reason)
                }
            } finally {
                connectInProgress.value = false
            }
        }
    }
}
