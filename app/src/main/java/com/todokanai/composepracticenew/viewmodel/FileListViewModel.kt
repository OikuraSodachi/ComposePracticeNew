package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.model.ProgressStateEntity
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.model.toEntity
import com.todokanai.composepracticenew.myobjects.Constants.DEFAULT_MODE
import com.todokanai.composepracticenew.myobjects.Constants.MULTI_SELECT_MODE
import com.todokanai.composepracticenew.tools.MyNotification
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.OpenFileUseCase
import com.todokanai.composepracticenew.usecase.ProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FileListViewModel @Inject constructor(
    private val fileNavigatorUseCase: FileNavigatorUseCase,
    private val progressUseCase: ProgressUseCase,
    private val myNoti: MyNotification,
    private val openFileUseCase: OpenFileUseCase
) : ViewModel() {

    /** 파일 목록 화면에 필요한 UI 상태를 담는 클래스. */
    data class UiState(
        val fileHolderItemList: List<FileHolderItem> = emptyList()
    )

    val uiState: StateFlow<UiState> = fileNavigatorUseCase.fileList
        .map { list -> UiState(list.map { FileHolderItem.from(it) }) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState()
        )

    /** 동시에 진행 중인 파일 작업들의 progress 상태. actionKey를 키로 사용한다. */
    val progressMap: StateFlow<Map<Int, ProgressStateEntity>> = progressUseCase.progressMap
        .map { map -> map.mapValues { (_, state) -> state.toEntity() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )

    /** 알림 클릭으로 다이얼로그를 다시 표시해야 할 때 설정되는 actionKey. null이면 신호 없음. */
    private val _showProgressDialogForKey = MutableStateFlow<Int?>(null)
    val showProgressDialogForKey: StateFlow<Int?> = _showProgressDialogForKey.asStateFlow()

    fun requestShowProgressDialog(actionKey: Int) {
        _showProgressDialogForKey.value = actionKey
    }

    fun onProgressDialogShown() {
        _showProgressDialogForKey.value = null
    }

    /** pendingList 중 현재 로컬 디렉터리에 이미 같은 이름으로 존재하는 파일 목록을 반환한다. */
    fun getDownloadConflicts(pendingList: List<FileHolderItem>): List<FileHolderItem> {
        val localFiles = uiState.value.fileHolderItemList
        return pendingList.filter { remote -> localFiles.any { local -> local.name == remote.name } }
    }

    fun updateCurrentPath(file: File) {
        viewModelScope.launch {
            fileNavigatorUseCase.setPath(file.absolutePath)
        }
    }

    fun onItemClick(selected: FileHolderItem, selectMode: Int) {
        viewModelScope.launch {
            when (selectMode) {
                DEFAULT_MODE -> openFileUseCase.open(selected.toDomain())
                MULTI_SELECT_MODE -> { }
                else -> if (selected.isDirectory) openFileUseCase.open(selected.toDomain())
            }
        }
    }

}
