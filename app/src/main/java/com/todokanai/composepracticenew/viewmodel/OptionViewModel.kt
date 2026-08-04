package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.model.StorageHolderItem
import com.todokanai.composepracticenew.myobjects.Constants
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.SortModeUseCase
import com.todokanai.composepracticenew.usecase.StorageVolumeUseCase
import com.todokanai.composepracticenew.variables.FileListSorter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OptionViewModel @Inject constructor(
    private val fileNavigatorUseCase: FileNavigatorUseCase,
    private val storageVolumeUseCase: StorageVolumeUseCase,
    private val sortModeUseCase: SortModeUseCase
) : ViewModel() {

    /** 옵션 바 화면에 필요한 UI 상태를 담는 클래스. */
    data class UiState(
        val storageList: List<StorageHolderItem> = emptyList(),
        val sortMode: String = Constants.BY_DEFAULT
    )

    val uiState: StateFlow<UiState> = combine(
        storageVolumeUseCase.storageList,
        sortModeUseCase.sortBy
    ) { storageList, sortMode ->
        UiState(storageList, sortMode)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState()
    )

    fun toPair(storageList: List<StorageHolderItem>) = listToPair(storageList)

    fun newFolder(name: String) {} // stub — not yet implemented

    fun sortModeCallbackList() = FileListSorter().getSortModeCallbackList { sortModeUseCase.saveSortBy(it) }

    private fun listToPair(storageList: List<StorageHolderItem>): List<Pair<String, () -> Unit>> {
        val result = mutableListOf<Pair<String, () -> Unit>>()
        storageList.forEach {
            result.add(Pair(it.absolutePath, {
                viewModelScope.launch { fileNavigatorUseCase.setLocalPath(it.storage.absolutePath) }
            }))
        }
        return result
    }
}
