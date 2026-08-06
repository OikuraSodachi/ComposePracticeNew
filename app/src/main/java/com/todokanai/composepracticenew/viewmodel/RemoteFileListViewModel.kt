package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.di.RemoteNavigator
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.fileexplorer.FileEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 원격 파일 목록 화면의 UI 상태와 네비게이션을 관리하는 ViewModel. 파일 조작 기능은 제공하지 않는다. */
@HiltViewModel
class RemoteFileListViewModel @Inject constructor(
    @RemoteNavigator private val fileNavigatorUseCase: FileNavigatorUseCase
) : ViewModel() {

    /** 원격 파일 목록 화면에 필요한 UI 상태를 담는 클래스. */
    data class UiState(
        val fileHolderItemList: List<FileHolderItem> = emptyList()
    )

    val uiState: StateFlow<UiState> = fileNavigatorUseCase.fileList
        .map { UiState(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState()
        )

    /** 원격 경로 breadcrumb 목록. */
    val dirTree: StateFlow<List<FileEntry>> = fileNavigatorUseCase.dirTree
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** breadcrumb 항목 클릭 시 해당 경로로 이동한다. */
    fun navigateToDir(entry: FileEntry) {
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
}
