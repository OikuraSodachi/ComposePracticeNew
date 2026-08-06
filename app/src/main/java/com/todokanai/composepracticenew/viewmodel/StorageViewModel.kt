package com.todokanai.composepracticenew.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.di.RemoteNavigator
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.model.StorageHolderItem
import com.todokanai.composepracticenew.tools.independent.exit_td
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.RemoteStorageUseCase
import com.todokanai.composepracticenew.usecase.StorageVolumeUseCase
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
    @RemoteNavigator private val remoteFileNavigatorUseCase: FileNavigatorUseCase
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

    val uiState: StateFlow<UiState> = combine(
        storageVolumeUseCase.storageList,
        remoteStorageUseCase.getAll()
    ) { storageList, remoteList ->
        UiState(storageList = storageList, remoteStorageList = remoteList)
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
            remoteStorageUseCase.update(item)
        }
    }

    /** 원격 스토리지 접속 정보를 삭제한다. */
    fun deleteRemoteStorage(item: RemoteStorageItem) {
        viewModelScope.launch {
            remoteStorageUseCase.delete(item)
        }
    }

    /** 원격 스토리지에 접속하여 파일 탐색기를 해당 스토리지 루트로 이동시킨다. 연결 성공 시 onConnected를 호출한다. */
    fun setPath(item: RemoteStorageItem, onConnected: () -> Unit) {
        viewModelScope.launch {
            _isConnecting.value = true
            val connected = remoteFileNavigatorUseCase.setPath(item)
            _isConnecting.value = false
            if (connected) onConnected() else _connectionFailed.tryEmit(Unit)
        }
    }

    fun exit(activity: Activity) = exit_td(activity)
}
