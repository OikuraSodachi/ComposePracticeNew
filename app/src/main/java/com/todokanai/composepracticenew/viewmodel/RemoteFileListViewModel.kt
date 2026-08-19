package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.di.ApplicationScope
import com.todokanai.composepracticenew.di.RemoteNavigator
import com.todokanai.composepracticenew.model.ProgressStateEntity
import com.todokanai.composepracticenew.model.toEntity
import com.todokanai.composepracticenew.service.FtpServiceController
import com.todokanai.composepracticenew.ui.model.DirectoryItem
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.FtpUseCase
import com.todokanai.composepracticenew.usecase.RemoteStorageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 원격 파일 목록 화면의 UI 상태, 네비게이션, 파일 조작(다운로드·업로드·이름변경·삭제·새폴더)을 관리하는 ViewModel. */
@HiltViewModel
class RemoteFileListViewModel @Inject constructor(
    @RemoteNavigator private val fileNavigatorUseCase: FileNavigatorUseCase,
    private val remoteStorageUseCase: RemoteStorageUseCase,
    private val ftpServiceController: FtpServiceController,
    private val ftpUseCase: FtpUseCase,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    private val _reconnectFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** 자동 재연결 실패 이벤트. */
    val reconnectFailed: SharedFlow<Unit> = _reconnectFailed.asSharedFlow()

    private val _transferProgress = MutableSharedFlow<ProgressStateEntity>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    /** 다운로드·업로드 진행 상태 이벤트. */
    val transferProgress: SharedFlow<ProgressStateEntity> = _transferProgress.asSharedFlow()


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

    /** pendingList 중 현재 원격 디렉터리에 이미 같은 이름으로 존재하는 파일 목록을 반환한다. */
    fun getUploadConflicts(pendingList: List<FileHolderItem>): List<FileHolderItem> {
        val remoteFiles = uiState.value.fileHolderItemList
        return pendingList.filter { local -> remoteFiles.any { remote -> remote.name == local.name } }
    }

    /**
     * item의 원격 파일을 localDestPath로 다운로드한다.
     * appScope에서 ftpUseCase.download()를 collect해 ProgressState를 처리한다.
     * @param item 다운로드할 원격 파일 항목
     * @param localDestPath 저장할 로컬 디렉터리의 절대 경로
     */
    fun onDownload(item: FileHolderItem, localDestPath: String) {
        appScope.launch {
            ftpUseCase.download(item.path, localDestPath)
                .catch { e -> _transferProgress.tryEmit(ProgressStateEntity(error = e.message)) }
                .collect { _transferProgress.tryEmit(it.toEntity()) }
        }
    }

    /**
     * localPath의 파일을 현재 원격 경로에 업로드한다.
     * currentPath를 remoteDestPath로 사용해 appScope에서 ftpUseCase.upload()를 collect한다.
     * @param localPath 업로드할 로컬 파일의 절대 경로
     */
    fun onUpload(localPath: String) {
        val remotePath = fileNavigatorUseCase.currentPath.value ?: return
        appScope.launch {
            ftpUseCase.upload(localPath, remotePath)
                .catch { e -> _transferProgress.tryEmit(ProgressStateEntity(error = e.message)) }
                .collect { _transferProgress.tryEmit(it.toEntity()) }
        }
    }

    /**
     * item을 newName으로 이름 변경한다.
     * ftpUseCase를 통해 FtpRepository.rename()을 호출하고 성공 시 목록을 갱신한다.
     * @param item 이름을 변경할 원격 파일 항목, @param newName 변경할 새 이름
     */
    fun onRename(item: FileHolderItem, newName: String) {
        appScope.launch {
            val success = ftpUseCase.rename(item.path, newName)
            if (success) fileNavigatorUseCase.refresh()
        }
    }

    /**
     * item을 원격 서버에서 삭제한다.
     * 파일이면 FtpRepository.deleteFile(), 디렉터리이면 FtpRepository.removeDirectory()를 호출한다.
     * @param item 삭제할 원격 파일 또는 디렉터리 항목
     */
    fun onDelete(item: FileHolderItem) {
        appScope.launch {
            val success = ftpUseCase.delete(item.path, item.isDirectory)
            if (success) fileNavigatorUseCase.refresh()
        }
    }

    /**
     * 현재 원격 경로 아래에 dirName 이름의 새 디렉터리를 생성한다.
     * FtpRepository.makeDirectory()를 호출하고 성공 시 목록을 갱신한다.
     * @param dirName 생성할 디렉터리 이름
     */
    fun onMakeDirectory(dirName: String) {
        val currentPath = fileNavigatorUseCase.currentPath.value ?: return
        appScope.launch {
            val success = ftpUseCase.makeDirectory(currentPath, dirName)
            if (success) fileNavigatorUseCase.refresh()
        }
    }
}
