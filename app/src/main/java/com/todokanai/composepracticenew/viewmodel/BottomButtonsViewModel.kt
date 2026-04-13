package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.data.dataclass.ProgressState
import com.todokanai.composepracticenew.myobjects.Constants
import com.todokanai.composepracticenew.tools.FileAction
import com.todokanai.composepracticenew.variables.Variables
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject

@HiltViewModel
class BottomButtonsViewModel @Inject constructor() : ViewModel(){

    val selectMode = Variables.selectMode
    val currentPath = Variables.currentPath
    private val vars = Variables()
    private val fAction = FileAction({updateCurrentPath(it)},currentPath)

    private val selectedList = Variables.selectedList

    private fun updateCurrentPath(file: File){
        viewModelScope.launch {
            vars.setCurrentPath(file)
        }
    }

    private fun changeSelectMode(mode:Int) {
        vars.setSelectMode(mode)
    }

    private fun setProgressState(progressState: ProgressState){
        vars.setProgressState(progressState)
    }

    /*
    fun confirm(selectedList:List<File>, currentPath: File){
        viewModelScope.launch {
            val list = selectedList.toTypedArray()

            when (selectMode.value) {       // selectMode : StateFlow<Int>
                Constants.CONFIRM_MODE_COPY -> {
                    fAction.copyAction(list, currentPath, {setProgressState(it)})
                }
                Constants.CONFIRM_MODE_MOVE -> {
                    fAction.moveAction(list, currentPath, {setProgressState(it)})

                }
                Constants.CONFIRM_MODE_UNZIP -> {
                    val zipFile = ZipFile(list.first())
                    //  fAction.unzipAction(list.first(), currentPath, unzipHere = false)
                    fAction.unzipAction(zipFile, currentPath, unzipHere = false,{setProgressState(it)})
                }
                Constants.CONFIRM_MODE_UNZIP_HERE -> {
                    val zipFile = ZipFile(list.first())
                    //  fAction.unzipAction(list.first(), currentPath, unzipHere = false)
                    fAction.unzipAction(zipFile, currentPath, unzipHere = false,{setProgressState(it)})
                }
            }
            changeSelectMode(Constants.DEFAULT_MODE)
        }
    }

     */

    fun confirm(currentPath: File){
        viewModelScope.launch {
            val list = selectedList.value.toTypedArray()

            when (selectMode.value) {       // selectMode : StateFlow<Int>
                Constants.CONFIRM_MODE_COPY -> {
                    fAction.copyAction(list, currentPath, {setProgressState(it)})
                }
                Constants.CONFIRM_MODE_MOVE -> {
                    fAction.moveAction(list, currentPath, {setProgressState(it)})

                }
                Constants.CONFIRM_MODE_UNZIP -> {
                    val zipFile = ZipFile(list.first())
                    //  fAction.unzipAction(list.first(), currentPath, unzipHere = false)
                    fAction.unzipAction(zipFile, currentPath, unzipHere = false,{setProgressState(it)})
                }
                Constants.CONFIRM_MODE_UNZIP_HERE -> {
                    val zipFile = ZipFile(list.first())
                    //  fAction.unzipAction(list.first(), currentPath, unzipHere = false)
                    fAction.unzipAction(zipFile, currentPath, unzipHere = false,{setProgressState(it)})
                }
            }
            changeSelectMode(Constants.DEFAULT_MODE)
        }
    }


    fun moveMode() = changeSelectMode(Constants.CONFIRM_MODE_MOVE)

    fun copyMode() = changeSelectMode(Constants.CONFIRM_MODE_COPY)

    /**
     * 넘겨줄 file이 압축해제 가능한지 체크할것
     * 웬만하면 "압축해제 가능한 단일 파일"이 선택된 상황에만 unzip 활성화하기
     */
    fun unzipMode() = changeSelectMode(Constants.CONFIRM_MODE_UNZIP)

    fun unzipHereMode() = changeSelectMode(Constants.CONFIRM_MODE_UNZIP_HERE)

    fun cancel() = changeSelectMode(Constants.DEFAULT_MODE)

    fun zip(name:String){
        viewModelScope.launch {
            fAction.zipAction(selectedList.value.toTypedArray(), name,{setProgressState(it)})
            changeSelectMode(Constants.DEFAULT_MODE)
        }
    }

    fun rename(name:String){
        viewModelScope.launch {
            fAction.renameAction(selectedList.value.first(), name)
            changeSelectMode(Constants.DEFAULT_MODE)
        }
    }

    fun delete(){
        viewModelScope.launch {
            fAction.deleteAction(selectedList.value.toTypedArray(),{setProgressState(it)})
            changeSelectMode(Constants.DEFAULT_MODE)
        }
    }
}