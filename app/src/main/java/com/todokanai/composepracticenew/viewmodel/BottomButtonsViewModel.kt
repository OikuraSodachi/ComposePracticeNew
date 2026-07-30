package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.data.dataclass.ProgressState
import com.todokanai.composepracticenew.myobjects.Constants
import com.todokanai.composepracticenew.tools.FileAction
import com.todokanai.composepracticenew.tools.LogTool
import com.todokanai.composepracticenew.tools.MyNotification
import com.todokanai.composepracticenew.variables.Variables
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject

@HiltViewModel
class BottomButtonsViewModel @Inject constructor(
    private val vars: Variables,
    private val myNoti: MyNotification,
    private val logTool: LogTool
) : ViewModel() {

    val selectMode = vars.selectMode
    val currentPath = vars.currentPath
    private val fAction = FileAction({ updateCurrentPath(it) }, vars.currentPath, myNoti, logTool)

    private val selectedList = vars.selectedList

    private fun updateCurrentPath(file: File) {
        viewModelScope.launch { vars.setCurrentPath(file) }
    }

    private fun changeSelectMode(mode: Int) = vars.setSelectMode(mode)

    private fun setProgressState(progressState: ProgressState) = vars.setProgressState(progressState)

    fun confirm(currentPath: File) {
        viewModelScope.launch {
            val list = selectedList.value.toTypedArray()
            when (selectMode.value) {
                Constants.CONFIRM_MODE_COPY -> fAction.copyAction(list, currentPath, { setProgressState(it) })
                Constants.CONFIRM_MODE_MOVE -> fAction.moveAction(list, currentPath, { setProgressState(it) })
                Constants.CONFIRM_MODE_UNZIP -> fAction.unzipAction(ZipFile(list.first()), currentPath, unzipHere = false, { setProgressState(it) })
                Constants.CONFIRM_MODE_UNZIP_HERE -> fAction.unzipAction(ZipFile(list.first()), currentPath, unzipHere = false, { setProgressState(it) })
            }
            changeSelectMode(Constants.DEFAULT_MODE)
        }
    }

    fun moveMode() = changeSelectMode(Constants.CONFIRM_MODE_MOVE)
    fun copyMode() = changeSelectMode(Constants.CONFIRM_MODE_COPY)
    fun unzipMode() = changeSelectMode(Constants.CONFIRM_MODE_UNZIP)
    fun unzipHereMode() = changeSelectMode(Constants.CONFIRM_MODE_UNZIP_HERE)
    fun cancel() = changeSelectMode(Constants.DEFAULT_MODE)

    fun zip(name: String) {
        viewModelScope.launch {
            fAction.zipAction(selectedList.value.toTypedArray(), name, { setProgressState(it) })
            changeSelectMode(Constants.DEFAULT_MODE)
        }
    }

    fun rename(name: String) {
        viewModelScope.launch {
            fAction.renameAction(selectedList.value.first(), name)
            changeSelectMode(Constants.DEFAULT_MODE)
        }
    }

    fun delete() {
        viewModelScope.launch {
            fAction.deleteAction(selectedList.value.toTypedArray(), { setProgressState(it) })
            changeSelectMode(Constants.DEFAULT_MODE)
        }
    }
}
