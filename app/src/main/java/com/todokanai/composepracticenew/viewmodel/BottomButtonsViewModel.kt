package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.myobjects.Constants
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.usecase.CopyFilesUseCase
import com.todokanai.composepracticenew.usecase.DeleteFilesUseCase
import com.todokanai.composepracticenew.usecase.MoveFilesUseCase
import com.todokanai.composepracticenew.usecase.RenameFileUseCase
import com.todokanai.composepracticenew.usecase.UnzipFilesUseCase
import com.todokanai.composepracticenew.usecase.ZipFilesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject

@HiltViewModel
class BottomButtonsViewModel @Inject constructor(
    private val nav: FileNavigatorRepository,
    private val copyFilesUseCase: CopyFilesUseCase,
    private val moveFilesUseCase: MoveFilesUseCase,
    private val deleteFilesUseCase: DeleteFilesUseCase,
    private val renameFileUseCase: RenameFileUseCase,
    private val zipFilesUseCase: ZipFilesUseCase,
    private val unzipFilesUseCase: UnzipFilesUseCase
) : ViewModel() {

    val currentPath = nav.currentPath

    fun confirm(selectedList: List<File>, selectMode: Int, currentPath: File) {
        viewModelScope.launch {
            val list = selectedList.toTypedArray()
            when (selectMode) {
                Constants.CONFIRM_MODE_COPY -> copyFilesUseCase(list, currentPath)
                Constants.CONFIRM_MODE_MOVE -> moveFilesUseCase(list, currentPath)
                Constants.CONFIRM_MODE_UNZIP -> unzipFilesUseCase(ZipFile(list.first()), currentPath, unzipHere = false)
                Constants.CONFIRM_MODE_UNZIP_HERE -> unzipFilesUseCase(ZipFile(list.first()), currentPath, unzipHere = true)
            }
        }
    }

    fun zip(selectedList: List<File>, name: String) {
        viewModelScope.launch {
            zipFilesUseCase(selectedList.toTypedArray(), name)
        }
    }

    fun rename(file: File, name: String) {
        viewModelScope.launch {
            renameFileUseCase(file, name)
        }
    }

    fun delete(selectedList: List<File>) {
        viewModelScope.launch {
            deleteFilesUseCase(selectedList.toTypedArray())
        }
    }
}
