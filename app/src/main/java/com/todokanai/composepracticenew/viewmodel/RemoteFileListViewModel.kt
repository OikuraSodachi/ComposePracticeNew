package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.di.RemoteNavigator
import com.todokanai.composepracticenew.service.FtpServiceController
import com.todokanai.composepracticenew.ui.model.DirectoryItem
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.RemoteStorageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 원격 파일 목록 화면의 UI 상태, 네비게이션, 파일 조작(다운로드·업로드·이름변경·삭제·새폴더)을 관리하는 ViewModel. */
@HiltViewModel
class RemoteFileListViewModel @Inject constructor(
    @RemoteNavigator private val fileNavigatorUseCase: FileNavigatorUseCase,
    private val remoteStorageUseCase: RemoteStorageUseCase,
    private val ftpServiceController: FtpServiceController
) : ViewModel() {

    private val _reconnectFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** 자동 재연결 실패 이벤트. */
    val reconnectFailed: SharedFlow<Unit> = _reconnectFailed.asSharedFlow()

    init {
        if (fileNavigatorUseCase.currentPath.value == null) {
            viewModelScope.launch { reconnectLast() }
        }
    }

    private suspend fun reconnectLast() {
        remoteStorageUseCase.getLastConnectedItem()?.let { item ->
            val connected = fileNavigatorUseCase.setPath(item)
            if (connected) {
                ftpServiceController.start(item.name)
            } else {
                _reconnectFailed.tryEmit(Unit)
            }
        }
    }

    /** 원격 파일 목록 화면에 필요한 UI 상태를 담는 클래스. */
    data class UiState(
        val fileHolderItemList: List<FileHolderItem> = emptyList()
    )

    val uiState: StateFlow<UiState> = fileNavigatorUseCase.fileList
        .map { list -> UiState(list.map { FileHolderItem.from(it) }) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState()
        )

    /** 원격 경로 breadcrumb 목록. */
    val dirTree: StateFlow<List<DirectoryItem>> = fileNavigatorUseCase.dirTree
        .map { list -> list.map { DirectoryItem(name = it.name, path = it.path) } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** breadcrumb 항목 클릭 시 해당 경로로 이동한다. */
    fun navigateToDir(entry: DirectoryItem) {
        viewModelScope.launch { fileNavigatorUseCase.setPath(entry.path) }
    }

    /** 항목 클릭 시 디렉터리이면 해당 경로로 이동하고, 파일이면 아무 동작도 하지 않는다. */
    fun onItemClick(item: FileHolderItem) {
        if (item.isDirectory) {
            viewModelScope.launch {
                fileNavigatorUseCase.setPath(item.path)
            }
        }
    }

    /** 상위 디렉터리로 이동한다. 루트이면 toStorageFrag를 호출한다. */
    fun onBackPressed(toStorageFrag: () -> Unit) {
        viewModelScope.launch {
            fileNavigatorUseCase.navigateBack(toStorageFrag)
        }
    }

    /** item의 원격 파일을 로컬 다운로드 경로로 저장한다. */
    fun onDownload(item: FileHolderItem) {} // stub — not yet implemented

    /** localPath의 파일을 현재 원격 경로에 업로드한다. */
    fun onUpload(localPath: String) {} // stub — not yet implemented

    /** item을 newName으로 이름 변경한다. */
    fun onRename(item: FileHolderItem, newName: String) {} // stub — not yet implemented

    /** item을 삭제한다. */
    fun onDelete(item: FileHolderItem) {} // stub — not yet implemented

    /** 현재 원격 경로 아래에 dirName 이름의 새 디렉토리를 생성한다. */
    fun onMakeDirectory(dirName: String) {} // stub — not yet implemented
}
