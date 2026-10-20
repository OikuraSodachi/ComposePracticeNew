package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    /** 원격 연결 진행 중 여부. */
    val isConnecting: StateFlow<Boolean> = connectionStateUseCase.isConnecting

    private val _connectionFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** 원격 연결 실패 이벤트. */
    val connectionFailed: SharedFlow<Unit> = _connectionFailed.asSharedFlow()

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

    /** 기존 원격 스토리지 접속 정보를 수정한다. */
    fun updateRemoteStorage(item: RemoteStorageItem) {
        viewModelScope.launch {
            remoteStorageUseCase.update(item.toDomain())
        }
    }

    /** 원격 스토리지 접속 정보를 삭제한다. */
    fun deleteRemoteStorage(item: RemoteStorageItem) {
        viewModelScope.launch {
            remoteStorageUseCase.delete(item.toDomain())
            remoteStorageUseCase.clearLastConnectedIfMatches(item.id)
        }
    }

    /** 원격 스토리지에 접속하여 파일 탐색기를 해당 스토리지 루트로 이동시킨다. 연결 성공 시 ForegroundService를 시작하고 connectionSucceeded를 emit한다. */
    fun setPath(item: RemoteStorageItem) {
        viewModelScope.launch {
            val connected = remoteConnectionUseCase.connectAndNavigate(item.toDomain())
            if (connected) {
                remoteStorageUseCase.saveLastConnectedId(item.id)
                connectionServiceController.start(item.name)
                _connectionSucceeded.tryEmit(Unit)
            } else {
                _connectionFailed.tryEmit(Unit)
            }
        }
    }
}
