package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.variables.Variables
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DirectoryViewModel @Inject constructor(private val vars: Variables) : ViewModel() {

    val dirTree = vars.dirTree

    fun updateCurrentPath(file: File) {
        viewModelScope.launch {
            vars.setCurrentPath(file)
        }
    }
}
