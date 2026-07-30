package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.repository.FileNavigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DirectoryViewModel @Inject constructor(private val nav: FileNavigator) : ViewModel() {

    val dirTree = nav.dirTree

    fun updateCurrentPath(file: File) {
        viewModelScope.launch {
            nav.setCurrentPath(file)
        }
    }
}
