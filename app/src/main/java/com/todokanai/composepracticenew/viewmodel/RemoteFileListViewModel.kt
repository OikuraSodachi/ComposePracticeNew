package com.todokanai.composepracticenew.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.di.RemoteNavigator
import com.todokanai.composepracticenew.model.ReconnectResult
import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.tools.NewFolderNameDefaults
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.ListingUiState
import com.todokanai.composepracticenew.ui.model.ListingUiStateMapper
import com.todokanai.composepracticenew.usecase.ConflictCheckUseCase
import com.todokanai.composepracticenew.usecase.ConnectFailureReportUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.RemoteConnectionResilienceUseCase
import com.todokanai.composepracticenew.usecase.RemoteConnectionStateUseCase
import com.todokanai.composepracticenew.usecase.RemoteFileOperationUseCase
import com.todokanai.composepracticenew.usecase.SortModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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
 * 원격 파일 목록 화면의 UI 상태, 네비게이션, 자동 재연결, 원격 파일 조작(이름변경·삭제·새폴더)을 관리하는 ViewModel.
 * 다운로드·업로드 실행은 FileTransferCoordinatorViewModel이, 연결 유지 서비스는 연결 상태를 관찰하는 MyApplication이 담당한다.
 * @param fileNavigatorUseCase 원격 디렉터리 탐색 상태를 제공하는 UseCase
 * @param sortModeUseCase 정렬 기준을 조회·저장하는 UseCase
 * @param remoteFileOperationUseCase 원격 파일 조작을 실행하는 UseCase
 * @param connectionResilienceUseCase 마지막 서버 자동 재연결 UseCase
 * @param connectionStateUseCase 원격 연결 상태(끊김 포함)를 제공하는 UseCase
 * @param conflictCheckUseCase 이름 변경 시 같은 이름 항목과의 겹침을 판정하는 UseCase
 * @param connectFailureReportUseCase 다시 시도하면 될 수 있는 자동 재연결 실패를 알림함에 맡기는 UseCase
 * @param folderNames 새 폴더 다이얼로그의 기본 이름 제공자
 */
