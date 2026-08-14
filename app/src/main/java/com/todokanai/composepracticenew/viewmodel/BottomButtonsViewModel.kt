package com.todokanai.composepracticenew.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.todokanai.composepracticenew.R
import dagger.hilt.android.qualifiers.ApplicationContext
import com.todokanai.composepracticenew.di.ApplicationScope
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_COPY
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_DELETE
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_MOVE
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_UNZIP
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_ZIP
import com.todokanai.composepracticenew.myobjects.Constants.CONFIRM_MODE_COPY
import com.todokanai.composepracticenew.myobjects.Constants.CONFIRM_MODE_MOVE
import com.todokanai.composepracticenew.myobjects.Constants.CONFIRM_MODE_UNZIP
import com.todokanai.composepracticenew.myobjects.Constants.CONFIRM_MODE_UNZIP_HERE
import com.todokanai.composepracticenew.tools.MyNotification
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.usecase.FileActionUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.ProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class BottomButtonsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
    private val fileNavigatorUseCase: FileNavigatorUseCase,
    private val fileActionUseCase: FileActionUseCase,
    private val progressUseCase: ProgressUseCase,
    private val myNoti: MyNotification
) : ViewModel() {

    /** selectMode 작업의 목적지 경로에 이미 같은 이름으로 존재하는 파일 목록을 반환한다. */
    fun getConflicts(selectedList: List<FileHolderItem>, selectMode: Int): List<FileHolderItem> {
        val currentPath = fileNavigatorUseCase.currentPath.value ?: return emptyList()
        return when (selectMode) {
            CONFIRM_MODE_COPY, CONFIRM_MODE_MOVE -> selectedList.filter { item ->
                File(currentPath, File(item.path).name).exists()
            }
            CONFIRM_MODE_UNZIP, CONFIRM_MODE_UNZIP_HERE -> selectedList.filter { item ->
                File(currentPath, File(item.path).nameWithoutExtension).exists()
            }
            else -> emptyList()
        }
    }

    fun confirm(selectedList: List<FileHolderItem>, selectMode: Int) {
        val currentPath = fileNavigatorUseCase.currentPath.value ?: return
        when (selectMode) {
            CONFIRM_MODE_COPY -> launchFlows(
                actionType = ACTION_KEY_COPY,
                flows = listOf(
                    fileActionUseCase.copyAction(
                        targetFiles = selectedList.map { it.path },
                        targetPath = currentPath
                    )
                )
            )
            CONFIRM_MODE_MOVE -> launchFlows(
                actionType = ACTION_KEY_MOVE,
                completionMessage = context.getString(R.string.noti_move_complete),
                flows = selectedList.map { fileActionUseCase.moveFile(it.path, currentPath) }
            )
            CONFIRM_MODE_UNZIP -> launchFlows(
                actionType = ACTION_KEY_UNZIP,
                flows = selectedList.map { fileActionUseCase.unzipAction(it.path, currentPath, unzipHere = false) }
            )
            CONFIRM_MODE_UNZIP_HERE -> launchFlows(
                actionType = ACTION_KEY_UNZIP,
                flows = selectedList.map { fileActionUseCase.unzipAction(it.path, currentPath, unzipHere = true) }
            )
        }
    }

    fun zip(selectedList: List<FileHolderItem>, name: String) {
        val parent = selectedList.firstOrNull()?.path?.let { File(it).parent } ?: return
        launchFlows(
            actionType = ACTION_KEY_ZIP,
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
            actionType = null,
            flows = listOf(fileActionUseCase.renameFile(item.path, name))
        )
    }

    fun delete(selectedList: List<FileHolderItem>) {
        launchFlows(
            actionType = ACTION_KEY_DELETE,
            completionMessage = context.getString(R.string.noti_delete_complete),
            flows = selectedList.map { fileActionUseCase.deleteFile(it.path) }
        )
    }


    private fun launchFlows(
        actionType: Int?,
        completionMessage: String? = null,
        flows: List<Flow<ProgressState>>
    ) {
        val instanceId = progressUseCase.nextInstanceId()
        appScope.launch {
            var hasError = false
            try {
                flows.forEach { flow ->
                    if (hasError) return@forEach
                    flow.collect { state ->
                        if (actionType != null) {
                            progressUseCase.setProgressState(instanceId, state.copy(actionKey = actionType))
                            sendProgressNoti(actionType, state)
                        }
                        if (state.error != null) hasError = true
                    }
                }
            } finally {
                if (actionType != null) progressUseCase.removeProgress(instanceId)
            }
            if (!hasError) {
                myNoti.completedNotification(
                    "",
                    completionMessage ?: context.getString(R.string.noti_complete),
                    actionType
                )
            }
            fileNavigatorUseCase.refresh()
        }
    }

    private fun sendProgressNoti(actionType: Int, state: ProgressState) {
        when (actionType) {
            ACTION_KEY_COPY -> myNoti.copyProgressNoti(state.progress ?: return)
            ACTION_KEY_DELETE -> myNoti.deleteProgressNoti(
                state.currentIndex ?: return,
                state.listSize ?: return
            )
            ACTION_KEY_MOVE -> myNoti.moveProgressNoti(state.progress ?: return)
            ACTION_KEY_ZIP -> myNoti.zipProgressNoti(state.progress ?: return)
            ACTION_KEY_UNZIP -> myNoti.unzipProgressNoti(state.progress ?: return)
        }
    }
}
