package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.myobjects.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Holds the current multi-selection list and UI mode (DEFAULT, MULTI_SELECT, CONFIRM_*). */
@Singleton
class SelectionState @Inject constructor() {
    private val _selectedList = MutableStateFlow<List<File>>(emptyList())
    val selectedList: StateFlow<List<File>> get() = _selectedList

    private val _selectMode = MutableStateFlow<Int>(Constants.DEFAULT_MODE)
    val selectMode: StateFlow<Int> get() = _selectMode

    fun addToSelectedList(file: File) {
        _selectedList.value = _selectedList.value.plus(file)
    }

    fun removeFromSelectedList(file: File) {
        _selectedList.value = _selectedList.value.minus(file)
    }

    fun clearSelectedList() {
        _selectedList.value = emptyList()
    }

    fun setSelectMode(mode: Int) {
        _selectMode.value = mode
    }
}
