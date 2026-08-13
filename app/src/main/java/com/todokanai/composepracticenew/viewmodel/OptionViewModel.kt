package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.myobjects.Constants
import com.todokanai.composepracticenew.usecase.FileActionUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.SortModeUseCase
import com.todokanai.composepracticenew.variables.FileListSorter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OptionViewModel @Inject constructor(
    private val sortModeUseCase: SortModeUseCase,
    private val fileActionUseCase: FileActionUseCase,
    private val fileNavigatorUseCase: FileNavigatorUseCase
) : ViewModel() {

    /** 옵션 바 화면에 필요한 UI 상태를 담는 클래스. */
    data class UiState(
        val sortMode: String = Constants.BY_DEFAULT
    )

    private val _errorMessage = MutableStateFlow<String?>(null)

    /** 폴더 생성 실패 시 표시할 오류 메시지. null이면 표시 없음. */
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** 오류 메시지를 소비한 뒤 초기화한다. */
    fun clearError() { _errorMessage.value = null }

    val uiState: StateFlow<UiState> = sortModeUseCase.sortBy
        .map { UiState(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState()
        )

    /** 현재 경로에 [name] 이름의 새 폴더를 생성한다. */
    fun newFolder(name: String) {
        val currentPath = fileNavigatorUseCase.currentPath.value ?: return
        viewModelScope.launch {
            fileActionUseCase.makeDirectory(currentPath, name).collect { state ->
                state.error?.let { _errorMessage.value = it }
            }
            fileNavigatorUseCase.refresh()
        }
    }

    fun sortModeCallbackList() = FileListSorter().getSortModeCallbackList { sortModeUseCase.saveSortBy(it) }
}
