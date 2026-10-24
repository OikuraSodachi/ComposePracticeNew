package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.ProgressStateModel
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
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

    /** 사용자가 진행률 다이얼로그를 닫을 당시 진행 중이던 작업 ID. 이 목록에 없는 작업이 진행 중이면 다이얼로그를 다시 표시한다. */
    private val dismissedProgressIds = MutableStateFlow(emptySet<Int>())

    /** 진행률 다이얼로그에 표시할 진행 중 작업. 작업 인스턴스 ID를 키로 하며, 비어 있으면 다이얼로그를 표시하지 않는다. */
    val visibleProgress: StateFlow<Map<Int, ProgressStateModel>> =
        combine(progressUseCase.progressMap, dismissedProgressIds, ::visibleProgressOf)
            .flowOn(Dispatchers.Default) // 크기 문자열 포맷을 메인 스레드 밖에서 수행
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap()
            )

    private val _remoteProgressNavigation = Channel<Unit>(Channel.CONFLATED)
    /** 원격 전송 알림 클릭으로 원격 파일 목록 화면으로 이동해야 할 때 발행된다. */
    val remoteProgressNavigation: Flow<Unit> = _remoteProgressNavigation.receiveAsFlow()

    /** 파일 작업 실패 메시지 이벤트. UI에서 Toast 표시에 사용한다. */
    val operationError: SharedFlow<String> = progressUseCase.operationErrors
    /** 파일 작업 완료 메시지 이벤트. UI에서 Toast 표시에 사용한다. */
    val operationCompletion: SharedFlow<String> = progressUseCase.operationCompletions

    /** 앱을 종료하라는 이벤트. 화면 호스트에서 수집해 Activity를 종료한다. */
    val exitEvent: Flow<Unit> = exitHandler.exitEvent

    /**
     * 스토리지 목록을 로드해 UI 상태를 갱신한다.
     * @return Unit
     */
    fun loadStorages() = viewModelScope.launch(Dispatchers.IO) { getStorageListUseCase.execute() }

    /**
     * 알림 클릭으로 전달된 작업 종류 id에 맞춰 닫았던 진행률 다이얼로그를 다시 표시한다.
     * 원격 전송이면 원격 파일 목록 화면으로의 이동도 요청한다.
     * @param operationTypeId 알림 Intent로 전달된 FileOperationType.id
     */
    fun requestShowProgressDialog(operationTypeId: Int) {
        dismissedProgressIds.value = emptySet()
        if (FileOperationType.fromId(operationTypeId)?.isRemote == true) {
            _remoteProgressNavigation.trySend(Unit)
        }
    }

    /** 진행률 다이얼로그를 닫는다. 지금 표시 중인 작업은 다시 표시하지 않으며, 작업은 계속 진행된다. */
    fun dismissProgressDialog() {
        dismissedProgressIds.value = visibleProgress.value.keys
    }

    /** 연결 서비스를 중단하고 앱 종료 이벤트를 발행한다. */
    fun exit() = exitHandler.execute()

    /** 진행 중인 작업 중 닫지 않은 작업이 하나라도 있으면 진행 중인 작업 전부를, 없으면 빈 맵을 돌려준다. */
    private fun visibleProgressOf(
        progressMap: Map<Int, ProgressState>,
        dismissedIds: Set<Int>
    ): Map<Int, ProgressStateModel> {
        val active = activeProgressOf(progressMap)
        return if (active.keys.any { it !in dismissedIds }) active else emptyMap()
    }

    /** 진행률이 100% 미만인 작업만 UI 모델로 바꿔 돌려준다. */
    private fun activeProgressOf(progressMap: Map<Int, ProgressState>): Map<Int, ProgressStateModel> =
        progressMap.mapValues { (_, state) -> state.toModel() }
            .filterValues { it.progress < 100 }
}
