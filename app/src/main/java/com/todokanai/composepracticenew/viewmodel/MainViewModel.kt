package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.OperationNotice
import com.todokanai.composepracticenew.model.PreferenceFailure
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.ShownProgress
import com.todokanai.composepracticenew.model.toModel
import com.todokanai.composepracticenew.tools.AppExitHandler
import com.todokanai.composepracticenew.usecase.CancelOperationUseCase
import com.todokanai.composepracticenew.usecase.ConnectionLossNoticeUseCase
import com.todokanai.composepracticenew.usecase.GetStorageListUseCase
import com.todokanai.composepracticenew.usecase.OperationNoticeUseCase
import com.todokanai.composepracticenew.usecase.PreferenceFailureUseCase
import com.todokanai.composepracticenew.usecase.ProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Activity 스코프의 앱 전역 관심사(스토리지 목록 초기화·진행률 다이얼로그 표시·작업 실패·거부·완료 메시지·원격 세션 끊김·설정 저장소 실패 알림·앱 종료)를 담당하는 ViewModel.
 * @param getStorageListUseCase 물리 스토리지 목록을 로드·갱신하는 UseCase
 * @param progressUseCase 진행 중인 작업 상태를 제공하는 UseCase
 * @param operationNoticeUseCase 파일 작업의 실패·거부·완료 메시지를 알려주는 UseCase
 * @param exitHandler 원격 연결을 종료하고 앱 종료 이벤트를 발행하는 핸들러
 * @param connectionLossNoticeUseCase 원격 세션의 예기치 않은 끊김을 알려주는 UseCase
 * @param preferenceFailureUseCase 설정 저장소의 읽기·저장 실패와 손상 초기화를 알려주는 UseCase
 * @param cancelOperationUseCase 진행 중인 작업을 취소하는 UseCase
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val getStorageListUseCase: GetStorageListUseCase,
    progressUseCase: ProgressUseCase,
    private val operationNoticeUseCase: OperationNoticeUseCase,
    private val exitHandler: AppExitHandler,
    private val connectionLossNoticeUseCase: ConnectionLossNoticeUseCase,
    private val preferenceFailureUseCase: PreferenceFailureUseCase,
    private val cancelOperationUseCase: CancelOperationUseCase
) : ViewModel() {

    /**
     * 진행률 다이얼로그를 어느 작업에 띄울지. 진행 중인 작업 목록 변경, 닫기, 알림 클릭으로만 바뀐다.
     * ViewModel을 만들 때(앱을 열 때) 이미 진행 중인 작업은 새 작업으로 보지 않아 저절로 띄우지 않는다.
     */
    private val dialogSelection = MutableStateFlow(
        ProgressDialogSelection.startingWith(activeIdsOf(progressUseCase.progressMap.value))
    )

    /** 진행률 다이얼로그에 띄울 작업. null이면 다이얼로그를 띄우지 않는다. */
    val shownProgress: StateFlow<ShownProgress?> =
        combine(progressUseCase.progressMap, dialogSelection.map { it.shownId }, ::shownProgressOf)
            .flowOn(Dispatchers.Default) // 크기 문자열 포맷을 메인 스레드 밖에서 수행
            .stateIn(
                scope = viewModelScope,
                // 수집이 멈추면 마지막 값을 바로 버린다 — 복귀 직후 그 사이 끝난 작업의 다이얼로그가 다시 그려지지 않도록
                started = SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0),
                initialValue = null
            )

    /** 원격 파일 목록 화면으로의 이동 요청을 화면이 수집할 때까지 하나만 보관하는 채널. 연속 요청은 한 번의 이동으로 합쳐진다. */
    private val _remoteProgressNavigation = Channel<Unit>(Channel.CONFLATED)
    /** 원격 전송 알림 클릭으로 원격 파일 목록 화면으로 이동해야 할 때 발행된다. */
    val remoteProgressNavigation: Flow<Unit> = _remoteProgressNavigation.receiveAsFlow()

    /** 앱을 종료하라는 이벤트. 화면 호스트에서 수집해 Activity를 종료한다. */
    val exitEvent: Flow<Unit> = exitHandler.exitEvent

    /**
     * 원격 세션의 예기치 않은 끊김을 [show]로 하나씩, 앞 알림을 다 보여 준 뒤에 알린다. 어느 화면에 있든 알리기 위해 화면 호스트가 화면이 보이는 동안 부른다. 취소될 때까지 돌아오지 않는다.
     * 보이는 화면이 없을 때 끊긴 것과 보여 주는 도중 화면이 사라진 알림도 부르면 받고, 그 사이 새 연결을 시작했으면 받지 않는다.
     * 끊김에 따른 정리(서비스 중지·전송 상태 정리·원격 화면 이탈)는 각자 연결 상태를 보고 하므로, 이 알림은 알리는 데만 쓴다.
     * @param show 끊김을 사용자에게 보여 주고, 다 보여 줄 때까지 돌아오지 않는 함수
     */
    suspend fun showConnectionLosses(show: suspend () -> Unit): Nothing = connectionLossNoticeUseCase.deliver { show() }

    /**
     * 설정 저장소의 실패를 [show]로 하나씩, 앞 실패를 다 보여 준 뒤에 알린다. 어느 화면에 있든 알리기 위해 화면 호스트가 화면이 보이는 동안 부른다. 취소될 때까지 돌아오지 않는다.
     * 화면이 보이기 전에 일어난 실패와 보여 주는 도중 화면이 사라진 실패도 부르면 받고, 그 사이 회복한 읽기 실패는 받지 않는다.
     * @param show 실패를 사용자에게 보여 주고, 다 보여 줄 때까지 돌아오지 않는 함수
     */
    suspend fun showPreferenceFailures(show: suspend (PreferenceFailure) -> Unit): Nothing = preferenceFailureUseCase.deliver(show)

    /**
     * 파일 작업의 실패·거부·완료 메시지를 [show]로 하나씩, 앞 메시지를 다 보여 준 뒤에 알린다. 어느 화면에 있든 알리기 위해 화면 호스트가 화면이 보이는 동안 부른다. 취소될 때까지 돌아오지 않는다.
     * 보이는 화면이 없을 때 생긴 메시지(저장소 화면에 있거나 Activity가 없을 때 끝난 작업)와 보여 주는 도중 화면이 사라진 메시지도 부르면 받는다.
     * @param show 메시지를 사용자에게 보여 주고, 다 보여 줄 때까지 돌아오지 않는 함수
     */
    suspend fun showOperationNotices(show: suspend (OperationNotice) -> Unit): Nothing = operationNoticeUseCase.deliver(show)

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

    /**
     * [instanceId] 작업을 취소한다. 다이얼로그는 작업이 멈춰 진행 상태가 지워질 때 함께 사라진다.
     * @param instanceId 화면이 띄우고 있던 다이얼로그의 작업 인스턴스 ID
     */
    fun cancelOperation(instanceId: Int) {
        cancelOperationUseCase.cancel(instanceId)
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
