package com.todokanai.composepracticenew.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.di.RemoteNavigator
import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.model.ProgressStateModel
import com.todokanai.composepracticenew.model.toModel
import com.todokanai.composepracticenew.model.SortMode
import com.todokanai.composepracticenew.service.ConnectionServiceController
import com.todokanai.composepracticenew.tools.OperationMessages
import com.todokanai.composepracticenew.ui.model.DirectoryItem
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.usecase.FileNameUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.ProgressUseCase
import com.todokanai.composepracticenew.usecase.RemoteConnectionResilienceUseCase
import com.todokanai.composepracticenew.usecase.RemoteFileOperationUseCase
import com.todokanai.composepracticenew.usecase.SortModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOn
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
 * @param fileNameUseCase 원격 새 폴더·이름 변경의 이름 규칙을 검증하는 UseCase
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
    private val fileNameUseCase: FileNameUseCase,
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
        .flowOn(Dispatchers.Default) // 크기 문자열 포맷을 메인 스레드 밖에서 수행
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
        .map { list -> UiState(FileHolderItem.fromList(list, isLocalFile = false)) }
        .flowOn(Dispatchers.Default) // 항목 수에 비례하는 날짜·크기 포맷을 메인 스레드 밖에서 수행
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
    val sortMode: StateFlow<SortMode> = sortModeUseCase.sortBy
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SortMode.BY_DEFAULT
        )

    /** 정렬 다이얼로그에서 선택된 정렬 기준을 저장한다. */
    fun onSortModeSelected(mode: SortMode) {
        sortModeUseCase.saveSortBy(mode)
    }

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

    /** 원격 새 폴더·이름 변경에 쓸 [name]을 검증해 위반한 규칙 집합을 반환한다. 비어 있으면 유효하다. */
    fun validateEntryName(name: String): Set<FileNameError> = fileNameUseCase.validate(name)

    /**
     * item을 newName으로 이름 변경한다. 규칙을 위반한 이름이면 아무것도 하지 않는다.
     * @param item 이름을 변경할 원격 파일 항목, @param newName 변경할 새 이름
     */
    fun onRename(item: FileHolderItem, newName: String) {
        if (validateEntryName(newName).isNotEmpty()) return
        remoteFileOperationUseCase.renameRemote(
            path = item.path,
            newName = newName,
            completionMessage = messages.notiComplete,
            errorMessage = "rename 실패: ${item.path.substringAfterLast("/")}",
            onRefresh = { fileNavigatorUseCase.refresh() }
        )
    }

    /**
     * items를 원격 서버에서 하나의 작업으로 삭제한다.
     * @param items 삭제할 원격 파일 또는 디렉터리 항목
     */
    fun onDelete(items: List<FileHolderItem>) {
        if (items.isEmpty()) return
        remoteFileOperationUseCase.deleteRemote(
            targets = items.associate { it.path to it.isDirectory },
            completionMessage = messages.notiDeleteComplete,
            errorMessage = { path -> "삭제 실패: ${path.substringAfterLast("/")}" },
            onRefresh = { fileNavigatorUseCase.refresh() }
        )
    }

    /**
     * 현재 원격 경로 아래에 dirName 이름의 새 디렉터리를 생성한다. 규칙을 위반한 이름이면 아무것도 하지 않는다.
     * @param dirName 생성할 디렉터리 이름
     */
    fun onMakeDirectory(dirName: String) {
        if (validateEntryName(dirName).isNotEmpty()) return
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