@HiltViewModel
class RemoteFileListViewModel @Inject constructor(
    @RemoteNavigator private val fileNavigatorUseCase: FileNavigatorUseCase,
    sortModeUseCase: SortModeUseCase,
    private val remoteFileOperationUseCase: RemoteFileOperationUseCase,
    private val connectionResilienceUseCase: RemoteConnectionResilienceUseCase,
    connectionStateUseCase: RemoteConnectionStateUseCase,
    conflictCheckUseCase: ConflictCheckUseCase,
    connectFailureReportUseCase: ConnectFailureReportUseCase,
    folderNames: NewFolderNameDefaults,
) : ViewModel() {

    /** 자동 재연결 실패를 이유에 맞는 알림(알림함·다이얼로그)으로 바꾼다. */
    private val reconnectFailureNotice = ConnectFailureNotice(connectFailureReportUseCase)

    /** 자동 재연결한 서버의 파일 목록을 읽을 수 없어 연결을 종료했음을 알리는 다이얼로그를 보여야 하면 true. */
    val unreadableServer: StateFlow<Boolean> = reconnectFailureNotice.unreadableServer

    /** 서버의 파일 목록을 읽을 수 없다는 다이얼로그를 닫는다. */
    fun dismissUnreadableServer() = reconnectFailureNotice.dismissUnreadableServer()

    /** 자동 재연결할 서버의 저장된 포트가 범위를 벗어나 연결을 시도하지 않았음을 알리는 다이얼로그를 보여야 하면 true. */
    val invalidPort: StateFlow<Boolean> = reconnectFailureNotice.invalidPort

    /** 포트가 범위를 벗어났다는 다이얼로그를 닫는다. */
    fun dismissInvalidPort() = reconnectFailureNotice.dismissInvalidPort()

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
                    is ReconnectResult.Connected -> Log.d(TAG, "reconnectLast — 재연결 성공: ${result.item.name}")
                    ReconnectResult.NoSavedServer -> {
                        Log.w(TAG, "reconnectLast — 재연결할 서버 없음")
                        reconnectFailureNotice.notifyConnectionFailed()
                    }
                    // 설정 저장소는 결과로 돌려준 읽기 실패를 따로 알리지 않으므로, 결과를 받은 이 화면이 이유와 함께 알린다
                    ReconnectResult.SettingsUnreadable -> {
                        Log.w(TAG, "reconnectLast — 설정을 읽지 못해 재연결할 서버를 알 수 없음")
                        reconnectFailureNotice.notifyReconnectSettingsUnreadable()
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
    private fun isLost(state: RemoteConnectionState): Boolean = state == RemoteConnectionState.Lost

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

    /** 경로 breadcrumb 상태와 그 항목으로의 이동. */
    val dirTreeStateHolder = DirTreeStateHolder(fileNavigatorUseCase, viewModelScope)

    /** 정렬 기준의 노출과 변경. */
    val sortModeStateHolder = SortModeStateHolder(sortModeUseCase, viewModelScope)

    /** 현재 원격 디렉터리의 목록을 다시 조회한다. 목록 조회 실패 화면의 다시 시도에 쓴다. */
    fun retryListing() = fileNavigatorUseCase.refresh()

    /** [folder]로 이동한다. 원격은 폴더만 열며, 열지는 선택 모드 규칙(clickActionOf)이 정한다. */
    fun openFolder(folder: FileHolderItem) {
        viewModelScope.launch {
            fileNavigatorUseCase.setPath(folder.path)
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

    /** 이름 변경의 검증·충돌 판정·덮어쓰기 확인 대기와 새 폴더의 대상 폴더·이름 제안·검증. */
    val entryNaming = EntryNamingStateHolder(
        validateEntryName = remoteFileOperationUseCase::validateEntryName,
        renameOverwriteOf = remoteFileOperationUseCase::renameOverwriteOf,
        conflictCheckUseCase = conflictCheckUseCase,
        folder = fileNavigatorUseCase,
        folderNames = folderNames,
    )

    /**
     * selection의 항목 하나를 newName으로 이름 변경한다. 겹침 판정과 덮어쓰기 확인 요청은 [entryNaming]이 한다.
     * 확인 후에도 RNTO만 보내며, 덮어쓸지는 서버 동작에 따른다. 규칙을 위반한 이름이면 UseCase가 거부하고 오류를 알린다.
     * @param selection 원격 화면의 선택에서 꺼낸 한 항목
     * @param newName 변경할 새 이름
     */
    fun onRename(selection: SubmittedItem, newName: String) {
        val item = selection.item
        entryNaming.requestRename(item, newName) { _ ->
            remoteFileOperationUseCase.renameRemote(
                path = item.path,
                newName = newName,
                onRefresh = { fileNavigatorUseCase.refresh() }
            )
        }
    }

    /**
     * selection의 항목을 원격 서버에서 하나의 작업으로 삭제한다.
     * @param selection 원격 화면의 선택에서 꺼낸 삭제할 원격 파일 또는 디렉터리 항목
     */
    fun onDelete(selection: SubmittedSelection) {
        val items = selection.items
        if (items.isEmpty()) return
        remoteFileOperationUseCase.deleteRemote(
            targets = items.map { it.toDomain() },
            onRefresh = { fileNavigatorUseCase.refresh() }
        )
    }

    /**
     * [target] 원격 폴더 아래에 dirName 이름의 새 디렉터리를 생성한다. 규칙을 위반한 이름이면 UseCase가 거부하고 오류를 알린다.
     * @param target 다이얼로그를 연 때 정한 대상 폴더 — [EntryNamingStateHolder.validateNewFolderName]이 판정한 것과 같은 폴더
     * @param dirName 생성할 디렉터리 이름
     */
    fun onMakeDirectory(target: String, dirName: String) {
        remoteFileOperationUseCase.makeDirectoryRemote(
            parentPath = target,
            dirName = dirName,
            onRefresh = { fileNavigatorUseCase.refresh() }
        )
    }

    companion object {
        private const val TAG = "RemoteFileListVM"
    }
}
