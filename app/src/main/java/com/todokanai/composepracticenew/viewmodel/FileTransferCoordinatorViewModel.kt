package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.di.RemoteNavigator
import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.tools.OperationMessages
import com.todokanai.composepracticenew.ui.model.ConflictResolution
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.NameCollisionGroup
import com.todokanai.composepracticenew.ui.model.PendingConflict
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.ui.model.SelectionState
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.RemoteConnectionStateUseCase
import com.todokanai.composepracticenew.usecase.RemoteFileOperationUseCase
import com.todokanai.composepracticenew.usecase.UploadConflictCheckUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 로컬·원격 파일 탐색기의 선택 상태([SelectionState] — 전송 대기 목록 포함)를 조율하고, 대기 목록의 다운로드·업로드(업로드는 확인할 때의 충돌 확인 포함)를 실행하는 ViewModel.
 * FILE_BROWSER_GRAPH에 scoped된다.
 * @param localNavigator 다운로드 목적지(현재 로컬 경로)와 완료 후 새로고침을 제공하는 UseCase
 * @param remoteNavigator 업로드 목적지(현재 원격 경로)와 원격 목록을 제공하는 UseCase
 * @param remoteFileOperationUseCase 다운로드·업로드를 실행하는 UseCase
 * @param uploadConflictCheckUseCase 업로드 전에 확인할 겹침(목적지에 이미 있는 이름, 함께 올리는 항목끼리)을 모으는 UseCase
 * @param messages 작업 완료 메시지 제공자
 * @param connectionStateUseCase 원격 세션 끊김을 알려주는 연결 상태 UseCase
 */
