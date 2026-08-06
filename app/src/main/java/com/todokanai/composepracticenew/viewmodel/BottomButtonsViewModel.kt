package com.todokanai.composepracticenew.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.todokanai.composepracticenew.R
import dagger.hilt.android.qualifiers.ApplicationContext
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_COPY
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_DELETE
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_MOVE
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_ZIP
import com.todokanai.composepracticenew.myobjects.Constants.CONFIRM_MODE_COPY
import com.todokanai.composepracticenew.myobjects.Constants.CONFIRM_MODE_MOVE
import com.todokanai.composepracticenew.tools.MyNotification
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.usecase.FileActionUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.ProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class BottomButtonsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileNavigatorUseCase: FileNavigatorUseCase,
    private val fileActionUseCase: FileActionUseCase,
    private val progressUseCase: ProgressUseCase,
    private val myNoti: MyNotification
) : ViewModel() {

    fun confirm(selectedList: List<FileHolderItem>, selectMode: Int) {
        val currentPath = fileNavigatorUseCase.currentPath.value ?: return
        when (selectMode) {
            CONFIRM_MODE_COPY -> launchAction(ACTION_KEY_COPY, currentPath) {
                fileActionUseCase.copyAction(
                    targetFiles = selectedList.map { it.path },
                    targetPath = currentPath
                )
            }
            CONFIRM_MODE_MOVE -> launchAction(
                actionKey = ACTION_KEY_MOVE,
                refreshPath = currentPath,
                targetList = selectedList,
                completionMessage = context.getString(R.string.noti_move_complete)
            ) { item ->
                fileActionUseCase.moveFile(item.path, currentPath)
            }
        }
    }

    fun zip(selectedList: List<FileHolderItem>, name: String) {
        val parent = selectedList.firstOrNull()?.path?.let { File(it).parent } ?: return
        launchAction(ACTION_KEY_ZIP, parent) {
            fileActionUseCase.zipAction(
                targetFiles = selectedList.map { it.path },
                zipFile = "$parent/$name.zip"
            )
        }
    }

    fun rename(item: FileHolderItem, name: String) {
        launchAction(actionKey = null, refreshPath = File(item.path).parent) {
            fileActionUseCase.renameFile(item.path, name)
        }
    }

    fun delete(selectedList: List<FileHolderItem>) {
        val refreshPath = selectedList.firstOrNull()?.path?.let { File(it).parent } ?: return
        CoroutineScope(Dispatchers.IO).launch {
            var hasError = false
            selectedList.forEach { item ->
                fileActionUseCase.deleteFile(item.path)
                    .collect { state ->
                        progressUseCase.setProgressState(ACTION_KEY_DELETE, state)
                        if (state.error != null) hasError = true
                    }
            }
            progressUseCase.removeProgress(ACTION_KEY_DELETE)
            if (!hasError) {
                fileNavigatorUseCase.setPath(refreshPath)
                fileNavigatorUseCase.refresh()
                myNoti.completedNotification("", context.getString(R.string.noti_delete_complete), ACTION_KEY_DELETE)
            }
        }
    }

    private fun launchAction(actionKey: Int?, refreshPath: String?, flowProvider: () -> Flow<ProgressState>) {
        CoroutineScope(Dispatchers.IO).launch {
            var hasError = false
            flowProvider().collect { state ->
                actionKey?.let { progressUseCase.setProgressState(it, state) }
                if (state.error != null) hasError = true
            }
            actionKey?.let { progressUseCase.removeProgress(it) }
            if (!hasError) {
                refreshPath?.let { fileNavigatorUseCase.setPath(it) }
                fileNavigatorUseCase.refresh()
                myNoti.completedNotification("", context.getString(R.string.noti_complete), actionKey)
            }
        }
    }

    private fun launchAction(
        actionKey: Int?,
        refreshPath: String?,
        targetList: List<FileHolderItem>,
        completionMessage: String? = null,
        flowProvider: (FileHolderItem) -> Flow<ProgressState>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            var hasError = false
            targetList.forEach { item ->
                flowProvider(item).collect { state ->
                    actionKey?.let { progressUseCase.setProgressState(it, state) }
                    if (state.error != null) hasError = true
                }
            }
            actionKey?.let { progressUseCase.removeProgress(it) }
            if (!hasError) {
                refreshPath?.let { fileNavigatorUseCase.setPath(it) }
                fileNavigatorUseCase.refresh()
                myNoti.completedNotification(
                    "",
                    completionMessage ?: context.getString(R.string.noti_complete),
                    actionKey
                )
            }
        }
    }
}
