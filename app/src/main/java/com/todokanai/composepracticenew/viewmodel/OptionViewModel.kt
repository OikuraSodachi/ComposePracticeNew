package com.todokanai.composepracticenew.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.data.dataclass.StorageHolderItem
import com.todokanai.composepracticenew.data.datastore.DataStoreRepository
import com.todokanai.composepracticenew.myobjects.Constants
import com.todokanai.composepracticenew.repository.FileNavigator
import com.todokanai.composepracticenew.tools.FileAction
import com.todokanai.composepracticenew.tools.LogTool
import com.todokanai.composepracticenew.tools.MyNotification
import com.todokanai.composepracticenew.tools.independent.exit_td
import com.todokanai.composepracticenew.variables.FileListSorter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class OptionViewModel @Inject constructor(
    private val nav: FileNavigator,
    private val dsRepo: DataStoreRepository,
    private val myNoti: MyNotification,
    private val logTool: LogTool
) : ViewModel() {

    val storageList = MainViewModel.physicalStorageList
    private val fAction = FileAction({}, nav.currentPath, myNoti, logTool)

    val sortMode = dsRepo.sortBy.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(0),
        initialValue = Constants.BY_DEFAULT
    )

    private fun updateCurrentPath(file: File) {
        viewModelScope.launch { nav.setCurrentPath(file) }
    }

    fun toPair(storageList: List<StorageHolderItem>) = listToPair(storageList)

    fun newFolder(name: String) = fAction.newFolderAction(nav.currentPath.value, name)

    fun exit(activity: Activity) = exit_td(activity)

    private fun onUpdateSortMode(sortMode: String) {
        dsRepo.saveSortBy(sortMode)
        nav.setFileHolderItemList(sortMode)
    }

    fun sortModeCallbackList() = FileListSorter().getSortModeCallbackList({ onUpdateSortMode(it) })

    private fun listToPair(storageList: List<StorageHolderItem>): List<Pair<String, () -> Unit>> {
        val result = mutableListOf<Pair<String, () -> Unit>>()
        storageList.forEach {
            result.add(Pair(it.absolutePath, { updateCurrentPath(it.storage) }))
        }
        return result
    }
}
