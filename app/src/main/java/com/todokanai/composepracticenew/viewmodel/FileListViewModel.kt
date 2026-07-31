package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_COPY
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_DELETE
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_MOVE
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_UNZIP
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_ZIP
import com.todokanai.composepracticenew.myobjects.Constants.DEFAULT_MODE
import com.todokanai.composepracticenew.myobjects.Constants.MULTI_SELECT_MODE
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.ProgressTracker
import com.todokanai.composepracticenew.tools.MyNotification
import com.todokanai.composepracticenew.usecase.NavigateToDirectoryUseCase
import com.todokanai.composepracticenew.usecase.OpenFileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FileListViewModel @Inject constructor(
    private val nav: FileNavigatorRepository,
    private val prog: ProgressTracker,
    private val myNoti: MyNotification,
    private val navigateToDirectoryUseCase: NavigateToDirectoryUseCase,
    private val openFileUseCase: OpenFileUseCase
) : ViewModel() {

    private lateinit var selectedItem: File

    val fileHolderItemList = nav.fileHolderItemList
    val progressState = prog.progressState

    fun progressNoti(actionKey: Int, progressState: ProgressState) {
        when (actionKey) {
            ACTION_KEY_COPY -> progressState.progress?.let { myNoti.copyProgressNoti(it) }
            ACTION_KEY_DELETE -> progressState.currentIndex?.let { index ->
                progressState.listSize?.let { size -> myNoti.deleteProgressNoti(index, size) }
            }
            ACTION_KEY_MOVE -> progressState.progress?.let { myNoti.moveProgressNoti(it) }
            ACTION_KEY_ZIP -> progressState.progress?.let { myNoti.zipProgressNoti(it) }
            ACTION_KEY_UNZIP -> progressState.progress?.let { myNoti.unzipProgressNoti(it) }
        }
    }

    fun updateCurrentPath(file: File) {
        viewModelScope.launch {
            navigateToDirectoryUseCase(file)
        }
    }

    fun onItemClick(selected: File, selectMode: Int) {
        viewModelScope.launch {
            selectedItem = selected
            when (selectMode) {
                DEFAULT_MODE -> openFileUseCase(selected)
                MULTI_SELECT_MODE -> { }
                else -> if (selected.isDirectory) openFileUseCase(selected)
            }
        }
    }
}
