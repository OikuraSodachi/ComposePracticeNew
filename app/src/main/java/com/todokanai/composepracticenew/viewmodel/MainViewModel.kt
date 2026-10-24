package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.ShownProgress
import com.todokanai.composepracticenew.model.toModel
import com.todokanai.composepracticenew.tools.AppExitHandler
import com.todokanai.composepracticenew.usecase.GetStorageListUseCase
import com.todokanai.composepracticenew.usecase.ProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Activity 스코프의 앱 전역 관심사(스토리지 목록 초기화·진행률 다이얼로그 표시·작업 완료/실패 이벤트·앱 종료)를 담당하는 ViewModel.
 * @param getStorageListUseCase 물리 스토리지 목록을 로드·갱신하는 UseCase
 * @param progressUseCase 진행 중인 작업 상태와 파일 작업 완료·실패 이벤트를 제공하는 UseCase
 * @param exitHandler 연결 서비스를 중단하고 앱 종료 이벤트를 발행하는 핸들러
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val getStorageListUseCase: GetStorageListUseCase,
    progressUseCase: ProgressUseCase,
    private val exitHandler: AppExitHandler
) : ViewModel() {

    /** 진행률 다이얼로그를 어느 작업에 띄울지. 진행 중인 작업 목록 변경, 닫기, 알림 클릭으로만 바뀐다. */
    private val dialogSelection = MutableStateFlow(ProgressDialogSelection())

    /** 진행률 다이얼로그에 띄울 작업. null이면 다이얼로그를 띄우지 않는다. */
    val shownProgress: StateFlow<ShownProgress?> =
        combine(progressUseCase.progressMap, dialogSelection.map { it.shownId }, ::shownProgressOf)
            .flowOn(Dispatchers.Default) // 크기 문자열 포맷을 메인 스레드 밖에서 수행
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null
            )

    /** 원격 파일 목록 화면으로의 이동 요청을 화면이 수집할 때까지 하나만 보관하는 채널. 연속 요청은 한 번의 이동으로 합쳐진다. */
    private val _remoteProgressNavigation = Channel<Unit>(Channel.CONFLATED)
    /** 원격 전송 알림 클릭으로 원격 파일 목록 화면으로 이동해야 할 때 발행된다. */
    val remoteProgressNavigation: Flow<Unit> = _remoteProgressNavigation.receiveAsFlow()

    /** 파일 작업 실패 메시지 이벤트. UI에서 Toast 표시에 사용한다. */
    val operationError: SharedFlow<String> = progressUseCase.operationErrors
    /** 파일 작업 완료 메시지 이벤트. UI에서 Toast 표시에 사용한다. */
    val operationCompletion: SharedFlow<String> = progressUseCase.operationCompletions

    /** 앱을 종료하라는 이벤트. 화면 호스트에서 수집해 Activity를 종료한다. */
    val exitEvent: Flow<Unit> = exitHandler.exitEvent

    init {
        // 화면이 다이얼로그를 수집하지 않는 동안에도 새 작업의 시작을 놓치지 않도록 ViewModel이 살아 있는 동안 계속 본다
        progressUseCase.progressMap
            .map(::activeIdsOf)
            .distinctUntilChanged()
            .onEach { activeIds -> dialogSelection.update { it.onActiveIdsChanged(activeIds) } }
            .launchIn(viewModelScope)
    }

    /**
     * 스토리지 목록을 로드해 UI 상태를 갱신한다.
     * @return Unit
     */
    fun loadStorages() = viewModelScope.launch(Dispatchers.IO) { getStorageListUseCase.execute() }

    /**
     * 알림 클릭으로 전달된 작업의 진행률 다이얼로그를 띄운다. 그 작업이 이미 끝났으면 띄우지 않는다.
     * 원격 전송이면 원격 파일 목록 화면으로의 이동도 요청한다.
     * @param operationTypeId 알림 Intent로 전달된 FileOperationType.id
     * @param instanceId 알림 Intent로 전달된 작업 인스턴스 ID
     */
    fun requestShowProgressDialog(operationTypeId: Int, instanceId: Int) {
        dialogSelection.update { it.show(instanceId) }
        if (FileOperationType.fromId(operationTypeId)?.isRemote == true) {
            _remoteProgressNavigation.trySend(Unit)
        }
    }

    /**
     * [instanceId] 작업의 진행률 다이얼로그를 닫는다. 작업은 계속 진행되며, 다른 작업의 다이얼로그도 띄우지 않는다.
     * @param instanceId 화면이 띄우고 있던 다이얼로그의 작업 인스턴스 ID
     */
    fun dismissProgressDialog(instanceId: Int) {
        dialogSelection.update { it.dismiss(instanceId) }
    }

    /** 연결 서비스를 중단하고 앱 종료 이벤트를 발행한다. */
    fun exit() = exitHandler.execute()

    /** [shownId] 작업이 진행 중이면 그 작업의 UI 상태를, 아니면 null을 돌려준다. */
    private fun shownProgressOf(progressMap: Map<Int, ProgressState>, shownId: Int?): ShownProgress? {
        val state = shownId?.let(progressMap::get)?.takeIf(::isActive) ?: return null
        return ShownProgress(instanceId = shownId, progress = state.toModel())
    }

    /** 진행 중인(진행률 100% 미만) 작업의 ID를 돌려준다. */
    private fun activeIdsOf(progressMap: Map<Int, ProgressState>): Set<Int> =
        progressMap.filterValues(::isActive).keys

    /** 작업이 아직 진행 중인지. 진행률을 보고하기 전인 작업도 진행 중으로 본다. */
    private fun isActive(state: ProgressState): Boolean = (state.progress ?: 0) < 100
}
