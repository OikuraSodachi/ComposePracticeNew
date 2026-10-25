package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.di.RemoteNavigator
import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.tools.OperationMessages
import com.todokanai.composepracticenew.ui.model.ConflictResolution
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.PendingConflict
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.ui.model.SelectionState
import com.todokanai.composepracticenew.ui.model.SelectionSummary
import com.todokanai.composepracticenew.usecase.ConflictCheckUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.RemoteConnectionStateUseCase
import com.todokanai.composepracticenew.usecase.RemoteFileOperationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 로컬·원격 파일 탐색기의 선택 상태([SelectionState] — 전송 대기 목록 포함)를 조율하고, 대기 목록의 다운로드·업로드(충돌 확인 포함)를 실행하는 ViewModel.
 * FILE_BROWSER_GRAPH에 scoped된다.
 * @param localNavigator 다운로드 목적지(현재 로컬 경로)와 로컬 목록을 제공하는 UseCase
 * @param remoteNavigator 업로드 목적지(현재 원격 경로)와 원격 목록을 제공하는 UseCase
 * @param remoteFileOperationUseCase 다운로드·업로드를 실행하는 UseCase
 * @param conflictCheckUseCase 목적지 이름 충돌을 판별하는 UseCase
 * @param messages 작업 완료 메시지 제공자
 * @param connectionStateUseCase 원격 세션 끊김을 알려주는 연결 상태 UseCase
 */
