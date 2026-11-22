package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.di.RemoteNavigator
import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.ui.model.ConflictResolution
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.PendingConflict
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.ui.model.SelectionState
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.RemoteConnectionStateUseCase
import com.todokanai.composepracticenew.usecase.RemoteFileOperationUseCase
import com.todokanai.composepracticenew.usecase.RemoteSessionCheckUseCase
import com.todokanai.composepracticenew.usecase.UploadConflictCheckUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * 로컬·원격 파일 탐색기의 선택 상태([SelectionState] — 전송 대기 목록 포함)를 조율하고, 대기 목록의 다운로드·업로드(업로드는 확인할 때의 충돌 확인 포함)를 실행하는 ViewModel.
 * FILE_BROWSER_GRAPH에 scoped된다.
 * @param localNavigator 다운로드 목적지(현재 로컬 경로)와 완료 후 새로고침, 로컬 다중 선택을 맞출 로컬 목록을 제공하는 UseCase
 * @param remoteNavigator 업로드 목적지(현재 원격 경로)와 원격 목록(원격 다중 선택도 이 목록에 맞춘다)을 제공하는 UseCase
 * @param remoteFileOperationUseCase 다운로드·업로드를 실행하는 UseCase
 * @param uploadConflictCheckUseCase 업로드 전에 확인할 겹침(목적지에 이미 있는 이름, 함께 올리는 항목끼리)을 모으는 UseCase
 * @param connectionStateUseCase 원격 세션 끊김을 알려주는 연결 상태 UseCase
 * @param remoteSessionCheckUseCase 원격 화면으로 가는 진입(하단 전환·업로드) 전에 연결된 원격 세션이 있는지 확인하는 UseCase
 */
