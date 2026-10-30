package com.todokanai.composepracticenew.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.di.RemoteNavigator
import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.model.ReconnectResult
import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.model.RenameConflict
import com.todokanai.composepracticenew.model.SortMode
import com.todokanai.composepracticenew.service.ConnectionServiceController
import com.todokanai.composepracticenew.tools.OperationMessages
import com.todokanai.composepracticenew.ui.model.DirectoryItem
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.ListingUiState
import com.todokanai.composepracticenew.ui.model.ListingUiStateMapper
import com.todokanai.composepracticenew.ui.model.PendingRename
import com.todokanai.composepracticenew.usecase.ConflictCheckUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.RemoteConnectionResilienceUseCase
import com.todokanai.composepracticenew.usecase.RemoteConnectionStateUseCase
import com.todokanai.composepracticenew.usecase.RemoteFileOperationUseCase
import com.todokanai.composepracticenew.usecase.SortModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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
 * @param sortModeUseCase 정렬 기준을 조회·저장하는 UseCase
 * @param remoteFileOperationUseCase 원격 파일 조작을 실행하는 UseCase
 * @param connectionResilienceUseCase 마지막 서버 자동 재연결 UseCase
 * @param connectionStateUseCase 원격 연결 상태(끊김 포함)를 제공하는 UseCase
 * @param conflictCheckUseCase 이름 변경 시 같은 이름 항목과의 겹침을 판정하는 UseCase
 * @param messages 작업 완료 메시지 제공자
 */