@HiltViewModel
class FileTransferCoordinatorViewModel @Inject constructor(
    private val localNavigator: FileNavigatorUseCase,
    @RemoteNavigator private val remoteNavigator: FileNavigatorUseCase,
    private val remoteFileOperationUseCase: RemoteFileOperationUseCase,
    private val conflictCheckUseCase: ConflictCheckUseCase,
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

    /** 로컬 선택 목록의 요약(개수·총 크기 표시 문자열). 정보 다이얼로그가 표시하며, 선택이 바뀔 때만 다시 계산한다. */
    val localSelectionSummary: StateFlow<SelectionSummary> = local.state
        .map { it.selected }
        .distinctUntilChanged()
        .map(SelectionSummary::from)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SelectionSummary.from(emptyList())
        )

    /** 다운로드의 "충돌 검사 → 즉시 실행 / 사용자 확인 대기" 흐름을 관리한다. */
    private val downloadGate = ConflictGate()
    /** 다운로드 실행 전 사용자 확인을 기다리는 충돌. */
    val downloadConflict: StateFlow<PendingConflict?> = downloadGate.pending

    /** 업로드의 "충돌 검사 → 즉시 실행 / 사용자 확인 대기" 흐름을 관리한다. */
    private val uploadGate = ConflictGate()
    /** 업로드 실행 전 사용자 확인을 기다리는 충돌. */
    val uploadConflict: StateFlow<PendingConflict?> = uploadGate.pending

    init {
        // 끊김을 상태로 본다 — 어느 화면에 있든, 끊긴 뒤에 이 ViewModel이 만들어졌든 끊긴 세션에 기대는 상태를 남기지 않는다
        connectionStateUseCase.connectionState
            .filter { it == RemoteConnectionState.LOST }
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
     * 로컬 선택 목록을 업로드 대기 목록으로 원격 화면에 넘기고, 로컬 화면은 기본 모드로 되돌린다.
     * 원격 화면에 다중 선택이 남아 있어도 묻지 않고 버린다 — 의도된 동작이다(새로 시작한 전송이 받는 화면의 이전 선택보다 우선한다).
     */
    fun enterUploadMode() {
        remote.receive(SelectMode.Upload, local.state.value.selected)
        local.reset()
    }

    /**
     * items를 다운로드 대기 목록으로 로컬 화면에 넘기고, 원격 화면은 기본 모드로 되돌린다.
     * 로컬 화면에 다중 선택이나 작업(복사·이동·압축 해제) 목적지 확인이 남아 있어도 묻지 않고 버린다 — 의도된 동작이다(새로 시작한 전송이 받는 화면의 이전 선택보다 우선한다).
     */
    fun enterDownloadMode(items: List<FileHolderItem>) {
        local.receive(SelectMode.Download, items)
        remote.reset()
    }

    /**
     * 다운로드 대기 목록을 현재 로컬 경로(충돌을 판정한 확인된 목록의 경로)로 다운로드한다. 이름 충돌이 있으면 [downloadConflict]로 확인을 요청한다.
     * 실제로 실행됐을 때만 로컬 화면을 기본 모드로 되돌린다(대기 목록도 함께 사라짐).
     * 확인을 취소하면 다운로드 확인 상태(대기 목록 포함)가 유지되어 다시 확인할 수 있다.
     * 현재 로컬 경로의 목록을 확인하지 못했으면(조회 중·실패) 충돌을 판정할 수 없으므로 실행하지 않는다(거부 알림은 ConflictCheckUseCase가 한다). 대기 목록은 유지된다.
     * 로컬 화면이 다운로드 확인 상태가 아니면 다운로드할 대상이 없으므로 아무것도 하지 않는다.
     */
    fun confirmDownload() {
        val pending = (local.state.value as? SelectionState.Receiving)?.pending ?: return
        val checked = conflictCheckUseCase.findCopyMoveConflicts(pending, localNavigator) { it.name } ?: return
        downloadGate.request(pending, checked) { skip ->
            download(pending.excluding(skip), checked.destinationPath)
            local.reset()
        }
    }

    /**
     * 업로드 대기 목록을 현재 원격 경로(충돌을 판정한 확인된 목록의 경로)로 업로드한다. 이름 충돌이 있으면 [uploadConflict]로 확인을 요청한다.
     * 서버 응답으로 빈 폴더인지 확인하지 못한 목록이면 같은 이름의 파일이 덮어써질 수 있으므로 충돌이 없어 보여도 확인을 요청한다.
     * 실제로 실행됐을 때만 원격 화면을 기본 모드로 되돌린다(대기 목록도 함께 사라짐).
     * 확인을 취소하면 업로드 확인 상태(대기 목록 포함)가 유지되어 다시 확인할 수 있다.
     * 현재 원격 경로의 목록을 확인하지 못했으면(조회 중·실패) 충돌을 판정할 수 없으므로 실행하지 않는다(거부 알림은 ConflictCheckUseCase가 한다). 대기 목록은 유지된다.
     * 원격 화면이 업로드 확인 상태가 아니면 업로드할 대상이 없으므로 아무것도 하지 않는다.
     */
    fun confirmUpload() {
        val pending = (remote.state.value as? SelectionState.Receiving)?.pending ?: return
        val checked = conflictCheckUseCase.findCopyMoveConflicts(pending, remoteNavigator) { it.name } ?: return
        uploadGate.request(pending, checked) { skip ->
            upload(pending.excluding(skip), checked.destinationPath)
            remote.reset()
        }
    }

    /** 로컬 화면을 기본 모드로 되돌린다. 선택 목록·다운로드 대기 목록도 함께 사라진다. */
    fun resetLocalSelection() = local.reset()

    /** 원격 화면을 기본 모드로 되돌린다. 선택 목록·업로드 대기 목록도 함께 사라진다. */
    fun resetRemoteSelection() = remote.reset()

    /**
     * 끊긴 원격 세션에 기대는 상태를 버린다. 로컬의 다운로드 대기(원격 경로 목록)와 원격 화면의 선택·업로드 대기, 그 확인을 기다리는 충돌이 해당한다.
     * 다시 연결하려면 저장소 선택을 거쳐 새 세션을 열어야 하므로 이 상태로는 어떤 전송도 성공할 수 없다. 로컬의 선택·작업 상태는 로컬 저장소만 쓰므로 그대로 둔다.
     */
    private fun discardRemoteSessionState() {
        local.discardReceiving()
        downloadGate.resolve(ConflictResolution.CANCEL)
        remote.reset()
        uploadGate.resolve(ConflictResolution.CANCEL)
    }

    /** 대기 중인 다운로드 충돌을 사용자 선택대로 처리한다. */
    fun resolveDownloadConflict(resolution: ConflictResolution) { downloadGate.resolve(resolution) }

    /** 대기 중인 업로드 충돌을 사용자 선택대로 처리한다. */
    fun resolveUploadConflict(resolution: ConflictResolution) { uploadGate.resolve(resolution) }

    /** 이 목록에서 [skip]과 경로가 같은 항목을 제외한다. */
    private fun List<FileHolderItem>.excluding(skip: List<FileHolderItem>): List<FileHolderItem> {
        val skipPaths = skip.mapTo(HashSet()) { it.path }
        return filter { it.path !in skipPaths }
    }

    /** items를 [localDestPath]에 다운로드한다. */
    private fun download(items: List<FileHolderItem>, localDestPath: String) {
        items.forEach { item ->
            remoteFileOperationUseCase.download(
                remotePath = item.path,
                localDestPath = localDestPath,
                isDirectory = item.isDirectory,
                completionMessage = messages.notiDownloadComplete,
                onRefresh = { localNavigator.refresh() }
            )
        }
    }

    /** items를 [remoteDestPath]에 업로드한다. */
    private fun upload(items: List<FileHolderItem>, remoteDestPath: String) {
        items.forEach { item ->
            remoteFileOperationUseCase.upload(
                localPath = item.path,
                remoteDestPath = remoteDestPath,
                completionMessage = messages.notiUploadComplete,
                onRefresh = { remoteNavigator.refresh() }
            )
        }
    }
}
