package com.todokanai.composepracticenew.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.model.StorageHolderItem
import com.todokanai.composepracticenew.tools.independent.exit_td
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.RemoteStorageUseCase
import com.todokanai.composepracticenew.usecase.StorageVolumeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StorageViewModel @Inject constructor(
    private val storageVolumeUseCase: StorageVolumeUseCase,
    private val remoteStorageUseCase: RemoteStorageUseCase,
    private val fileNavigatorUseCase: FileNavigatorUseCase
) : ViewModel() {

    /** 스토리지 선택 화면에 필요한 UI 상태를 담는 클래스. */
    data class UiState(
        val storageList: List<StorageHolderItem> = emptyList(),
        val remoteStorageList: List<RemoteStorageItem> = emptyList()
    )

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

    /** 원격 스토리지에 접속하여 파일 탐색기를 해당 스토리지 루트로 이동시킨다. */
    fun setPath(item: RemoteStorageItem) {
        viewModelScope.launch {
            fileNavigatorUseCase.setPath(item)
        }
    }

    fun exit(activity: Activity) = exit_td(activity)
}
