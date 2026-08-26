package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.di.ApplicationScope
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.ProgressStateModel
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.model.toModel
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_DOWNLOAD
import com.todokanai.composepracticenew.myobjects.Constants.DEFAULT_MODE
import com.todokanai.composepracticenew.myobjects.Constants.MULTI_SELECT_MODE
import com.todokanai.composepracticenew.tools.MyNotification
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.FtpUseCase
import com.todokanai.composepracticenew.usecase.OpenFileUseCase
import com.todokanai.composepracticenew.usecase.ProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FileListViewModel @Inject constructor(
    private val fileNavigatorUseCase: FileNavigatorUseCase,
    private val progressUseCase: ProgressUseCase,
    private val myNoti: MyNotification,
    private val openFileUseCase: OpenFileUseCase,
    private val ftpUseCase: FtpUseCase,
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

    /** 알림 클릭으로 다이얼로그를 다시 표시해야 할 때 설정되는 actionKey. null이면 신호 없음. */
    private val _showProgressDialogForKey = MutableStateFlow<Int?>(null)
    val showProgressDialogForKey: StateFlow<Int?> = _showProgressDialogForKey.asStateFlow()

    fun requestShowProgressDialog(actionKey: Int) {
        _showProgressDialogForKey.value = actionKey
    }

    fun onProgressDialogShown() {
        _showProgressDialogForKey.value = null
    }

    /** pendingList 중 현재 로컬 디렉터리에 이미 같은 이름으로 존재하는 파일 목록을 반환한다. */
    fun getDownloadConflicts(pendingList: List<FileHolderItem>): List<FileHolderItem> {
        val localFiles = uiState.value.fileHolderItemList
        return pendingList.filter { remote -> localFiles.any { local -> local.name == remote.name } }
    }

    fun updateCurrentPath(file: File) {
        viewModelScope.launch {
            fileNavigatorUseCase.setPath(file.absolutePath)
        }
    }

    /** items를 현재 로컬 경로에 다운로드한다. 진행률은 ProgressTracker를 통해 표시된다. */
    fun onDownload(items: List<FileHolderItem>) {
        val localPath = fileNavigatorUseCase.currentPath.value ?: return
        items.forEach { item -> downloadSingle(item.path.hashCode(), ftpUseCase.download(item.path, localPath)) }
    }

    /** pending 중 conflicts에 포함된 항목을 제외하고 다운로드한다. */
    fun onDownloadSkipping(pending: List<FileHolderItem>, conflicts: List<FileHolderItem>) {
        val skipPaths = conflicts.map { it.path }.toSet()
        onDownload(pending.filter { it.path !in skipPaths })
    }

    /**
     * source Flow를 수집해 ProgressUseCase를 통해 진행률을 갱신하고, 완료 또는 에러 시 해당 키를 제거한다.
     * @param instanceId 진행률 맵에서 이 전송 인스턴스를 식별하는 키
     * @param source 수집할 다운로드 진행률 Flow
     */
    private fun downloadSingle(instanceId: Int, source: Flow<ProgressState>) {
        appScope.launch {
            source
                .onCompletion { progressUseCase.removeProgress(instanceId) }
                .catch { }
                .collect { state ->
                    val error = state.error
                    if (error != null) {
                        progressUseCase.removeProgress(instanceId)
                        _downloadError.tryEmit(error)
                    } else {
                        progressUseCase.setProgressState(instanceId, state.copy(actionKey = ACTION_KEY_DOWNLOAD))
                    }
                }
        }
    }

    fun onItemClick(selected: FileHolderItem, selectMode: Int) {
        viewModelScope.launch {
            when (selectMode) {
                DEFAULT_MODE -> openFileUseCase.open(selected.toDomain())
                MULTI_SELECT_MODE -> { }
                else -> if (selected.isDirectory) openFileUseCase.open(selected.toDomain())
            }
        }
    }

}