@HiltViewModel
class FileTransferCoordinatorViewModel @Inject constructor(
    private val localNavigator: FileNavigatorUseCase,
    @RemoteNavigator private val remoteNavigator: FileNavigatorUseCase,
    private val remoteFileOperationUseCase: RemoteFileOperationUseCase,
    uploadConflictCheckUseCase: UploadConflictCheckUseCase,
    connectionStateUseCase: RemoteConnectionStateUseCase,
    private val remoteSessionCheckUseCase: RemoteSessionCheckUseCase,
) : ViewModel() {

    /** 로컬 화면의 선택 상태 — 다운로드 대기 목록도 이 상태(다운로드 목적지 확인)에만 있다. */
    private val local = SelectionStateHolder<SelectMode.LocalOperation, SelectMode.Download>()
    /** 원격 화면의 선택 상태 — 업로드 대기 목록도 이 상태(업로드 목적지 확인)에만 있다. 원격 작업은 아직 없다. */
    private val remote = SelectionStateHolder<Nothing, SelectMode.Upload>()

    /** 로컬 파일 목록 화면의 선택 상태(모드와 선택·다운로드 대기 목록). */
    val localSelection: StateFlow<SelectionState<SelectMode.LocalOperation, SelectMode.Download>> = local.state

    /** 원격 파일 목록 화면의 선택 상태(모드와 선택·업로드 대기 목록). */
    val remoteSelection: StateFlow<SelectionState<Nothing, SelectMode.Upload>> = remote.state

    /** 업로드 대기 목록을 확인할 때의 겹침 확인·충돌 확인 대기와 진행 중인 확인. */
    private val uploadConfirmation = UploadConfirmation(
        scope = viewModelScope,
        uploadConflictCheckUseCase = uploadConflictCheckUseCase,
        remoteNavigator = remoteNavigator,
        restoreReceiving = remote::restoreReceiving,
        upload = ::upload,
    )

    /** 업로드 실행 전 사용자 확인을 기다리는 충돌. */
    val uploadConflict: StateFlow<PendingConflict?> = uploadConfirmation.conflict

    /** 업로드 전 겹침 확인이 진행 중인지. 표시 전용이며, 확인 흐름의 판단에는 쓰지 않는다. */
    val uploadChecking: StateFlow<Boolean> = uploadConfirmation.checking

    // 이 init은 모든 상태 선언 뒤에 둔다(#363) — viewModelScope는 Main.immediate이고 연결 상태는 현재 값을 바로 내보내므로,
    // 생성할 때 이미 끊겨 있으면 생성자 안에서 discardRemoteSessionState가 실행된다. 이 아래에 선언한 상태는 그때 아직 null이다.
    init {
        // 끊김을 상태로 본다 — 어느 화면에 있든, 끊긴 뒤에 이 ViewModel이 만들어졌든 끊긴 세션에 기대는 상태를 남기지 않는다
        connectionStateUseCase.connectionState
            .filter { it == RemoteConnectionState.Lost }
            .onEach { discardRemoteSessionState() }
            .launchIn(viewModelScope)
        // 다중 선택은 그 선택을 고른 목록에 묶인다(#524) — 같은 폴더를 다시 조회해 목록이 바뀌어도 화면에 없는 항목이 선택에 남지 않도록
        localNavigator.listing
            .onEach { local.followListing(it, isLocalFile = true) }
            .launchIn(viewModelScope)
        remoteNavigator.listing
            .onEach { remote.followListing(it, isLocalFile = false) }
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
    /** 로컬 다중 선택 목록을 일괄 교체한다. 드래그 범위 선택과 모두 선택/해제에 사용한다. */
    fun setLocalSelectedList(list: List<FileHolderItem>) = local.replaceSelection(list)

    /** 원격 다중 선택에 item을 더한다. 호출부가 토글을 보장해야 한다. */
    fun addToRemoteList(item: FileHolderItem) = remote.add(item)
    /** 원격 다중 선택에서 item을 뺀다. */
    fun removeFromRemoteList(item: FileHolderItem) = remote.remove(item)
    /** 원격 다중 선택 목록을 일괄 교체한다. 드래그 범위 선택과 모두 선택/해제에 사용한다. */
    fun setRemoteSelectedList(list: List<FileHolderItem>) = remote.replaceSelection(list)

    /**
     * 로컬 화면의 선택을 작업 대상으로 꺼내고 로컬 화면을 기본 모드로 되돌린다. 같은 저장소 작업(복사·이동·압축 해제·압축·삭제)은 확인할 때 이것으로 대상을 받는다.
     * @return 꺼낸 선택. 로컬 화면에 꺼낼 선택이 없으면(기본 모드·다운로드 확인) null
     */
    fun submitLocalSelection(): SubmittedSelection? = local.submit()

    /**
     * 로컬 화면의 선택이 한 항목일 때만 그 항목을 작업 대상으로 꺼내고 로컬 화면을 기본 모드로 되돌린다. 이름 변경은 확인할 때 이것으로 대상을 받는다.
     * @return 꺼낸 항목. 로컬 화면의 선택이 한 항목이 아니면 null
     */
    fun submitLocalItem(): SubmittedItem? = local.submitSingle()

    /**
     * 원격 화면의 선택을 작업 대상으로 꺼내고 원격 화면을 기본 모드로 되돌린다. 원격 삭제는 확인할 때 이것으로 대상을 받는다.
     * @return 꺼낸 선택. 원격 화면에 꺼낼 선택이 없으면(기본 모드·업로드 확인) null
     */
    fun submitRemoteSelection(): SubmittedSelection? = remote.submit()

    /**
     * 원격 화면의 선택이 한 항목일 때만 그 항목을 작업 대상으로 꺼내고 원격 화면을 기본 모드로 되돌린다. 원격 이름 변경은 확인할 때 이것으로 대상을 받는다.
     * @return 꺼낸 항목. 원격 화면의 선택이 한 항목이 아니면 null
     */
    fun submitRemoteItem(): SubmittedItem? = remote.submitSingle()

    /**
     * 로컬 선택을 꺼내 업로드 대기 목록으로 원격 화면에 넘긴다. 로컬 화면은 꺼낼 때 기본 모드로 돌아간다.
     * 원격 화면에 다중 선택이 남아 있어도 묻지 않고 버린다 — 의도된 동작이다(새로 시작한 전송이 받는 화면의 이전 선택보다 우선한다).
     * 연결된 원격 세션이 없으면 업로드할 곳이 없으므로 거부를 알리고, 로컬 선택은 꺼내지 않고 그대로 둔다.
     * @return 업로드 확인 단계로 들어갔는지. 로컬 화면에 꺼낼 선택이 없거나 연결된 원격 세션이 없으면 아무것도 바꾸지 않고 false
     */
    fun enterUploadMode(): Boolean {
        if (!remoteSessionCheckUseCase.checkUploadEntry()) return false
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

    /**
     * 업로드 대기 목록을 꺼내 현재 원격 경로(충돌을 판정한 확인된 목록의 경로)로 업로드한다. 겹침 확인과 충돌 확인 요청은 [UploadConfirmation.confirm]이 한다.
     * 대기 목록은 확인을 누를 때 원격 화면에서 꺼내 이 확인이 직접 들고 간다 — 확인하는 동안 다음 업로드가 원격 화면에 들어와도 이 업로드는 사라지지 않고, 같은 대기를 두 번 확인할 수도 없다.
     * 확인을 마치지 못한 대기 목록은 원격 화면이 기본 모드일 때만 돌려놓아 다시 확인할 수 있게 한다. 그사이 다른 선택·대기가 원격 화면을 차지했으면 이 업로드는 끝난다.
     * 원격 화면이 업로드 확인 상태가 아니면 업로드할 대상이 없으므로 아무것도 하지 않는다.
     */
    fun confirmUpload() {
        val waiting = remote.takeReceiving() ?: return
        uploadConfirmation.confirm(waiting)
    }

    /** 가장 먼저 시작한 진행 중인 업로드 전 확인을 멈추고 그 대기 목록을 돌려놓는다([UploadConfirmation.cancelFrontCheck]). */
    fun cancelUploadCheck() = uploadConfirmation.cancelFrontCheck()

    /**
     * 연결된 원격 세션이 있는지 판별한다. 원격 화면은 연결된 세션이 있을 때만 들어가므로, 하단 전환은 이것으로 갈 곳(원격 화면 / 저장소 화면)을 정한다.
     * @return 연결된 원격 세션이 있으면 true
     */
    fun hasRemoteSession(): Boolean = remoteSessionCheckUseCase.hasSession()

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
        uploadConfirmation.discardAll()
        remote.reset()
    }

    /**
     * 다중 선택을 새 목록 조회 상태에 맞춘다. 조회 결과가 오면 선택을 그 목록에 맞추고, 조회에 실패했거나 목록이 없으면 고를 항목이 없으므로 다중 선택을 끝낸다.
     * 다시 조회하는 중에는 결과를 기다리며 선택을 그대로 둔다. 작업 목적지 확인·전송 대기는 다른 폴더로 옮겨 다니는 상태이므로 맞추지 않는다.
     * @param listing 이 화면의 새 목록 조회 상태
     * @param isLocalFile 로컬 파일 목록이면 true — 선택 항목을 목록 화면과 같은 앱 레이어 항목으로 만든다
     */
    private fun SelectionStateHolder<*, *>.followListing(listing: DirectoryListing, isLocalFile: Boolean) {
        when (listing) {
            // 다중 선택이 아니면 맞출 선택이 없으므로 목록 전체를 앱 레이어 항목으로 바꾸지 않는다
            is DirectoryListing.Loaded -> if (state.value is SelectionState.MultiSelect) retainListed(FileHolderItem.fromList(listing.items, isLocalFile))
            is DirectoryListing.Loading -> Unit
            DirectoryListing.NoPath, is DirectoryListing.Failed -> endMultiSelect()
        }
    }

    /** 대기 중인 업로드 충돌을 사용자 선택대로 처리한다. */
    fun resolveUploadConflict(resolution: ConflictResolution) = uploadConfirmation.resolve(resolution)

    /** items를 [localDestPath]에 다운로드한다. */
    private fun download(items: List<FileHolderItem>, localDestPath: String) {
        remoteFileOperationUseCase.download(
            items = items.map { it.toDomain() },
            localDestPath = localDestPath,
            onRefresh = { localNavigator.refresh() }
        )
    }

    /** items를 [remoteDestPath]에 업로드한다. */
    private fun upload(items: List<FileHolderItem>, remoteDestPath: String) {
        remoteFileOperationUseCase.upload(
            items = items.map { it.toDomain() },
            remoteDestPath = remoteDestPath,
            onRefresh = { remoteNavigator.refresh() }
        )
    }
}