@HiltViewModel
class RemoteFileListViewModel @Inject constructor(
    @RemoteNavigator private val fileNavigatorUseCase: FileNavigatorUseCase,
    private val connectionServiceController: ConnectionServiceController,
    private val sortModeUseCase: SortModeUseCase,
    private val remoteFileOperationUseCase: RemoteFileOperationUseCase,
    private val connectionResilienceUseCase: RemoteConnectionResilienceUseCase,
    connectionStateUseCase: RemoteConnectionStateUseCase,
    private val conflictCheckUseCase: ConflictCheckUseCase,
    private val messages: OperationMessages,
) : ViewModel() {

    /** 자동 재연결 실패를 이유에 맞는 알림(이벤트·다이얼로그)으로 바꾼다. */
    private val reconnectFailureNotice = ConnectFailureNotice()

    /** 다시 시도하면 될 수 있는 자동 재연결 실패 이벤트. */
    val reconnectFailed: SharedFlow<Unit> = reconnectFailureNotice.connectionFailed

    /** 자동 재연결한 서버의 파일 목록을 읽을 수 없어 연결을 종료했음을 알리는 다이얼로그를 보여야 하면 true. */
    val unreadableServer: StateFlow<Boolean> = reconnectFailureNotice.unreadableServer

    /** 서버의 파일 목록을 읽을 수 없다는 다이얼로그를 닫는다. */
    fun dismissUnreadableServer() = reconnectFailureNotice.dismissUnreadableServer()

    /**
     * 연결된 세션이 끊겨(서버 측 종료·idle timeout 등) 이 화면이 보여줄 원격 세션이 없으면 true.
     * 끊김을 상태로 보므로, 이 화면을 떠나 있는 동안 끊긴 경우에도 돌아왔을 때 true다.
     */
    val sessionLost: StateFlow<Boolean> = connectionStateUseCase.connectionState
        .map(::isLost)
        .stateIn(viewModelScope, SharingStarted.Eagerly, isLost(connectionStateUseCase.connectionState.value))

    init {
        val path = fileNavigatorUseCase.currentPath.value
        Log.d(TAG, "init — currentPath=$path")
        if (path == null) {
            viewModelScope.launch {
                when (val result = connectionResilienceUseCase.reconnectLast()) {
                    is ReconnectResult.Connected -> {
                        Log.d(TAG, "reconnectLast — 재연결 성공: ${result.item.name}")
                        connectionServiceController.start(result.item.name)
                    }
                    ReconnectResult.NoSavedServer -> {
                        Log.w(TAG, "reconnectLast — 재연결할 서버 없음")
                        reconnectFailureNotice.notifyConnectionFailed()
                    }
                    is ReconnectResult.Failed -> {
                        Log.w(TAG, "reconnectLast — 재연결 실패: ${result.reason}")
                        reconnectFailureNotice.report(result.reason)
                    }
                }
            }
        }
    }

    /** [state]가 연결된 세션이 끊긴 상태인지 판별한다. */
    private fun isLost(state: RemoteConnectionState): Boolean = state == RemoteConnectionState.LOST

    /**
     * 원격 파일 목록 화면에 필요한 UI 상태를 담는 클래스.
     * @param listing 현재 원격 디렉터리의 목록 상태(조회 중·완료·실패)
     */
    data class UiState(
        val listing: ListingUiState = ListingUiState.NoPath
    )

    val uiState: StateFlow<UiState> = flow {
        // 수집마다 변환기를 새로 만든다 — 변환기는 직전 변환 결과를 기억하므로 하나의 수집 코루틴 안에서만 써야 한다
        val mapper = ListingUiStateMapper(isLocalFile = false)
        emitAll(fileNavigatorUseCase.listing.map { listing -> UiState(mapper.map(listing)) })
    }
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

    /** 현재 원격 디렉터리의 목록을 다시 조회한다. 목록 조회 실패 화면의 다시 시도에 쓴다. */
    fun retryListing() = fileNavigatorUseCase.refresh()

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

    /**
     * 상위 디렉터리로 이동한다. 루트이면 leaveScreen을 호출한다.
     * 화면을 떠난 뒤 어디로 가는지(저장소 또는 로컬 목록)는 이 화면에 들어온 경로가 만든 백스택이 정한다.
     */
    fun onBackPressed(leaveScreen: () -> Unit) {
        viewModelScope.launch {
            fileNavigatorUseCase.navigateBack(leaveScreen)
        }
    }

    /** 원격 새 폴더·이름 변경에 쓸 [name]을 검증해 위반한 규칙 집합을 반환한다. 비어 있으면 유효하다. 실행 시점의 강제는 UseCase가 한다. */
    fun validateEntryName(name: String): Set<FileNameError> = remoteFileOperationUseCase.validateEntryName(name)

    /** 이름 변경의 "충돌 판정 → 즉시 실행 / 덮어쓰기 확인 대기" 흐름을 관리한다. */
    private val renameGate = RenameConfirmGate()
    /** 같은 이름의 항목이 있어 덮어쓰기 확인을 기다리는 원격 이름 변경. */
    val pendingRename: StateFlow<PendingRename?> = renameGate.pending

    /**
     * [item]을 [name]으로 바꿀 때의 위반 규칙 집합을 반환한다. 이름 규칙에 더해, 같은 폴더에 종류가 다른 같은 이름 항목이 있으면
     * [FileNameError.EXISTS_AS_OTHER_TYPE]을 포함한다. 같은 종류의 겹침은 오류가 아니라 확인 대상이다.
     * @param item 이름을 바꿀 원격 항목
     * @param name 사용자가 입력한 새 이름
     * @return 위반한 규칙의 집합. 비어 있으면 확인 버튼을 누를 수 있다
     */
    fun validateRename(item: FileHolderItem, name: String): Set<FileNameError> {
        val errors = remoteFileOperationUseCase.validateEntryName(name)
        val conflict = conflictCheckUseCase.previewRenameConflict(
            targetPath = item.path,
            targetIsDirectory = item.isDirectory,
            newName = name,
            folder = fileNavigatorUseCase,
            ignoreCase = REMOTE_IGNORE_CASE,
        )
        return if (conflict == RenameConflict.TYPE_MISMATCH) errors + FileNameError.EXISTS_AS_OTHER_TYPE else errors
    }

    /**
     * item을 newName으로 이름 변경한다. 같은 이름의 파일·폴더가 있으면 실행하지 않고 [pendingRename]으로 덮어쓰기 확인을 요청한다.
     * 확인 후에도 RNTO만 보내며, 덮어쓸지는 서버 동작에 따른다. 규칙을 위반한 이름이면 UseCase가 거부하고 오류를 알린다.
     * 폴더 목록을 확인하지 못했으면 겹침을 판정할 수 없으므로 실행하지 않는다(거부 알림은 ConflictCheckUseCase가 한다).
     * @param item 이름을 변경할 원격 파일 항목, @param newName 변경할 새 이름
     */
    fun onRename(item: FileHolderItem, newName: String) {
        val conflict = renameConflictOf(item, newName) ?: return
        renameGate.request(item, newName, conflict) { _ ->
            remoteFileOperationUseCase.renameRemote(
                path = item.path,
                newName = newName,
                completionMessage = messages.notiComplete,
                errorMessage = "rename 실패: ${item.path.substringAfterLast("/")}",
                onRefresh = { fileNavigatorUseCase.refresh() }
            )
        }
    }

    /** 확인을 기다리는 원격 이름 변경을 실행한다. */
    fun confirmRename() = renameGate.confirm()

    /** 확인을 기다리는 원격 이름 변경을 취소한다. */
    fun cancelRename() = renameGate.cancel()

    /**
     * [item]을 [name]으로 바꿀 때 현재 원격 폴더의 기존 항목과 겹치는 방식을 판정한다.
     * @return 겹치는 방식. 폴더 목록을 확인하지 못했으면(조회 중·실패) 판정할 수 없으므로 null(거부 알림은 ConflictCheckUseCase가 한다)
     */
    private fun renameConflictOf(item: FileHolderItem, name: String): RenameConflict? =
        conflictCheckUseCase.findRenameConflict(
            targetPath = item.path,
            targetIsDirectory = item.isDirectory,
            newName = name,
            folder = fileNavigatorUseCase,
            ignoreCase = REMOTE_IGNORE_CASE,
        )

    /**
     * items를 원격 서버에서 하나의 작업으로 삭제한다.
     * @param items 삭제할 원격 파일 또는 디렉터리 항목
     */
    fun onDelete(items: List<FileHolderItem>) {
        if (items.isEmpty()) return
        remoteFileOperationUseCase.deleteRemote(
            targets = items.associate { it.path to it.isDirectory },
            completionMessage = messages.notiDeleteComplete,
            onRefresh = { fileNavigatorUseCase.refresh() }
        )
    }

    /** 새 폴더 다이얼로그에 미리 채울 이름. 현재 폴더에 기본 이름이 있으면 `이름 (n)`으로 겹치지 않게 고른다. */
    fun suggestNewFolderName(): String = conflictCheckUseCase.suggestNewEntryName(
        baseName = messages.defaultNewFolderName,
        extension = "",
        folder = fileNavigatorUseCase,
        ignoreCase = REMOTE_IGNORE_CASE,
    )

    /**
     * 현재 원격 경로 아래에 dirName 이름의 새 디렉터리를 생성한다. 규칙을 위반한 이름이면 UseCase가 거부하고 오류를 알린다.
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

    companion object {
        private const val TAG = "RemoteFileListVM"
        /** 서버의 대소문자 구분 여부를 알 수 없으므로, 대부분인 Unix 계열 서버에 맞춰 이름 변경 겹침을 대소문자를 구분해 비교한다. */
        private const val REMOTE_IGNORE_CASE = false
    }
}
