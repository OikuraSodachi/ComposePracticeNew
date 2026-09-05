package com.todokanai.composepracticenew.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.di.ApplicationScope
import com.todokanai.composepracticenew.model.ProgressStateModel
import com.todokanai.composepracticenew.ui.model.DirectoryItem
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.model.toModel
import com.todokanai.composepracticenew.myobjects.AppConstants
import com.todokanai.composepracticenew.myobjects.Constants
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_DOWNLOAD
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_UPLOAD
import com.todokanai.composepracticenew.operation.FtpDownloadOperation
import com.todokanai.composepracticenew.tools.MyNotification
import com.todokanai.composepracticenew.usecase.FileActionUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.FtpUseCase
import com.todokanai.composepracticenew.usecase.OpenFileUseCase
import com.todokanai.composepracticenew.usecase.ProgressUseCase
import com.todokanai.composepracticenew.usecase.SortModeUseCase
import com.todokanai.composepracticenew.variables.FileListSorter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FileListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileNavigatorUseCase: FileNavigatorUseCase,
    private val progressUseCase: ProgressUseCase,
    private val myNoti: MyNotification,
    private val openFileUseCase: OpenFileUseCase,
    private val ftpUseCase: FtpUseCase,
    private val sortModeUseCase: SortModeUseCase,
    private val fileActionUseCase: FileActionUseCase,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    /** 파일 목록 화면에 필요한 UI 상태를 담는 클래스. */
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

    /** 동시에 진행 중인 파일 작업들의 progress 상태. 작업 인스턴스 ID(instanceId)를 키로 사용한다. */
    val progressMap: StateFlow<Map<Int, ProgressStateModel>> = progressUseCase.progressMap
        .map { map -> map.mapValues { (_, state) -> state.toModel() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )

    private val _downloadError = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** 다운로드 실패 메시지 이벤트. UI에서 Toast 표시에 사용한다. */
    val downloadError: SharedFlow<String> = _downloadError.asSharedFlow()

    /** 로컬 파일 작업(복사·이동·압축 등) 실패 메시지 이벤트. UI에서 Toast 표시에 사용한다. */
    val operationError: SharedFlow<String> = progressUseCase.operationErrors
    /** 파일 작업 완료 메시지 이벤트. UI에서 Toast 표시에 사용한다. */
    val operationCompletion: SharedFlow<String> = progressUseCase.operationCompletions

    /** 현재 정렬 기준. DataStore에서 수집한다. */
    val sortMode: StateFlow<String> = sortModeUseCase.sortBy
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Constants.BY_DEFAULT
        )

    private val _errorMessage = MutableStateFlow<String?>(null)
    /** 폴더 생성 실패 시 표시할 오류 메시지. null이면 표시 없음. */
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** 오류 메시지를 소비한 뒤 초기화한다. */
    fun clearError() { _errorMessage.value = null }

    /** 현재 경로에 [name] 이름의 새 폴더를 생성한다. */
    fun newFolder(name: String) {
        val currentPath = fileNavigatorUseCase.currentPath.value ?: return
        viewModelScope.launch {
            fileActionUseCase.makeDirectory(currentPath, name).collect { state ->
                state.error?.let { _errorMessage.value = it }
            }
            fileNavigatorUseCase.refresh()
        }
    }

    /** 정렬 모드 선택 목록과 각 항목 선택 시 실행할 콜백을 반환한다. */
    fun sortModeCallbackList() = FileListSorter().getSortModeCallbackList { sortModeUseCase.saveSortBy(it) }

    /** 알림 클릭으로 다이얼로그를 다시 표시해야 할 때 설정되는 actionKey. null이면 신호 없음. */
    private val _showProgressDialogForKey = MutableStateFlow<Int?>(null)
    val showProgressDialogForKey: StateFlow<Int?> = _showProgressDialogForKey.asStateFlow()

    /** 알림 클릭으로 로컬 작업 진행률 다이얼로그를 강제 재표시해야 하는지 여부. */
    val showProgressDialog: StateFlow<Boolean> = _showProgressDialogForKey
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    private val _showRemoteProgressDialogForKey = MutableStateFlow<Int?>(null)
    /** 원격 전송(다운로드·업로드) 알림 클릭 시 REMOTE_FILE_LIST 화면의 진행률 다이얼로그를 다시 표시해야 하는지 여부. */
    val showRemoteProgressDialog: StateFlow<Boolean> = _showRemoteProgressDialogForKey
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    fun requestShowProgressDialog(actionKey: Int) {
        if (actionKey == ACTION_KEY_DOWNLOAD || actionKey == ACTION_KEY_UPLOAD) {
            _showRemoteProgressDialogForKey.value = actionKey
        } else {
            _showProgressDialogForKey.value = actionKey
        }
    }

    fun onRemoteProgressDialogShown() {
        _showRemoteProgressDialogForKey.value = null
    }

    fun onProgressDialogShown() {
        _showProgressDialogForKey.value = null
    }

    /** pendingList 중 현재 로컬 디렉터리에 이미 같은 이름으로 존재하는 파일 목록을 반환한다. */
    fun getDownloadConflicts(pendingList: List<FileHolderItem>): List<FileHolderItem> {
        val localFiles = uiState.value.fileHolderItemList
        return pendingList.filter { remote -> localFiles.any { local -> local.name == remote.name } }
    }

    /** 경로 breadcrumb에 표시할 디렉터리 트리. */
    val dirTree: StateFlow<List<DirectoryItem>> = fileNavigatorUseCase.dirTree
        .map { list -> list.map { DirectoryItem(name = it.name, path = it.path) } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** 주어진 절대 경로로 현재 디렉터리를 이동한다. */
    fun updateCurrentPath(path: String) {
        viewModelScope.launch {
            fileNavigatorUseCase.setPath(path)
        }
    }

    /** items를 현재 로컬 경로에 다운로드한다. */
    fun onDownload(items: List<FileHolderItem>) {
        val localPath = fileNavigatorUseCase.currentPath.value ?: return
        items.forEach { item ->
            val instanceId = item.path.hashCode()
            FtpDownloadOperation(
                remotePath = item.path,
                localDestPath = localPath,
                isDirectory = item.isDirectory,
                ftpUseCase = ftpUseCase,
                instanceId = instanceId,
                myNoti = myNoti,
                completionMessage = context.getString(R.string.noti_download_complete),
                onRefresh = { fileNavigatorUseCase.refresh() },
                onEmitCompletion = { msg -> progressUseCase.emitCompletion(msg) },
                onEmitError = { msg -> _downloadError.tryEmit(msg) },
                setProgress = { state -> progressUseCase.setProgressState(instanceId, state) },
                clearProgress = { progressUseCase.removeProgress(instanceId) }
            ).execute(appScope)
        }
    }

    /** pending 중 conflicts에 포함된 항목을 제외하고 다운로드한다. */
    fun onDownloadSkipping(pending: List<FileHolderItem>, conflicts: List<FileHolderItem>) {
        val skipPaths = conflicts.map { it.path }.toSet()
        onDownload(pending.filter { it.path !in skipPaths })
    }

    fun onItemClick(selected: FileHolderItem, selectMode: Int) {
        viewModelScope.launch {
            when (selectMode) {
                AppConstants.DEFAULT_MODE -> openFileUseCase.open(selected.toDomain())
                AppConstants.MULTI_SELECT_MODE -> { }
                else -> if (selected.isDirectory) openFileUseCase.open(selected.toDomain())
            }
        }
    }
}
