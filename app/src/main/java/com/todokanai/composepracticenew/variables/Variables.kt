package com.todokanai.composepracticenew.variables

import android.os.Environment
import com.todokanai.composepracticenew.application.MyApplication
import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.data.dataclass.FileHolderItem
import com.todokanai.composepracticenew.data.dataclass.ProgressState
import com.todokanai.composepracticenew.data.datastore.DataStoreRepository
import com.todokanai.composepracticenew.myobjects.Constants
import com.todokanai.composepracticenew.tools.independent.dirTree_td
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class Variables {
    companion object{
        private val defaultStorage = Environment.getExternalStorageDirectory()

        private val _selectedList = MutableStateFlow<List<File>>(emptyList())        // Variables에 배치하는게 맞는지 아직 몰?루
        val selectedList : StateFlow<List<File>>
            get() = _selectedList


        private val _fileHolderItemList = MutableStateFlow<List<FileHolderItem>>(emptyList())
        val fileHolderItemList : StateFlow<List<FileHolderItem>>
            get() = _fileHolderItemList


        private val _currentPath = MutableStateFlow<File>(defaultStorage)
        val currentPath : StateFlow<File>
            get() = _currentPath


        private val _dirTree = MutableStateFlow<List<File>>(dirTree_td(defaultStorage))     // 초기값은 최초 화면 디렉토리로
        val dirTree : StateFlow<List<File>>
            get() = _dirTree


        private val _selectMode = MutableStateFlow<Int>(Constants.DEFAULT_MODE)
        val selectMode: StateFlow<Int>
            get() = _selectMode



        private val _myProgressState = MutableStateFlow<ProgressState>(ProgressState(
            null,
            totalSize = null,
            currentSize = null,
            listSize = null,
            currentIndex = null
        ))
        val myProgressState : StateFlow<ProgressState>
            get() = _myProgressState


        /** 동시에 여러 종류의 ProgressState가 발생할 경우를 대비한 스페어(Spare) **/
        private val _myProgressStateSpare = MutableStateFlow<ProgressState>(ProgressState(
            null,
            totalSize = null,
            currentSize = null,
            listSize = null,
            currentIndex = null
        ))
        val myProgressStateSpare : StateFlow<ProgressState>
            get() = _myProgressStateSpare
    }

    private val converter = DataConverter(MyApplication.appContext)
    private val dsRepo = DataStoreRepository()
    //--------------------------------------------------------
    // setter 구간
    private fun selectedListSetter(files: List<File>){
        _selectedList.value = files
    }

    fun addToSelectedList(file: File){
        selectedListSetter(selectedList.value.plus(file))
    }

    fun removeFromSelectedList(file: File){
        selectedListSetter(selectedList.value.minus(file))
    }

    fun clearSelectedList(){
        selectedListSetter(emptyList())
    }


    fun setProgressState(progressState:ProgressState){
        _myProgressState.value = progressState
    }

    fun setSelectMode(mode:Int){
        _selectMode.value = mode
    }

    /** fileHolderItemList의 setter*/
    fun setFileHolderItemList(sortMode:String) {
        currentPath.value.listFiles()?.let { files ->
            _fileHolderItemList.value = converter.fileHolderItemList(files,sortMode)         // 아직 불안정?
        }
    }

    suspend fun setCurrentPath(file:File){
        file.listFiles()?.let {         // file.listFiles()?.let -> 접근 가능한 경로일 경우
            _currentPath.value = file
            setDirTree(file)
            setFileHolderItemList(dsRepo.sortBy())
        }
    }


    /** private으로 냅둘것 */
    private fun setDirTree(currentPath: File){
        _dirTree.value = dirTree_td(currentPath)
    }

    // setter 구간 끝
    //---------------------------------

}