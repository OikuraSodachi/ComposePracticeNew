package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.fileexplorer.FileEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DirectoryViewModel @Inject constructor(
    private val fileNavigatorUseCase: FileNavigatorUseCase
) : ViewModel() {

    /** 경로 breadcrumb 화면에 필요한 UI 상태를 담는 클래스. */
    data class UiState(
        val dirTree: List<FileEntry> = emptyList()
    )

    val uiState: StateFlow<UiState> = fileNavigatorUseCase.dirTree
        .map { UiState(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState()
        )

    fun updateCurrentPath(entry: FileEntry) {
        viewModelScope.launch {
            fileNavigatorUseCase.setLocalPath(entry.path)
        }
    }
}
