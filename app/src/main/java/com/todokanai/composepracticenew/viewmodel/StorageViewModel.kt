package com.todokanai.composepracticenew.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.di.RemoteNavigator
import com.todokanai.composepracticenew.ui.model.RemoteStorageItem
import com.todokanai.composepracticenew.ui.model.StorageHolderItem
import com.todokanai.composepracticenew.tools.independent.exit_td
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.RemoteStorageUseCase
import com.todokanai.composepracticenew.usecase.StorageVolumeUseCase
import com.todokanai.composepracticenew.service.FtpServiceController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StorageViewModel @Inject constructor(
    private val storageVolumeUseCase: StorageVolumeUseCase,
    private val remoteStorageUseCase: RemoteStorageUseCase,
    @RemoteNavigator private val remoteFileNavigatorUseCase: FileNavigatorUseCase,
    private val ftpServiceController: FtpServiceController
) : ViewModel() {

    /** 스토리지 선택 화면에 필요한 UI 상태를 담는 클래스. */
    data class UiState(
        val storageList: List<StorageHolderItem> = emptyList(),
        val remoteStorageList: List<RemoteStorageItem> = emptyList()
    )

    private val _isConnecting = MutableStateFlow(false)
    /** FTP 연결 진행 중 여부. */
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _connectionFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** FTP 연결 실패 이벤트. */
    val connectionFailed: SharedFlow<Unit> = _connectionFailed.asSharedFlow()

    private val _connectionSucceeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** FTP 연결 성공 이벤트. */
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

    /** FileListFrag의 초기 경로값 */
    fun setInitialPath(setPath: () -> Unit) {
        viewModelScope.launch {
            setPath()
        }
    }

    /** 원격 스토리지를 추가한다. */
    fun addRemoteStorage(name: String, address: String, port: Int, id: String, password: String) {
        viewModelScope.launch {
            remoteStorageUseCase.add(name, address, port, id, password)
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
            _isConnecting.value = true
            try {
                val connected = remoteFileNavigatorUseCase.setPath(item.toDomain())
                if (connected) {
                    remoteStorageUseCase.saveLastConnectedId(item.id)
                    ftpServiceController.start(item.name)
                    _connectionSucceeded.tryEmit(Unit)
                } else {
                    _connectionFailed.tryEmit(Unit)
                }
            } finally {
                _isConnecting.value = false
            }
        }
    }

    fun exit(activity: Activity) = exit_td(activity)
}