@HiltViewModel
class FileTransferCoordinatorViewModel @Inject constructor(
    private val localNavigator: FileNavigatorUseCase,
    @RemoteNavigator private val remoteNavigator: FileNavigatorUseCase,
    private val remoteFileOperationUseCase: RemoteFileOperationUseCase,
    private val uploadConflictCheckUseCase: UploadConflictCheckUseCase,
    private val messages: OperationMessages,
    connectionStateUseCase: RemoteConnectionStateUseCase,
) : ViewModel() {

    /** 로컬 화면의 선택 상태 — 다운로드 대기 목록도 이 상태(다운로드 목적지 확인)에만 있다. */
    private val local = SelectionStateHolder<SelectMode.LocalOperation, SelectMode.Download>()
    /** 원격 화면의 선택 상태 — 업로드 대기 목록도 이 상태(업로드 목적지 확인)에만 있다. 원격 작업은 아직 없다. */
    private val remote = SelectionStateHolder<Nothing, SelectMode.Upload>()

    /** 로컬 파일 목록 화면의 선택 상태(모드와 선택·다운로드 대기 목록). */
    val localSelection: StateFlow<SelectionState<SelectMode.LocalOperation, SelectMode.Download>> = local.state

    /** 원격 파일 목록 화면의 선택 상태(모드와 선택·업로드 대기 목록). */
    val remoteSelection: StateFlow<SelectionState<Nothing, SelectMode.Upload>> = remote.state

    /** 업로드의 "충돌 검사 → 즉시 실행 / 사용자 확인 대기" 흐름을 관리한다. */
    private val uploadGate = ConflictGate()
    /** 업로드 실행 전 사용자 확인을 기다리는 충돌. */
    val uploadConflict: StateFlow<PendingConflict?> = uploadGate.pending

    init {
        // 끊김을 상태로 본다 — 어느 화면에 있든, 끊긴 뒤에 이 ViewModel이 만들어졌든 끊긴 세션에 기대는 상태를 남기지 않는다
        connectionStateUseCase.connectionState
            .filter { it == RemoteConnectionState.Lost }
            .onEach { discardRemoteSessionState() }
            .launchIn(viewModelScope)
    }

    /** first 하나를 선택한 채로 로컬 화면을 다중 선택 모드로 전환한다. */
    fun startLocalMultiSelect(first: FileHolderItem) = local.startMultiSelect(first)

    /** 선택 목록을 유지한 채 로컬 작업(복사·이동·압축 해제)의 목적지 확인 단계로 전환한다. */
    fun enterLocalOperation(operation: SelectMode.LocalOperation) = local.enterOperation(operation)

    /** first 하나를 선택한 채로 원격 화면을 다중 선택 모드로 전환한다. */
    fun startRemoteMultiSelect(first: FileHolderItem) = remote.startMultiSelect(first)

    /** 로컬 다중 선택에 item을 더한다. 호출부가 토글을 보장해야 한다. */
    fun addToLocalList(item: FileHolderItem) = local.add(item)
    /** 로컬 다중 선택에서 item을 뺀다. */
    fun removeFromLocalList(item: FileHolderItem) = local.remove(item)
    /** 로컬 다중 선택 목록을 일괄 교체한다. 드래그 범위 선택 시 사용한다. */
    fun setLocalSelectedList(list: List<FileHolderItem>) = local.replaceSelection(list)

    /** 원격 다중 선택에 item을 더한다. 호출부가 토글을 보장해야 한다. */
    fun addToRemoteList(item: FileHolderItem) = remote.add(item)
    /** 원격 다중 선택에서 item을 뺀다. */
    fun removeFromRemoteList(item: FileHolderItem) = remote.remove(item)
    /** 원격 다중 선택 목록을 일괄 교체한다. 드래그 범위 선택 시 사용한다. */
    fun setRemoteSelectedList(list: List<FileHolderItem>) = remote.replaceSelection(list)

    /**
     * 로컬 화면의 선택을 작업 대상으로 꺼내고 로컬 화면을 기본 모드로 되돌린다. 같은 저장소 작업(복사·이동·압축 해제·압축·삭제·이름 변경)은 확인할 때 이것으로 대상을 받는다.
     * @return 꺼낸 선택. 로컬 화면에 꺼낼 선택이 없으면(기본 모드·다운로드 확인) null
     */
    fun submitLocalSelection(): SubmittedSelection? = local.submit()

    /**
     * 원격 화면의 선택을 작업 대상으로 꺼내고 원격 화면을 기본 모드로 되돌린다. 원격 작업(삭제·이름 변경)은 확인할 때 이것으로 대상을 받는다.
     * @return 꺼낸 선택. 원격 화면에 꺼낼 선택이 없으면(기본 모드·업로드 확인) null
     */
    fun submitRemoteSelection(): SubmittedSelection? = remote.submit()

    /**
     * 로컬 선택을 꺼내 업로드 대기 목록으로 원격 화면에 넘긴다. 로컬 화면은 꺼낼 때 기본 모드로 돌아간다.
     * 원격 화면에 다중 선택이 남아 있어도 묻지 않고 버린다 — 의도된 동작이다(새로 시작한 전송이 받는 화면의 이전 선택보다 우선한다).
     * @return 업로드 확인 단계로 들어갔는지. 로컬 화면에 꺼낼 선택이 없으면 아무것도 바꾸지 않고 false
     */
    fun enterUploadMode(): Boolean {
        val submitted = local.submit() ?: return false
        remote.receive(SelectMode.Upload, submitted.items)
        return true
    }

    /**
     * 원격 선택을 꺼내 다운로드 대기 목록으로 로컬 화면에 넘긴다. 원격 화면은 꺼낼 때 기본 모드로 돌아간다.
     * 로컬 화면에 다중 선택이나 작업(복사·이동·압축 해제) 목적지 확인이 남아 있어도 묻지 않고 버린다 — 의도된 동작이다(새로 시작한 전송이 받는 화면의 이전 선택보다 우선한다).
     * @return 다운로드 확인 단계로 들어갔는지. 원격 화면에 꺼낼 선택이 없으면 아무것도 바꾸지 않고 false
     */
    fun enterDownloadMode(): Boolean {
        val submitted = remote.submit() ?: return false
        local.receive(SelectMode.Download, submitted.items)
        return true
    }

    /**
     * 다운로드 대기 목록을 현재 로컬 경로로 다운로드하고 로컬 화면을 기본 모드로 되돌린다(대기 목록도 함께 사라짐).
     * 같은 이름의 로컬 항목은 다운로드가 그 항목을 쓰려 할 때 묻는다(전역 충돌 질문).
     * 로컬 화면이 다운로드 확인 상태가 아니거나 현재 로컬 경로가 없으면 다운로드할 대상·자리가 없으므로 아무것도 하지 않는다.
     */
    fun confirmDownload() {
        val pending = (local.state.value as? SelectionState.Receiving)?.pending ?: return
        val destPath = localNavigator.currentPath.value ?: return
        download(pending, destPath)
        local.reset()
    }

    /** 진행 중인 업로드 전 겹침 확인들의 부모 Job. 원격 세션이 끊기면 이 아래의 확인을 모두 멈춘다. */
    private val uploadChecks = SupervisorJob(viewModelScope.coroutineContext[Job])

    /** 진행 중인 업로드 전 겹침 확인(시작한 순서). 맨 앞이 [cancelUploadCheck]의 대상이다. */
    private val runningChecks = MutableStateFlow<List<RunningCheck>>(emptyList())

    /** 업로드 전 겹침 확인이 진행 중인지. 표시 전용이며, 확인 흐름의 판단에는 쓰지 않는다. */
    val uploadChecking: StateFlow<Boolean> = runningChecks
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * 업로드 대기 목록을 꺼내 현재 원격 경로(충돌을 판정한 확인된 목록의 경로)로 업로드한다.
     * 대기 목록은 확인을 누를 때 원격 화면에서 꺼내 이 확인이 직접 들고 간다 — 확인하는 동안 다음 업로드가 원격 화면에 들어와도 이 업로드는 사라지지 않고, 같은 대기를 두 번 확인할 수도 없다.
     * 목적지에 같은 이름이 있거나 함께 올리는 항목끼리(폴더 안쪽 포함) 목적지에서 같은 이름이 될 수 있으면 [uploadConflict]로 확인을 요청한다(여러 업로드의 확인은 차례로 묻는다).
     * 확인을 취소했거나 현재 원격 경로의 목록을 확인하지 못했으면(조회 중·실패 — 거부 알림은 ConflictCheckUseCase가 한다) 실행하지 않고,
     * 원격 화면이 기본 모드일 때만 대기 목록을 돌려놓아 다시 확인할 수 있게 한다. 그사이 다른 선택·대기가 원격 화면을 차지했으면 이 업로드는 끝난다.
     * 확인하는 동안에는 [uploadChecking]이 true이며, [cancelUploadCheck]로 멈출 수 있다.
     * 원격 화면이 업로드 확인 상태가 아니면 업로드할 대상이 없으므로 아무것도 하지 않는다.
     */
    fun confirmUpload() {
        val waiting = remote.takeReceiving() ?: return
        val job = viewModelScope.launch(uploadChecks, start = CoroutineStart.LAZY) {
            val checked = uploadConflictCheckUseCase.findConflicts(waiting.pending, remoteNavigator, nameOf = { it.name }, pathOf = { it.path })
            // 확인을 마친 직후 취소됐을 수 있다 — 취소한 업로드를 묻거나 올리지 않도록 이어 가기 전에 확인한다
            ensureActive()
            if (checked == null) {
                remote.restoreReceiving(waiting)
                return@launch
            }
            uploadGate.request(
                waiting.pending,
                checked.conflicts,
                checked.collisions.map(NameCollisionGroup::from),
                onCancel = { remote.restoreReceiving(waiting) },
            ) { skip -> upload(waiting.pending.excluding(skip), checked.destinationPath) }
        }
        val check = RunningCheck(waiting, job)
        runningChecks.update { it + check }
        job.invokeOnCompletion { runningChecks.update { it - check } }
        job.start()
    }

    /**
     * 가장 먼저 시작한 진행 중인 업로드 전 확인을 멈춘다. 확인 창에서 취소할 때와 같이, 원격 화면이 기본 모드일 때만 그 대기 목록을 돌려놓는다.
     * 대기 목록은 멈춘 확인이 끝나기를 기다리지 않고 여기서 돌려놓는다 — 확인의 정리 코드에서 돌려놓으면 세션 끊김으로 멈춘 확인도 끊긴 뒤에 대기를 되살린다.
     * 진행 중인 확인이 없으면 아무것도 하지 않는다.
     */
    fun cancelUploadCheck() {
        val front = runningChecks.value.firstOrNull() ?: return
        front.job.cancel()
        runningChecks.update { it - front }
        remote.restoreReceiving(front.waiting)
    }

    /** 로컬 화면을 기본 모드로 되돌린다(취소·뒤로가기). 선택 목록·다운로드 대기 목록도 함께 사라진다. */
    fun resetLocalSelection() = local.reset()

    /** 원격 화면을 기본 모드로 되돌린다(취소·뒤로가기). 선택 목록·업로드 대기 목록도 함께 사라진다. */
    fun resetRemoteSelection() = remote.reset()

    /**
     * 끊긴 원격 세션에 기대는 상태를 버린다. 로컬의 다운로드 대기(원격 경로 목록)와 원격 화면의 선택·업로드 대기, 진행 중인 업로드 전 확인과 확인을 기다리는 충돌이 해당한다.
     * 다시 연결하려면 저장소 선택을 거쳐 새 세션을 열어야 하므로 이 상태로는 어떤 전송도 성공할 수 없다. 로컬의 선택·작업 상태는 로컬 저장소만 쓰므로 그대로 둔다.
     */
    private fun discardRemoteSessionState() {
        local.discardReceiving()
        uploadChecks.cancelChildren()
        runningChecks.value = emptyList()
        uploadGate.discardAll()
        remote.reset()
    }

    /** 대기 중인 업로드 충돌을 사용자 선택대로 처리한다. */
    fun resolveUploadConflict(resolution: ConflictResolution) { uploadGate.resolve(resolution) }

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

    /** items를 [localDestPath]에 다운로드한다. */
    private fun download(items: List<FileHolderItem>, localDestPath: String) {
        remoteFileOperationUseCase.download(
            items = items.map { it.toDomain() },
            localDestPath = localDestPath,
            completionMessage = messages.notiDownloadComplete,
            onRefresh = { localNavigator.refresh() }
        )
    }

    /** items를 [remoteDestPath]에 업로드한다. */
    private fun upload(items: List<FileHolderItem>, remoteDestPath: String) {
        items.forEach { item ->
            remoteFileOperationUseCase.upload(
                item = item.toDomain(),
                remoteDestPath = remoteDestPath,
                completionMessage = messages.notiUploadComplete,
                onRefresh = { remoteNavigator.refresh() }
            )
        }
    }
}
