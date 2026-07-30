package com.todokanai.composepracticenew.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.data.dataclass.ProgressState
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_COPY
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_DELETE
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_MOVE
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_UNZIP
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_ZIP
import com.todokanai.composepracticenew.myobjects.Constants.DEFAULT_MODE
import com.todokanai.composepracticenew.myobjects.Constants.MULTI_SELECT_MODE
import com.todokanai.composepracticenew.repository.FileNavigator
import com.todokanai.composepracticenew.repository.ProgressTracker
import com.todokanai.composepracticenew.repository.SelectionState
import com.todokanai.composepracticenew.tools.FileAction
import com.todokanai.composepracticenew.tools.LogTool
import com.todokanai.composepracticenew.tools.MyNotification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FileListViewModel @Inject constructor(
    private val nav: FileNavigator,
    private val sel: SelectionState,
    private val prog: ProgressTracker,
    private val myNoti: MyNotification,
    private val logTool: LogTool
) : ViewModel() {

    private val fAction = FileAction({ updateCurrentPath(it) }, nav.currentPath, myNoti, logTool)

    private lateinit var selectedItem: File
    val selectMode = sel.selectMode

    fun addToList(file: File) = sel.addToSelectedList(file)
    fun removeFromList(file: File) = sel.removeFromSelectedList(file)
    fun clearList() = sel.clearSelectedList()

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
            nav.setCurrentPath(file)
        }
    }

    fun onItemClick(context: Context, selected: File) {
        viewModelScope.launch {
            selectedItem = selected
            when (selectMode.value) {
                DEFAULT_MODE -> fAction.openAction(context, selected)
                MULTI_SELECT_MODE -> { }
                else -> if (selected.isDirectory) fAction.openAction(context, selected)
            }
        }
    }

    fun onItemLongClick() {
        if (selectMode.value == DEFAULT_MODE) {
            sel.setSelectMode(MULTI_SELECT_MODE)
        }
    }
}
