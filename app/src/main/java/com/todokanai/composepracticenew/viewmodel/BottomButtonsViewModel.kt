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
            CONFIRM_MODE_COPY -> launchFlows(
                actionKey = ACTION_KEY_COPY,
                refreshPath = currentPath,
                flows = listOf(
                    fileActionUseCase.copyAction(
                        targetFiles = selectedList.map { it.path },
                        targetPath = currentPath
                    )
                )
            )
            CONFIRM_MODE_MOVE -> launchFlows(
                actionKey = ACTION_KEY_MOVE,
                refreshPath = currentPath,
                completionMessage = context.getString(R.string.noti_move_complete),
                flows = selectedList.map { fileActionUseCase.moveFile(it.path, currentPath) }
            )
        }
    }

    fun zip(selectedList: List<FileHolderItem>, name: String) {
        val parent = selectedList.firstOrNull()?.path?.let { File(it).parent } ?: return
        launchFlows(
            actionKey = ACTION_KEY_ZIP,
            refreshPath = parent,
            flows = listOf(
                fileActionUseCase.zipAction(
                    targetFiles = selectedList.map { it.path },
                    zipFile = "$parent/$name.zip"
                )
            )
        )
    }

    fun rename(item: FileHolderItem, name: String) {
        launchFlows(
            actionKey = null,
            refreshPath = File(item.path).parent,
            flows = listOf(fileActionUseCase.renameFile(item.path, name))
        )
    }

    fun delete(selectedList: List<FileHolderItem>) {
        val refreshPath = selectedList.firstOrNull()?.path?.let { File(it).parent } ?: return
        launchFlows(
            actionKey = ACTION_KEY_DELETE,
            refreshPath = refreshPath,
            completionMessage = context.getString(R.string.noti_delete_complete),
            flows = selectedList.map { fileActionUseCase.deleteFile(it.path) }
        )
    }

    private fun launchFlows(
        actionKey: Int?,
        refreshPath: String?,
        completionMessage: String? = null,
        flows: List<Flow<ProgressState>>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            var hasError = false
            try {
                flows.forEach { flow ->
                    if (hasError) return@forEach
                    flow.collect { state ->
                        actionKey?.let { progressUseCase.setProgressState(it, state) }
                        if (state.error != null) hasError = true
                    }
                }
            } finally {
                actionKey?.let { progressUseCase.removeProgress(it) }
            }
            if (!hasError) {
                refreshPath?.let { fileNavigatorUseCase.setPath(it) }
                myNoti.completedNotification(
                    "",
                    completionMessage ?: context.getString(R.string.noti_complete),
                    actionKey
                )
            }
            fileNavigatorUseCase.refresh()
        }
    }
}
