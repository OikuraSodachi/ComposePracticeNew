package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.myobjects.Constants
import com.todokanai.composepracticenew.repository.FileActionRepository
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject

@HiltViewModel
class BottomButtonsViewModel @Inject constructor(
    private val nav: FileNavigatorRepository,
    private val fileAction: FileActionRepository
) : ViewModel() {

    /** 하단 버튼 영역 화면에 필요한 UI 상태를 담는 클래스. */
    data class UiState(
        val currentPath: File = File("/")
    )

    val uiState: StateFlow<UiState> = nav.currentFile
        .map { UiState(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState()
        )

    fun confirm(selectedList: List<File>, selectMode: Int, currentPath: File) {
        viewModelScope.launch {
            val list = selectedList.toTypedArray()
            when (selectMode) {
                Constants.CONFIRM_MODE_COPY -> fileAction.copyAction(list, currentPath)
                Constants.CONFIRM_MODE_MOVE -> fileAction.moveAction(list, currentPath)
                Constants.CONFIRM_MODE_UNZIP -> fileAction.unzipAction(ZipFile(list.first()), currentPath, unzipHere = false)
                Constants.CONFIRM_MODE_UNZIP_HERE -> fileAction.unzipAction(ZipFile(list.first()), currentPath, unzipHere = true)
            }
        }
    }

    fun zip(selectedList: List<File>, name: String) {
        viewModelScope.launch {
            fileAction.zipAction(selectedList.toTypedArray(), name)
        }
    }

    fun rename(file: File, name: String) {
        viewModelScope.launch {
            fileAction.renameAction(file, name)
        }
    }

    fun delete(selectedList: List<File>) {
        viewModelScope.launch {
            fileAction.deleteAction(selectedList.toTypedArray())
        }
    }
}
