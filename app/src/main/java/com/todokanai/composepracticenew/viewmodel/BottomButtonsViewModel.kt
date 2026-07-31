package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File
import javax.inject.Inject

@HiltViewModel
class BottomButtonsViewModel @Inject constructor(
    private val nav: FileNavigatorRepository
) : ViewModel() {

    /** 하단 버튼 영역 화면에 필요한 UI 상태를 담는 클래스. */
    data class UiState(
        val currentPath: File = File("/")
    )

    val uiState: StateFlow<UiState> = nav.currentDirectory
        .map { UiState(File(it)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState()
        )

    fun confirm(selectedList: List<File>, selectMode: Int, currentPath: File) {} // stub — not yet implemented

    fun zip(selectedList: List<File>, name: String) {} // stub — not yet implemented

    fun rename(file: File, name: String) {} // stub — not yet implemented

    fun delete(selectedList: List<File>) {} // stub — not yet implemented
}
