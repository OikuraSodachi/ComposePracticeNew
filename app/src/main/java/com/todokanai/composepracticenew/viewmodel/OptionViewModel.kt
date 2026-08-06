package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.myobjects.Constants
import com.todokanai.composepracticenew.usecase.SortModeUseCase
import com.todokanai.composepracticenew.variables.FileListSorter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class OptionViewModel @Inject constructor(
    private val sortModeUseCase: SortModeUseCase
) : ViewModel() {

    /** 옵션 바 화면에 필요한 UI 상태를 담는 클래스. */
    data class UiState(
        val sortMode: String = Constants.BY_DEFAULT
    )

    val uiState: StateFlow<UiState> = sortModeUseCase.sortBy
        .map { UiState(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState()
        )

    fun newFolder(name: String) {} // stub — not yet implemented

    fun sortModeCallbackList() = FileListSorter().getSortModeCallbackList { sortModeUseCase.saveSortBy(it) }
}
