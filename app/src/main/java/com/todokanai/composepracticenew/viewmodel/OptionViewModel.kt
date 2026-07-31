package com.todokanai.composepracticenew.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.data.datastore.DataStoreRepository
import com.todokanai.composepracticenew.model.StorageHolderItem
import com.todokanai.composepracticenew.myobjects.Constants
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.StorageRepository
import com.todokanai.composepracticenew.tools.independent.exit_td
import com.todokanai.composepracticenew.usecase.NavigateToDirectoryUseCase
import com.todokanai.composepracticenew.usecase.NewFolderUseCase
import com.todokanai.composepracticenew.usecase.UpdateSortModeUseCase
import com.todokanai.composepracticenew.variables.FileListSorter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OptionViewModel @Inject constructor(
    private val nav: FileNavigatorRepository,
    private val storageRepo: StorageRepository,
    private val dsRepo: DataStoreRepository,
    private val navigateToDirectoryUseCase: NavigateToDirectoryUseCase,
    private val newFolderUseCase: NewFolderUseCase,
    private val updateSortModeUseCase: UpdateSortModeUseCase
) : ViewModel() {

    val storageList = storageRepo.storageList

    val sortMode = dsRepo.sortBy.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(0),
        initialValue = Constants.BY_DEFAULT
    )

    fun toPair(storageList: List<StorageHolderItem>) = listToPair(storageList)

    fun newFolder(name: String) = newFolderUseCase(nav.currentPath.value, name)

    fun exit(activity: Activity) = exit_td(activity)

    fun sortModeCallbackList() = FileListSorter().getSortModeCallbackList { updateSortModeUseCase(it) }

    private fun listToPair(storageList: List<StorageHolderItem>): List<Pair<String, () -> Unit>> {
        val result = mutableListOf<Pair<String, () -> Unit>>()
        storageList.forEach {
            result.add(Pair(it.absolutePath, {
                viewModelScope.launch { navigateToDirectoryUseCase(it.storage) }
            }))
        }
        return result
    }
}
