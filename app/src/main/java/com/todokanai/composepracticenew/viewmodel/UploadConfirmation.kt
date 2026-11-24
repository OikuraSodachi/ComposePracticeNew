package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.ui.model.ConflictResolution
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.NameCollisionGroup
import com.todokanai.composepracticenew.ui.model.PendingConflict
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.ui.model.SelectionState
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.UploadConflictCheckUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 업로드 대기 목록을 확인할 때의 "겹침 확인 → 즉시 업로드 / 사용자 확인 대기 후 업로드" 흐름과 진행 중인 확인을 관리한다.
 * 확인을 취소·멈췄거나 목록을 확인하지 못했으면 대기 목록을 [restoreReceiving]으로 돌려놓는다. 파일 전송 조율 ViewModel이 구성(composition)으로 보유한다(#451).
 * @param scope 확인을 실행할 scope — 이 scope가 끝나면 진행 중인 확인도 멈춘다
 * @param uploadConflictCheckUseCase 업로드 전에 확인할 겹침(목적지에 이미 있는 이름, 함께 올리는 항목끼리)을 모으는 UseCase
 * @param remoteNavigator 업로드 목적지(현재 원격 경로)와 원격 목록을 제공하는 UseCase
 * @param restoreReceiving 확인을 마치지 못한 업로드 대기를 원격 화면에 돌려놓는 함수(원격 화면이 기본 모드일 때만 돌려놓는다)
 * @param upload 올릴 항목과 원격 목적지 경로를 받아 업로드를 실행하는 함수
 */
class UploadConfirmation(
    private val scope: CoroutineScope,
    private val uploadConflictCheckUseCase: UploadConflictCheckUseCase,
    private val remoteNavigator: FileNavigatorUseCase,
    private val restoreReceiving: (SelectionState.Receiving<SelectMode.Upload>) -> Unit,
    private val upload: (items: List<FileHolderItem>, remoteDestPath: String) -> Unit,
) {

    /** 업로드의 "충돌 검사 → 즉시 실행 / 사용자 확인 대기" 흐름을 관리한다. */
    private val gate = ConflictGate()
    /** 업로드 실행 전 사용자 확인을 기다리는 충돌. */
    val conflict: StateFlow<PendingConflict?> = gate.pending

    /** 진행 중인 업로드 전 겹침 확인들의 부모 Job. 원격 세션이 끊기면 이 아래의 확인을 모두 멈춘다. */
    private val checks = SupervisorJob(scope.coroutineContext[Job])

    /** 진행 중인 업로드 전 겹침 확인(시작한 순서). 맨 앞이 [cancelFrontCheck]의 대상이다. */
    private val runningChecks = MutableStateFlow<List<RunningCheck>>(emptyList())

    /** 업로드 전 겹침 확인이 진행 중인지. 표시 전용이며, 확인 흐름의 판단에는 쓰지 않는다. */
    val checking: StateFlow<Boolean> = runningChecks
        .map { it.isNotEmpty() }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * [waiting]의 대기 목록을 현재 원격 경로(충돌을 판정한 확인된 목록의 경로)로 올리기 전에 겹침을 확인한다.
     * 겹침이 있으면 [conflict]로 확인을 요청하고(여러 업로드의 확인은 차례로 묻는다), 없으면 바로 올린다.
     * 확인을 취소했거나 현재 원격 경로의 목록을 확인하지 못했으면(조회 중·실패 — 거부 알림은 ConflictCheckUseCase가 한다) 올리지 않고 대기 목록을 돌려놓는다.
     * 확인하는 동안에는 [checking]이 true이며, [cancelFrontCheck]로 멈출 수 있다.
     * @param waiting 원격 화면에서 꺼내 이 확인이 들고 가는 업로드 대기
     */
    fun confirm(waiting: SelectionState.Receiving<SelectMode.Upload>) {
        val job = scope.launch(checks, start = CoroutineStart.LAZY) {
            val checked = uploadConflictCheckUseCase.findConflicts(waiting.transfer.pending, remoteNavigator, nameOf = { it.name }, pathOf = { it.path })
            // 확인을 마친 직후 취소됐을 수 있다 — 취소한 업로드를 묻거나 올리지 않도록 이어 가기 전에 확인한다
            ensureActive()
            if (checked == null) {
                restoreReceiving(waiting)
                return@launch
            }
            gate.request(
                waiting.transfer.pending,
                checked.conflicts,
                checked.collisions.map(NameCollisionGroup::from),
                onCancel = { restoreReceiving(waiting) },
            ) { skip -> upload(waiting.transfer.pending.excluding(skip), checked.destinationPath) }
        }
        val check = RunningCheck(waiting, job)
        runningChecks.update { it + check }
        job.invokeOnCompletion { runningChecks.update { it - check } }
        job.start()
    }

    /**
     * 가장 먼저 시작한 진행 중인 업로드 전 확인을 멈추고, 확인 창에서 취소할 때와 같이 그 대기 목록을 돌려놓는다.
     * 대기 목록은 멈춘 확인이 끝나기를 기다리지 않고 여기서 돌려놓는다 — 확인의 정리 코드에서 돌려놓으면 세션 끊김으로 멈춘 확인도 끊긴 뒤에 대기를 되살린다.
     * 진행 중인 확인이 없으면 아무것도 하지 않는다.
     */
    fun cancelFrontCheck() {
        val front = runningChecks.value.firstOrNull() ?: return
        front.job.cancel()
        runningChecks.update { it - front }
        restoreReceiving(front.waiting)
    }

    /** 대기 중인 업로드 충돌을 사용자 선택대로 처리한다. */
    fun resolve(resolution: ConflictResolution) { gate.resolve(resolution) }

    /** 진행 중인 확인을 모두 멈추고 확인을 기다리는 충돌을 버린다. 대기 목록은 돌려놓지 않는다. 업로드가 더는 성공할 수 없을 때(원격 세션 끊김) 쓴다. */
    fun discardAll() {
        checks.cancelChildren()
        runningChecks.value = emptyList()
        gate.discardAll()
    }

    /**
     * 진행 중인 업로드 전 겹침 확인 하나. 같은 대기 목록을 두 번 확인하지 않으므로 인스턴스로 구분한다.
     * @param waiting 이 확인이 원격 화면에서 꺼내 들고 간 업로드 대기
     * @param job 확인을 실행하는 Job
     */
    private class RunningCheck(val waiting: SelectionState.Receiving<SelectMode.Upload>, val job: Job)

    /** 이 목록에서 [skip]과 경로가 같은 항목을 제외한다. */
    private fun List<FileHolderItem>.excluding(skip: List<FileHolderItem>): List<FileHolderItem> {
        val skipPaths = skip.mapTo(HashSet()) { it.path }
        return filter { it.path !in skipPaths }
    }
}
