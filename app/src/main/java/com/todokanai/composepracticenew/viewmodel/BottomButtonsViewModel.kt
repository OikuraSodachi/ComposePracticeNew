package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_COPY
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_DELETE
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_MOVE
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_ZIP
import com.todokanai.composepracticenew.myobjects.Constants.CONFIRM_MODE_COPY
import com.todokanai.composepracticenew.myobjects.Constants.CONFIRM_MODE_MOVE
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.ProgressTracker
import com.todokanai.composepracticenew.tools.MyNotification
import com.todokanai.composepracticenew.usecase.FileActionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class BottomButtonsViewModel @Inject constructor(
    private val nav: FileNavigatorRepository,
    private val fileActionUseCase: FileActionUseCase,
    private val prog: ProgressTracker,
    private val myNoti: MyNotification
) : ViewModel() {

    /** 하단 버튼 영역 화면에 필요한 UI 상태를 담는 클래스. */
    data class UiState(
        val currentPath: File = File("/")
    )

    val uiState: StateFlow<UiState> = nav.currentPath
        .map { UiState(it?.let { File(it) } ?: File("/")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState()
        )

    fun confirm(selectedList: List<File>, selectMode: Int, currentPath: File) {
        when (selectMode) {
            CONFIRM_MODE_COPY -> launchAction(ACTION_KEY_COPY, currentPath) {
                fileActionUseCase.copyAction(
                    targetFiles = selectedList.map { it.absolutePath },
                    targetPath = currentPath.absolutePath
                )
            }
            CONFIRM_MODE_MOVE -> viewModelScope.launch {
                var hasError = false
                selectedList.forEach { file ->
                    fileActionUseCase.moveFile(file.absolutePath, currentPath.absolutePath)
                        .collect { state ->
                            prog.setProgressState(ACTION_KEY_MOVE, state)
                            if (state.error != null) hasError = true
                        }
                }
                prog.removeProgress(ACTION_KEY_MOVE)
                if (!hasError) {
                    nav.setCurrentPath(currentPath)
                    nav.refresh()
                    myNoti.completedNotification("", "이동 완료", ACTION_KEY_MOVE)
                }
            }
        }
    }

    fun zip(selectedList: List<File>, name: String) {
        val parent = selectedList.firstOrNull()?.parent ?: return
        launchAction(ACTION_KEY_ZIP, selectedList.first().parentFile) {
            fileActionUseCase.zipAction(
                targetFiles = selectedList.map { it.absolutePath },
                zipFile = "$parent/$name.zip"
            )
        }
    }

    fun rename(file: File, name: String) {
        launchAction(actionKey = null, refreshPath = file.parentFile) {
            fileActionUseCase.renameFile(file.absolutePath, name)
        }
    }

    fun delete(selectedList: List<File>) {
        val refreshPath = selectedList.firstOrNull()?.parentFile ?: return
        viewModelScope.launch {
            var hasError = false
            selectedList.forEach { file ->
                fileActionUseCase.deleteFile(file.absolutePath)
                    .collect { state ->
                        prog.setProgressState(ACTION_KEY_DELETE, state)
                        if (state.error != null) hasError = true
                    }
            }
            prog.removeProgress(ACTION_KEY_DELETE)
            if (!hasError) {
                nav.setCurrentPath(refreshPath)
                nav.refresh()
                myNoti.completedNotification("", "삭제 완료", ACTION_KEY_DELETE)
            }
        }
    }

    private fun launchAction(actionKey: Int?, refreshPath: File?, flowProvider: () -> Flow<ProgressState>) {
        viewModelScope.launch {
            var hasError = false
            flowProvider().collect { state ->
                actionKey?.let { prog.setProgressState(it, state) }
                if (state.error != null) hasError = true
            }
            actionKey?.let { prog.removeProgress(it) }
            if (!hasError) {
                refreshPath?.let { nav.setCurrentPath(it) }
                nav.refresh()
                myNoti.completedNotification("", "완료", actionKey)
            }
        }
    }
}
