package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.di.RemoteNavigator
import com.todokanai.composepracticenew.tools.OperationMessages
import com.todokanai.composepracticenew.ui.model.ConflictResolution
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.PendingConflict
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.usecase.ConflictCheckUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.RemoteFileOperationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * 로컬·원격 파일 탐색기 간 선택 상태와 전송 대기 목록을 조율하고, 대기 목록의 다운로드·업로드(충돌 확인 포함)를 실행하는 ViewModel.
 * FILE_BROWSER_GRAPH에 scoped된다.
 * @param localNavigator 다운로드 목적지(현재 로컬 경로)와 로컬 목록을 제공하는 UseCase
 * @param remoteNavigator 업로드 목적지(현재 원격 경로)와 원격 목록을 제공하는 UseCase
 * @param remoteFileOperationUseCase 다운로드·업로드를 실행하는 UseCase
 * @param conflictCheckUseCase 목적지 이름 충돌을 판별하는 UseCase
 * @param messages 작업 완료 메시지 제공자
 */
@HiltViewModel
class FileTransferCoordinatorViewModel @Inject constructor(
    private val localNavigator: FileNavigatorUseCase,
    @RemoteNavigator private val remoteNavigator: FileNavigatorUseCase,
    private val remoteFileOperationUseCase: RemoteFileOperationUseCase,
    private val conflictCheckUseCase: ConflictCheckUseCase,
    private val messages: OperationMessages,
) : ViewModel() {

    private val _localSelectMode = MutableStateFlow<SelectMode>(SelectMode.Default)
    /** 로컬 파일 목록 화면의 현재 선택 모드. */
    val localSelectMode: StateFlow<SelectMode> = _localSelectMode.asStateFlow()

    private val _remoteSelectMode = MutableStateFlow<SelectMode>(SelectMode.Default)
    /** 원격 파일 목록 화면의 현재 선택 모드. */
    val remoteSelectMode: StateFlow<SelectMode> = _remoteSelectMode.asStateFlow()

    private val _localSelectedList = MutableStateFlow<List<FileHolderItem>>(emptyList())
    /** 로컬 화면에서 선택된 파일 항목 목록. */
    val localSelectedList: StateFlow<List<FileHolderItem>> = _localSelectedList.asStateFlow()

    private val _remoteSelectedList = MutableStateFlow<List<FileHolderItem>>(emptyList())
    /** 원격 화면에서 선택된 파일 항목 목록. */
    val remoteSelectedList: StateFlow<List<FileHolderItem>> = _remoteSelectedList.asStateFlow()

    /** 로컬 선택 항목의 경로 집합. 선택 목록 변경 시에만 재계산된다. */
    val localSelectedPaths: StateFlow<Set<String>> = _localSelectedList
        .map { list -> list.mapTo(HashSet()) { it.path } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** 원격 선택 항목의 경로 집합. 선택 목록 변경 시에만 재계산된다. */
    val remoteSelectedPaths: StateFlow<Set<String>> = _remoteSelectedList
        .map { list -> list.mapTo(HashSet()) { it.path } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _downloadPendingList = MutableStateFlow<List<FileHolderItem>>(emptyList())
    /** 원격 → 로컬 다운로드 대기 목록. */
    val downloadPendingList: StateFlow<List<FileHolderItem>> = _downloadPendingList.asStateFlow()

    private val _uploadPendingList = MutableStateFlow<List<FileHolderItem>>(emptyList())
    /** 로컬 → 원격 업로드 대기 목록. */
    val uploadPendingList: StateFlow<List<FileHolderItem>> = _uploadPendingList.asStateFlow()

    private val downloadGate = ConflictGate()
    /** 다운로드 실행 전 사용자 확인을 기다리는 충돌. */
    val downloadConflict: StateFlow<PendingConflict?> = downloadGate.pending

    private val uploadGate = ConflictGate()
    /** 업로드 실행 전 사용자 확인을 기다리는 충돌. */
    val uploadConflict: StateFlow<PendingConflict?> = uploadGate.pending

    fun setLocalSelectMode(mode: SelectMode) { _localSelectMode.value = mode }
    fun setRemoteSelectMode(mode: SelectMode) { _remoteSelectMode.value = mode }

    /** 호출부가 토글을 보장해야 한다. 이미 목록에 있는 항목을 다시 추가하면 중복이 발생한다. */
    fun addToLocalList(item: FileHolderItem) { _localSelectedList.update { it + item } }
    fun removeFromLocalList(item: FileHolderItem) { _localSelectedList.update { it - item } }
    fun clearLocalList() { _localSelectedList.value = emptyList() }

    /** 호출부가 토글을 보장해야 한다. 이미 목록에 있는 항목을 다시 추가하면 중복이 발생한다. */
    fun addToRemoteList(item: FileHolderItem) { _remoteSelectedList.update { it + item } }
    fun removeFromRemoteList(item: FileHolderItem) { _remoteSelectedList.update { it - item } }
    fun clearRemoteList() { _remoteSelectedList.value = emptyList() }

    /** 로컬 선택 목록을 업로드 대기 목록으로 옮기고 양쪽 화면의 선택 모드를 전환한다. */
    fun enterUploadMode() {
        _uploadPendingList.value = _localSelectedList.value
        _remoteSelectMode.value = SelectMode.Confirm.Upload
        _localSelectMode.value = SelectMode.Default
        _localSelectedList.value = emptyList()
    }

    /** items를 다운로드 대기 목록으로 설정하고 양쪽 화면의 선택 모드를 전환한다. */
    fun enterDownloadMode(items: List<FileHolderItem>) {
        _downloadPendingList.value = items
        _localSelectMode.value = SelectMode.Confirm.Download
        _remoteSelectMode.value = SelectMode.Default
        _remoteSelectedList.value = emptyList()
    }

    /** 로컬 선택 목록을 일괄 교체한다. 드래그 범위 선택 시 clearList + 반복 addToList 대신 사용한다. */
    fun setLocalSelectedList(list: List<FileHolderItem>) { _localSelectedList.value = list }

    /** 원격 선택 목록을 일괄 교체한다. 드래그 범위 선택 시 clearList + 반복 addToList 대신 사용한다. */
    fun setRemoteSelectedList(list: List<FileHolderItem>) { _remoteSelectedList.value = list }

    /**
     * 다운로드 대기 목록을 현재 로컬 경로로 다운로드한다. 이름 충돌이 있으면 [downloadConflict]로 확인을 요청한다.
     * 실행 시 대기 목록을 비우고, 확인 취소 시에는 대기 목록을 유지한다.
     */
    fun confirmDownload() {
        val pending = _downloadPendingList.value
        val localNames = localNavigator.fileList.value.mapTo(HashSet()) { it.name }
        downloadGate.request(pending, conflictsIn(pending, localNames)) { skip ->
            download(pending.excluding(skip))
            _downloadPendingList.value = emptyList()
        }
    }

    /**
     * 업로드 대기 목록을 현재 원격 경로로 업로드한다. 이름 충돌이 있으면 [uploadConflict]로 확인을 요청한다.
     * 실행 시 대기 목록을 비우고, 확인 취소 시에는 대기 목록을 유지한다.
     */
    fun confirmUpload() {
        val pending = _uploadPendingList.value
        val remoteNames = remoteNavigator.fileList.value.mapTo(HashSet()) { it.name }
        uploadGate.request(pending, conflictsIn(pending, remoteNames)) { skip ->
            upload(pending.excluding(skip))
            _uploadPendingList.value = emptyList()
        }
    }

    /** 대기 중인 다운로드 충돌을 사용자 선택대로 처리한다. */
    fun resolveDownloadConflict(resolution: ConflictResolution) { downloadGate.resolve(resolution) }

    /** 대기 중인 업로드 충돌을 사용자 선택대로 처리한다. */
    fun resolveUploadConflict(resolution: ConflictResolution) { uploadGate.resolve(resolution) }

    /** [pending] 중 [destinationNames]에 같은 이름이 있는 항목을 반환한다. */
    private fun conflictsIn(pending: List<FileHolderItem>, destinationNames: Set<String>): List<FileHolderItem> {
        val conflictNames = conflictCheckUseCase.checkCopyMoveConflicts(pending.map { it.name }, destinationNames)
        return pending.filter { it.name in conflictNames }
    }

    /** 이 목록에서 [skip]과 경로가 같은 항목을 제외한다. */
    private fun List<FileHolderItem>.excluding(skip: List<FileHolderItem>): List<FileHolderItem> {
        val skipPaths = skip.mapTo(HashSet()) { it.path }
        return filter { it.path !in skipPaths }
    }

    /** items를 현재 로컬 경로에 다운로드한다. */
    private fun download(items: List<FileHolderItem>) {
        val localPath = localNavigator.currentPath.value ?: return
        items.forEach { item ->
            remoteFileOperationUseCase.download(
                remotePath = item.path,
                localDestPath = localPath,
                isDirectory = item.isDirectory,
                completionMessage = messages.notiDownloadComplete,
                onRefresh = { localNavigator.refresh() }
            )
        }
    }

    /** items를 현재 원격 경로에 업로드한다. */
    private fun upload(items: List<FileHolderItem>) {
        val remotePath = remoteNavigator.currentPath.value ?: return
        items.forEach { item ->
            remoteFileOperationUseCase.upload(
                localPath = item.path,
                remoteDestPath = remotePath,
                completionMessage = messages.notiUploadComplete,
                onRefresh = { remoteNavigator.refresh() }
            )
        }
    }
}
