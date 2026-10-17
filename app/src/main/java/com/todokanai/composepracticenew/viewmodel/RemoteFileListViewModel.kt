package com.todokanai.composepracticenew.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.di.RemoteNavigator
import com.todokanai.composepracticenew.model.ProgressStateModel
import com.todokanai.composepracticenew.model.toModel
import com.todokanai.composepracticenew.myobjects.Constants
import com.todokanai.composepracticenew.service.ConnectionServiceController
import com.todokanai.composepracticenew.tools.OperationMessages
import com.todokanai.composepracticenew.ui.model.DirectoryItem
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.ProgressUseCase
import com.todokanai.composepracticenew.usecase.RemoteConnectionResilienceUseCase
import com.todokanai.composepracticenew.usecase.RemoteFileOperationUseCase
import com.todokanai.composepracticenew.usecase.SortModeUseCase
import com.todokanai.composepracticenew.variables.FileListSorter
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

/**
 * 원격 파일 목록 화면의 UI 상태, 네비게이션, 연결 유지, 원격 파일 조작(이름변경·삭제·새폴더)을 관리하는 ViewModel.
 * 다운로드·업로드 실행은 FileTransferCoordinatorViewModel이 담당한다.
 * @param fileNavigatorUseCase 원격 디렉터리 탐색 상태를 제공하는 UseCase
 * @param connectionServiceController 연결 유지 ForegroundService를 제어하는 컨트롤러
 * @param progressUseCase 진행 중인 작업 상태를 제공하는 UseCase
 * @param sortModeUseCase 정렬 기준을 조회·저장하는 UseCase
 * @param remoteFileOperationUseCase 원격 파일 조작을 실행하는 UseCase
 * @param connectionResilienceUseCase 연결 끊김 감지·자동 재연결 UseCase
 * @param messages 작업 완료 메시지 제공자
 * @param sorter 정렬 모드 선택 목록을 만드는 FileListSorter
 */
@HiltViewModel
class RemoteFileListViewModel @Inject constructor(
    @RemoteNavigator private val fileNavigatorUseCase: FileNavigatorUseCase,
    private val connectionServiceController: ConnectionServiceController,
    progressUseCase: ProgressUseCase,
    private val sortModeUseCase: SortModeUseCase,
    private val remoteFileOperationUseCase: RemoteFileOperationUseCase,
    private val connectionResilienceUseCase: RemoteConnectionResilienceUseCase,
    private val messages: OperationMessages,
    sorter: FileListSorter,
) : ViewModel() {

    private val _reconnectFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** 자동 재연결 실패 이벤트. */
    val reconnectFailed: SharedFlow<Unit> = _reconnectFailed.asSharedFlow()

    private val _connectionLost = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** 서버 측 연결 종료(idle timeout 등)로 원격 연결이 끊겼을 때 발행된다. */
    val connectionLost: SharedFlow<Unit> = _connectionLost.asSharedFlow()

    /** 진행 중인 원격 전송 작업의 진행률 맵. ProgressDialog 표시에 사용한다. 원격 작업만 남겨 로컬 IO 항목을 제외한다. */
    val remoteProgressMap: StateFlow<Map<Int, ProgressStateModel>> = progressUseCase.progressMap
        .map { map ->
            map.filter { (_, state) -> state.operationType?.isRemote == true }
                .mapValues { (_, state) -> state.toModel() }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )

    init {
        val path = fileNavigatorUseCase.currentPath.value
        Log.d(TAG, "init — currentPath=$path")
        if (path == null) {
            viewModelScope.launch {
                val item = connectionResilienceUseCase.reconnectLast()
                if (item != null) {
                    Log.d(TAG, "reconnectLast — 재연결 성공: ${item.name}")
                    connectionServiceController.start(item.name)
                } else {
                    Log.w(TAG, "reconnectLast — 재연결 실패")
                    _reconnectFailed.tryEmit(Unit)
                }
            }
        }
        viewModelScope.launch {
            connectionResilienceUseCase.connectionDropped.collect {
                Log.w(TAG, "연결 끊김 감지 — 서비스 중지 및 이벤트 발행")
                connectionServiceController.stop()
                _connectionLost.emit(Unit)
            }
        }
    }

    /**
     * 원격 파일 목록 화면에 필요한 UI 상태를 담는 클래스.
     * @param fileHolderItemList 현재 원격 디렉터리의 파일 목록
     */
    data class UiState(
        val fileHolderItemList: List<FileHolderItem> = emptyList()
    )

    val uiState: StateFlow<UiState> = fileNavigatorUseCase.fileList
        .map { list -> UiState(list.map { FileHolderItem.from(it, isLocalFile = false) }) }
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

    /** 현재 정렬 기준. DataStore에서 수집한다. */
    val sortMode: StateFlow<String> = sortModeUseCase.sortBy
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Constants.BY_DEFAULT
        )

    /** 정렬 모드 선택 목록과 각 항목 선택 시 실행할 콜백을 반환한다. */
    val sortModeCallbackList = sorter.getSortModeCallbackList { sortModeUseCase.saveSortBy(it) }

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

    /**
     * item을 newName으로 이름 변경한다.
     * @param item 이름을 변경할 원격 파일 항목, @param newName 변경할 새 이름
     */
    fun onRename(item: FileHolderItem, newName: String) {
        remoteFileOperationUseCase.renameRemote(
            path = item.path,
            newName = newName,
            completionMessage = messages.notiComplete,
            errorMessage = "rename 실패: ${item.path.substringAfterLast("/")}",
            onRefresh = { fileNavigatorUseCase.refresh() }
        )
    }

    /**
     * item을 원격 서버에서 삭제한다.
     * @param item 삭제할 원격 파일 또는 디렉터리 항목
     */
    fun onDelete(item: FileHolderItem) {
        remoteFileOperationUseCase.deleteRemote(
            path = item.path,
            isDirectory = item.isDirectory,
            completionMessage = messages.notiDeleteComplete,
            errorMessage = "삭제 실패: ${item.path.substringAfterLast("/")}",
            onRefresh = { fileNavigatorUseCase.refresh() }
        )
    }

    /**
     * 현재 원격 경로 아래에 dirName 이름의 새 디렉터리를 생성한다.
     * @param dirName 생성할 디렉터리 이름
     */
    fun onMakeDirectory(dirName: String) {
        val currentPath = fileNavigatorUseCase.currentPath.value ?: return
        remoteFileOperationUseCase.makeDirectoryRemote(
            parentPath = currentPath,
            dirName = dirName,
            completionMessage = messages.notiComplete,
            errorMessage = "디렉터리 생성 실패: $dirName",
            onRefresh = { fileNavigatorUseCase.refresh() }
        )
    }

    /** 진행 중인 원격 전송(다운로드·업로드) 작업을 취소한다. */
    fun onCancelTransfer() {
        // TODO: 작업 취소 연결 예정
    }

    companion object {
        private const val TAG = "RemoteFileListVM"
    }
}
