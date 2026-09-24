package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.model.ProgressStateModel
import com.todokanai.composepracticenew.model.toModel
import com.todokanai.composepracticenew.myobjects.AppConstants
import com.todokanai.composepracticenew.myobjects.Constants
import com.todokanai.composepracticenew.myobjects.OperationConstants
import com.todokanai.composepracticenew.tools.CompletionMessageProvider
import com.todokanai.composepracticenew.ui.model.DirectoryItem
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.usecase.FileActionUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.FileOperationUseCase
import com.todokanai.composepracticenew.usecase.OpenFileUseCase
import com.todokanai.composepracticenew.usecase.ProgressUseCase
import com.todokanai.composepracticenew.usecase.SortModeUseCase
import com.todokanai.composepracticenew.variables.FileListSorter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FileListViewModel @Inject constructor(
    private val fileNavigatorUseCase: FileNavigatorUseCase,
    private val progressUseCase: ProgressUseCase,
    private val openFileUseCase: OpenFileUseCase,
    private val sortModeUseCase: SortModeUseCase,
    private val fileActionUseCase: FileActionUseCase,
    private val fileOperationUseCase: FileOperationUseCase,
    private val messages: CompletionMessageProvider
) : ViewModel() {

    /** 파일 목록 화면에 필요한 UI 상태를 담는 클래스. */
    data class UiState(
        val fileHolderItemList: List<FileHolderItem> = emptyList()
    )

    val uiState: StateFlow<UiState> = fileNavigatorUseCase.fileList
        .map { list -> UiState(list.map { FileHolderItem.from(it) }) }
        .flowOn(Dispatchers.IO)
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
    val sortModeCallbackList = FileListSorter().getSortModeCallbackList { sortModeUseCase.saveSortBy(it) }

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
        if (OperationConstants.isRemoteOperation(actionKey)) {
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
            fileOperationUseCase.download(
                remotePath = item.path,
                localDestPath = localPath,
                isDirectory = item.isDirectory,
                completionMessage = messages.notiDownloadComplete,
                onRefresh = { fileNavigatorUseCase.refresh() },
                onError = { msg -> _downloadError.tryEmit(msg) }
            )
        }
    }

    /** pending 중 conflicts에 포함된 항목을 제외하고 다운로드한다. */
    fun onDownloadSkipping(pending: List<FileHolderItem>, conflicts: List<FileHolderItem>) {
        val skipPaths = conflicts.map { it.path }.toSet()
        onDownload(pending.filter { it.path !in skipPaths })
    }

    /** 상위 디렉터리로 이동한다. 루트이면 toStorageFrag를 호출한다. */
    fun onBackPressed(toStorageFrag: () -> Unit) {
        viewModelScope.launch {
            fileNavigatorUseCase.navigateBack(toStorageFrag)
        }
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

    /** selectMode 작업의 목적지 경로에 이미 같은 이름으로 존재하는 파일 목록을 반환한다. */
    fun getConflicts(selectedList: List<FileHolderItem>, selectMode: Int): List<FileHolderItem> {
        val destinationNames = uiState.value.fileHolderItemList.map { it.name }.toHashSet()
        return when (selectMode) {
            AppConstants.CONFIRM_MODE_COPY, AppConstants.CONFIRM_MODE_MOVE ->
                selectedList.filter { it.name in destinationNames }
            AppConstants.CONFIRM_MODE_UNZIP, AppConstants.CONFIRM_MODE_UNZIP_HERE ->
                selectedList.filter { it.name.substringBeforeLast('.') in destinationNames }
            else -> emptyList()
        }
    }

    /** 선택 목록에서 skipFiles를 제외한 대상에 대해 selectMode에 맞는 파일 작업을 실행한다. @param skipFiles 충돌로 건너뛸 파일 목록 */
    fun confirm(selectedList: List<FileHolderItem>, selectMode: Int, skipFiles: List<FileHolderItem> = emptyList()) {
        val currentPath = fileNavigatorUseCase.currentPath.value ?: return
        val conflictPaths = skipFiles.map { it.path }.toSet()
        val targets = selectedList.filterNot { it.path in conflictPaths }
        if (targets.isEmpty()) return
        val onRefresh: suspend () -> Unit = { fileNavigatorUseCase.refresh() }
        when (selectMode) {
            AppConstants.CONFIRM_MODE_COPY -> fileOperationUseCase.copy(
                files = targets.map { it.path },
                destPath = currentPath,
                completionMessage = messages.notiComplete,
                onRefresh = onRefresh
            )
            AppConstants.CONFIRM_MODE_MOVE -> fileOperationUseCase.move(
                files = targets.map { it.path },
                destPath = currentPath,
                completionMessage = messages.notiMoveComplete,
                onRefresh = onRefresh
            )
            AppConstants.CONFIRM_MODE_UNZIP -> fileOperationUseCase.unzip(
                zipFiles = targets.map { it.path },
                destPath = currentPath,
                unzipHere = false,
                completionMessage = messages.notiComplete,
                onRefresh = onRefresh
            )
            AppConstants.CONFIRM_MODE_UNZIP_HERE -> fileOperationUseCase.unzip(
                zipFiles = targets.map { it.path },
                destPath = currentPath,
                unzipHere = true,
                completionMessage = messages.notiComplete,
                onRefresh = onRefresh
            )
        }
    }

    /** selectedList의 파일들을 [name].zip으로 압축한다. */
    fun zip(selectedList: List<FileHolderItem>, name: String) {
        val parent = selectedList.firstOrNull()?.path?.let { File(it).parent } ?: return
        fileOperationUseCase.zip(
            files = selectedList.map { it.path },
            zipFilePath = "$parent/$name.zip",
            completionMessage = messages.notiComplete,
            onRefresh = { fileNavigatorUseCase.refresh() }
        )
    }

    /** [item]의 이름을 [name]으로 변경한다. */
    fun rename(item: FileHolderItem, name: String) {
        fileOperationUseCase.rename(
            path = item.path,
            newName = name,
            completionMessage = messages.notiComplete,
            onRefresh = { fileNavigatorUseCase.refresh() }
        )
    }

    /** selectedList의 파일들을 삭제한다. */
    fun delete(selectedList: List<FileHolderItem>) {
        fileOperationUseCase.delete(
            files = selectedList.map { it.path },
            completionMessage = messages.notiDeleteComplete,
            onRefresh = { fileNavigatorUseCase.refresh() }
        )
    }
}
