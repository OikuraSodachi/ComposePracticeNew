package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.data.dataclass.ProgressState
import com.todokanai.composepracticenew.myobjects.Constants
import com.todokanai.composepracticenew.repository.FileNavigator
import com.todokanai.composepracticenew.repository.ProgressTracker
import com.todokanai.composepracticenew.tools.FileAction
import com.todokanai.composepracticenew.tools.LogTool
import com.todokanai.composepracticenew.tools.MyNotification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject

@HiltViewModel
class BottomButtonsViewModel @Inject constructor(
    private val nav: FileNavigator,
    private val prog: ProgressTracker,
    private val myNoti: MyNotification,
    private val logTool: LogTool
) : ViewModel() {

    val currentPath = nav.currentPath
    private val fAction = FileAction({ updateCurrentPath(it) }, nav.currentPath, myNoti, logTool)

    private fun updateCurrentPath(file: File) {
        viewModelScope.launch { nav.setCurrentPath(file) }
    }

    private fun setProgressState(progressState: ProgressState) = prog.setProgressState(progressState)

    fun confirm(selectedList: List<File>, selectMode: Int, currentPath: File) {
        viewModelScope.launch {
            val list = selectedList.toTypedArray()
            when (selectMode) {
                Constants.CONFIRM_MODE_COPY -> fAction.copyAction(list, currentPath, { setProgressState(it) })
                Constants.CONFIRM_MODE_MOVE -> fAction.moveAction(list, currentPath, { setProgressState(it) })
                Constants.CONFIRM_MODE_UNZIP -> fAction.unzipAction(ZipFile(list.first()), currentPath, unzipHere = false, { setProgressState(it) })
                Constants.CONFIRM_MODE_UNZIP_HERE -> fAction.unzipAction(ZipFile(list.first()), currentPath, unzipHere = true, { setProgressState(it) })
            }
        }
    }

    fun zip(selectedList: List<File>, name: String) {
        viewModelScope.launch {
            fAction.zipAction(selectedList.toTypedArray(), name, { setProgressState(it) })
        }
    }

    fun rename(file: File, name: String) {
        viewModelScope.launch {
            fAction.renameAction(file, name)
        }
    }

    fun delete(selectedList: List<File>) {
        viewModelScope.launch {
            fAction.deleteAction(selectedList.toTypedArray(), { setProgressState(it) })
        }
    }
}
