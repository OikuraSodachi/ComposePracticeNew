package com.todokanai.composepracticenew.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.model.StorageHolderItem
import com.todokanai.composepracticenew.repository.StorageVolumeRepository
import com.todokanai.composepracticenew.tools.independent.exit_td
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StorageViewModel @Inject constructor(
    private val storageRepo: StorageVolumeRepository
) : ViewModel() {

    /** 스토리지 선택 화면에 필요한 UI 상태를 담는 클래스. */
    data class UiState(
        val storageList: List<StorageHolderItem> = emptyList()
    )

    val uiState: StateFlow<UiState> = storageRepo.storageList
        .map { UiState(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState()
        )

    /** FileListFrag의 초기 경로값 */
    fun setInitialPath(setPath: () -> Unit) {
        viewModelScope.launch {
            setPath()
        }
    }

    fun button1() {

    }

    fun exit(activity: Activity) = exit_td(activity)
}
