package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.model.ByteAmount
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.IndeterminatePhase
import com.todokanai.composepracticenew.model.ProgressPhase
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.ui.model.ShownProgress
import com.todokanai.composepracticenew.ui.model.toModel
import com.todokanai.composepracticenew.usecase.CancelOperationUseCase
import com.todokanai.composepracticenew.usecase.ProgressUseCase
import com.todokanai.composepracticenew.usecase.RemoteSessionCheckUseCase
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
import javax.inject.Inject

/**
 * Activity 스코프에서 진행률 다이얼로그에 띄울 작업을 정하고, 진행 알림 클릭으로 그 작업을 보여 달라는 요청을 받는 ViewModel(#448).
 * @param progressUseCase 진행 중인 작업 상태를 제공하는 UseCase
 * @param cancelOperationUseCase 진행 중인 작업을 취소하는 UseCase
 * @param remoteSessionCheckUseCase 원격 작업 알림 클릭으로 원격 화면에 가기 전에 연결된 원격 세션이 있는지 확인하는 UseCase
 */
@HiltViewModel
class ProgressDialogViewModel @Inject constructor(
    progressUseCase: ProgressUseCase,
    private val cancelOperationUseCase: CancelOperationUseCase,
    private val remoteSessionCheckUseCase: RemoteSessionCheckUseCase
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
    /** 원격 작업 알림 클릭으로 원격 파일 목록 화면으로 이동해야 할 때(연결된 원격 세션이 있을 때만) 발행된다. */
    val remoteProgressNavigation: Flow<Unit> = _remoteProgressNavigation.receiveAsFlow()

    init {
        // 화면이 다이얼로그를 수집하지 않는 동안에도 새 작업의 시작을 놓치지 않도록 ViewModel이 살아 있는 동안 계속 본다
        progressUseCase.progressMap
            .map(::activeIdsOf)
            .distinctUntilChanged()
            .onEach { activeIds -> dialogSelection.update { it.onActiveIdsChanged(activeIds) } }
            .launchIn(viewModelScope)
    }

    /**
     * 알림 클릭으로 전달된 작업의 진행률 다이얼로그를 띄운다. 그 작업이 이미 끝났으면 띄우지 않는다.
     * 원격 작업이고 연결된 원격 세션이 있으면 원격 파일 목록 화면으로의 이동도 요청한다.
     * 작업 세션은 탐색 세션과 따로라(#436) 탐색 연결이 없어도 원격 작업 알림이 남으므로, 세션이 없으면 다이얼로그만 띄운다(#492).
     * @param operationTypeId 알림 Intent로 전달된 FileOperationType.id
     * @param instanceId 알림 Intent로 전달된 작업 인스턴스 ID
     */
    fun requestShowProgressDialog(operationTypeId: Int, instanceId: Int) {
        dialogSelection.update { it.show(instanceId) }
        if (shouldNavigateToRemote(operationTypeId)) {
            _remoteProgressNavigation.trySend(Unit)
        }
    }

    /**
     * [operationTypeId] 작업의 알림 클릭이 원격 화면으로 가야 하는지 — 원격 스토리지를 다루는 작업(`FileOperationType.isRemote`: 전송·원격 삭제, #466)이고
     * 갈 원격 화면에 연결된 세션이 있을 때만.
     */
    private fun shouldNavigateToRemote(operationTypeId: Int): Boolean =
        FileOperationType.fromId(operationTypeId)?.isRemote == true && remoteSessionCheckUseCase.hasSession()

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

    /** [shownId] 작업이 진행 중이면 그 작업의 UI 상태를, 아니면 null을 돌려준다. */
    private fun shownProgressOf(progressMap: Map<Int, ProgressState>, shownId: Int?): ShownProgress? {
        val state = shownId?.let(progressMap::get)?.takeIf(::isActive) ?: return null
        return ShownProgress(instanceId = shownId, progress = state.toModel())
    }

    /** 진행 중인(진행률 100% 미만) 작업의 ID를 돌려준다. */
    private fun activeIdsOf(progressMap: Map<Int, ProgressState>): Set<Int> =
        progressMap.filterValues(::isActive).keys

    /** 작업이 아직 진행 중인지. 진행률을 보고하기 전인 작업도 진행 중으로 본다. */
    private fun isActive(state: ProgressState): Boolean = when (val phase = state.phase) {
        ProgressPhase.Starting, is IndeterminatePhase -> true
        is ProgressPhase.Counted -> phase.percent < 100
        is ProgressPhase.Bytes -> when (val amount = phase.amount) {
            is ByteAmount.Known -> amount.percent < 100
            is ByteAmount.PartlyUnknown -> true
        }
    }
}
