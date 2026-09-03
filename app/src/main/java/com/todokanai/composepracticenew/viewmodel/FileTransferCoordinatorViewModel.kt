package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import com.todokanai.composepracticenew.myobjects.AppConstants
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** 로컬·원격 파일 탐색기 간 선택 상태와 전송 대기 목록을 조율하는 ViewModel. FILE_BROWSER_GRAPH에 scoped된다. */
@HiltViewModel
class FileTransferCoordinatorViewModel @Inject constructor() : ViewModel() {

    private val _localSelectMode = MutableStateFlow(AppConstants.DEFAULT_MODE)
    /** 로컬 파일 목록 화면의 현재 선택 모드. */
    val localSelectMode: StateFlow<Int> = _localSelectMode.asStateFlow()

    private val _remoteSelectMode = MutableStateFlow(AppConstants.DEFAULT_MODE)
    /** 원격 파일 목록 화면의 현재 선택 모드. */
    val remoteSelectMode: StateFlow<Int> = _remoteSelectMode.asStateFlow()

    private val _localSelectedList = MutableStateFlow<List<FileHolderItem>>(emptyList())
    /** 로컬 화면에서 선택된 파일 항목 목록. */
    val localSelectedList: StateFlow<List<FileHolderItem>> = _localSelectedList.asStateFlow()

    private val _remoteSelectedList = MutableStateFlow<List<FileHolderItem>>(emptyList())
    /** 원격 화면에서 선택된 파일 항목 목록. */
    val remoteSelectedList: StateFlow<List<FileHolderItem>> = _remoteSelectedList.asStateFlow()

    private val _downloadPendingList = MutableStateFlow<List<FileHolderItem>>(emptyList())
    /** 원격 → 로컬 다운로드 대기 목록. */
    val downloadPendingList: StateFlow<List<FileHolderItem>> = _downloadPendingList.asStateFlow()

    private val _uploadPendingList = MutableStateFlow<List<FileHolderItem>>(emptyList())
    /** 로컬 → 원격 업로드 대기 목록. */
    val uploadPendingList: StateFlow<List<FileHolderItem>> = _uploadPendingList.asStateFlow()

    fun setLocalSelectMode(mode: Int) { _localSelectMode.value = mode }
    fun setRemoteSelectMode(mode: Int) { _remoteSelectMode.value = mode }

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
        _remoteSelectMode.value = AppConstants.CONFIRM_MODE_UPLOAD
        _localSelectMode.value = AppConstants.DEFAULT_MODE
        _localSelectedList.value = emptyList()
    }

    /** items를 다운로드 대기 목록으로 설정하고 양쪽 화면의 선택 모드를 전환한다. */
    fun enterDownloadMode(items: List<FileHolderItem>) {
        _downloadPendingList.value = items
        _localSelectMode.value = AppConstants.CONFIRM_MODE_DOWNLOAD
        _remoteSelectMode.value = AppConstants.DEFAULT_MODE
        _remoteSelectedList.value = emptyList()
    }

    fun clearDownloadPending() { _downloadPendingList.value = emptyList() }
    fun clearUploadPending() { _uploadPendingList.value = emptyList() }
}
