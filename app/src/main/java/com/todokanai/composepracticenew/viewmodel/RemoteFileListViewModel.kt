package com.todokanai.composepracticenew.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.di.ApplicationScope
import com.todokanai.composepracticenew.di.RemoteNavigator
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.ProgressStateModel
import com.todokanai.composepracticenew.model.toModel
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_DOWNLOAD
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_UPLOAD
import com.todokanai.composepracticenew.service.FtpServiceController
import com.todokanai.composepracticenew.ui.model.DirectoryItem
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.FtpUseCase
import com.todokanai.composepracticenew.tools.MyNotification
import com.todokanai.composepracticenew.tools.TransferCoordinator
import com.todokanai.composepracticenew.usecase.ProgressUseCase
import com.todokanai.composepracticenew.usecase.RemoteStorageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 원격 파일 목록 화면의 UI 상태, 네비게이션, 파일 조작(다운로드·업로드·이름변경·삭제·새폴더)을 관리하는 ViewModel. */
@HiltViewModel
class RemoteFileListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    @RemoteNavigator private val fileNavigatorUseCase: FileNavigatorUseCase,
    private val remoteStorageUseCase: RemoteStorageUseCase,
    private val ftpServiceController: FtpServiceController,
    private val ftpUseCase: FtpUseCase,
    private val myNoti: MyNotification,
    private val transferCoordinator: TransferCoordinator,
    private val progressUseCase: ProgressUseCase,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    private val _reconnectFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** 자동 재연결 실패 이벤트. */
    val reconnectFailed: SharedFlow<Unit> = _reconnectFailed.asSharedFlow()

    private val _connectionLost = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** 서버 측 연결 종료(idle timeout 등)로 FTP 연결이 끊겼을 때 발행된다. */
    val connectionLost: SharedFlow<Unit> = _connectionLost.asSharedFlow()

    /** 진행 중인 원격 전송 작업의 진행률 맵. ProgressDialog 표시에 사용한다. DOWNLOAD·UPLOAD 키로 필터링해 로컬 IO 항목을 제외한다. */
    val remoteProgressMap: StateFlow<Map<Int, ProgressStateModel>> = progressUseCase.progressMap
        .map { map ->
            map.filter { (_, state) -> state.actionKey == ACTION_KEY_DOWNLOAD || state.actionKey == ACTION_KEY_UPLOAD }
                .mapValues { (_, state) -> state.toModel() }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )


    init {
        val path = fileNavigatorUseCase.currentPath.value
        Log.d(TAG, "init — currentPath=$path")
        if (path == null) {
            viewModelScope.launch { reconnectLast() }
        }
        viewModelScope.launch { observeConnectionLost() }
    }

    private suspend fun observeConnectionLost() {
        ftpUseCase.isConnected
            .scan(Pair(false, false)) { acc, curr -> Pair(acc.second, curr) }
            .filter { (prev, curr) -> prev && !curr }
            .collect {
                Log.w(TAG, "연결 끊김 감지 — 서비스 중지 및 이벤트 발행")
                ftpServiceController.stop()
                _connectionLost.emit(Unit)
            }
    }

    private suspend fun reconnectLast() {
        Log.d(TAG, "reconnectLast 시작")
        val item = remoteStorageUseCase.getLastConnectedItem()
        if (item == null) {
            Log.w(TAG, "reconnectLast — 저장된 서버 없음")
            return
        }
        Log.d(TAG, "reconnectLast — 서버=${item.address}:${item.port}")
        val connected = fileNavigatorUseCase.setPath(item)
        Log.d(TAG, "reconnectLast — 결과=$connected")
        if (connected) {
            ftpServiceController.start(item.name)
        } else {
            _reconnectFailed.tryEmit(Unit)
        }
    }

    /** 원격 파일 목록 화면에 필요한 UI 상태를 담는 클래스. */
    data class UiState(
        val fileHolderItemList: List<FileHolderItem> = emptyList()
    )

    val uiState: StateFlow<UiState> = fileNavigatorUseCase.fileList
        .map { list -> UiState(list.map { FileHolderItem.from(it, isLocalFile = false) }) }
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

    /** 상위 디렉터리로 이동한다. 루트이면 toStorageFrag를 호출한다. */
    fun onBackPressed(toStorageFrag: () -> Unit) {
        viewModelScope.launch {
            fileNavigatorUseCase.navigateBack(toStorageFrag)
        }
    }

    /** pendingList 중 현재 원격 디렉터리에 이미 같은 이름으로 존재하는 파일 목록을 반환한다. */
    fun getUploadConflicts(pendingList: List<FileHolderItem>): List<FileHolderItem> {
        val remoteFiles = uiState.value.fileHolderItemList
        return pendingList.filter { local -> remoteFiles.any { remote -> remote.name == local.name } }
    }

    /** pending 중 conflicts에 포함된 항목을 제외하고 업로드한다. */
    fun onUploadSkipping(pending: List<FileHolderItem>, conflicts: List<FileHolderItem>) {
        val skipPaths = conflicts.map { it.path }.toSet()
        pending.filter { it.path !in skipPaths }.forEach { onUpload(it.path) }
    }

    /**
     * item의 원격 파일을 localDestPath로 다운로드한다.
     * appScope에서 ftpUseCase.download()를 collect해 ProgressState를 처리한다.
     * @param item 다운로드할 원격 파일 항목
     * @param localDestPath 저장할 로컬 디렉터리의 절대 경로
     */
    fun onDownload(item: FileHolderItem, localDestPath: String) {
        val instanceId = item.path.hashCode()
        collectTransfer(instanceId, ACTION_KEY_DOWNLOAD, ftpUseCase.download(item.path, localDestPath, item.isDirectory) { state ->
            progressUseCase.setProgressState(instanceId, state.copy(actionKey = ACTION_KEY_DOWNLOAD))
            state.progress?.let { progress -> sendProgressNoti(ACTION_KEY_DOWNLOAD, progress, instanceId) }
        })
    }

    /**
     * localPath의 파일을 현재 원격 경로에 업로드한다.
     * currentPath를 remoteDestPath로 사용해 appScope에서 ftpUseCase.upload()를 collect한다.
     * @param localPath 업로드할 로컬 파일의 절대 경로
     */
    fun onUpload(localPath: String) {
        val remotePath = fileNavigatorUseCase.currentPath.value ?: return
        val instanceId = localPath.hashCode()
        collectTransfer(instanceId, ACTION_KEY_UPLOAD, ftpUseCase.upload(localPath, remotePath) { state ->
            progressUseCase.setProgressState(instanceId, state.copy(actionKey = ACTION_KEY_UPLOAD))
            state.progress?.let { progress -> sendProgressNoti(ACTION_KEY_UPLOAD, progress, instanceId) }
        })
    }

    /**
     * source Flow를 수집해 remoteProgressMap을 갱신하고, 완료 또는 에러 시 해당 키를 제거한다.
     * @param instanceId 진행률 맵에서 이 전송 인스턴스를 식별하는 키
     * @param actionKey ProgressDialog 라벨 표시에 사용하는 작업 유형 키
     * @param source 수집할 진행률 Flow
     */
    private fun collectTransfer(instanceId: Int, actionKey: Int, source: Flow<ProgressState>) {
        transferCoordinator.launch(
            scope = appScope,
            sources = listOf(source),
            onProgress = {},  // progress는 onProgress 콜백으로 직접 보고 — Flow는 error 신호만 방출
            onRemove = { progressUseCase.removeProgress(instanceId) },
            onSuccess = {
                val message = completionMessage(actionKey)
                myNoti.completedNotification("", message, actionKey, instanceId)
                progressUseCase.emitCompletion(message)
                if (actionKey == ACTION_KEY_UPLOAD) fileNavigatorUseCase.refresh()
            },
            onError = { message ->
                myNoti.cancelNotification(instanceId)
                progressUseCase.emitError(message ?: context.getString(R.string.noti_complete))
            }
        )
    }

    companion object {
        private const val TAG = "RemoteFileListVM"
    }

    private fun completionMessage(actionKey: Int): String = when (actionKey) {
        ACTION_KEY_DOWNLOAD -> context.getString(R.string.noti_download_complete)
        ACTION_KEY_UPLOAD -> context.getString(R.string.noti_upload_complete)
        else -> context.getString(R.string.noti_complete)
    }

    private fun sendProgressNoti(actionKey: Int, progress: Int, notifId: Int) {
        when (actionKey) {
            ACTION_KEY_DOWNLOAD -> myNoti.downloadProgressNoti(progress, notifId)
            ACTION_KEY_UPLOAD -> myNoti.uploadProgressNoti(progress, notifId)
        }
    }

    /**
     * item을 newName으로 이름 변경한다.
     * ftpUseCase를 통해 FtpRepository.rename()을 호출하고 성공 시 목록을 갱신한다.
     * @param item 이름을 변경할 원격 파일 항목, @param newName 변경할 새 이름
     */
    fun onRename(item: FileHolderItem, newName: String) {
        appScope.launch {
            val success = ftpUseCase.rename(item.path, newName)
            if (success) fileNavigatorUseCase.refresh()
            else progressUseCase.emitError("rename 실패: ${item.name}")
        }
    }

    /**
     * item을 원격 서버에서 삭제한다.
     * 파일이면 FtpRepository.deleteFile(), 디렉터리이면 FtpRepository.removeDirectory()를 호출한다.
     * @param item 삭제할 원격 파일 또는 디렉터리 항목
     */
    fun onDelete(item: FileHolderItem) {
        appScope.launch {
            val success = ftpUseCase.delete(item.path, item.isDirectory)
            if (success) fileNavigatorUseCase.refresh()
            else progressUseCase.emitError("삭제 실패: ${item.name}")
        }
    }

    /**
     * 현재 원격 경로 아래에 dirName 이름의 새 디렉터리를 생성한다.
     * FtpRepository.makeDirectory()를 호출하고 성공 시 목록을 갱신한다.
     * @param dirName 생성할 디렉터리 이름
     */
    fun onMakeDirectory(dirName: String) {
        val currentPath = fileNavigatorUseCase.currentPath.value ?: return
        appScope.launch {
            val success = ftpUseCase.makeDirectory(currentPath, dirName)
            if (success) fileNavigatorUseCase.refresh()
            else progressUseCase.emitError("디렉터리 생성 실패: $dirName")
        }
    }
}
