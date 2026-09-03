package com.todokanai.composepracticenew.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.todokanai.composepracticenew.R
import dagger.hilt.android.qualifiers.ApplicationContext
import com.todokanai.composepracticenew.di.ApplicationScope
import com.todokanai.composepracticenew.myobjects.AppConstants
import com.todokanai.composepracticenew.operation.CopyOperation
import com.todokanai.composepracticenew.operation.DeleteOperation
import com.todokanai.composepracticenew.operation.MoveOperation
import com.todokanai.composepracticenew.operation.UnzipOperation
import com.todokanai.composepracticenew.operation.ZipOperation
import com.todokanai.composepracticenew.tools.MyNotification
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.usecase.FileActionUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.ProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
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
            AppConstants.CONFIRM_MODE_COPY, AppConstants.CONFIRM_MODE_MOVE -> selectedList.filter { item ->
                File(currentPath, File(item.path).name).exists()
            }
            AppConstants.CONFIRM_MODE_UNZIP, AppConstants.CONFIRM_MODE_UNZIP_HERE -> selectedList.filter { item ->
                File(currentPath, File(item.path).nameWithoutExtension).exists()
            }
            else -> emptyList()
        }
    }

    /** 선택 목록에서 skipFiles를 제외한 대상에 대해 selectMode에 맞는 파일 작업을 실행한다. @param skipFiles 충돌로 건너뛸 파일 목록 */
    fun confirm(selectedList: List<FileHolderItem>, selectMode: Int, skipFiles: List<FileHolderItem> = emptyList()) {
        val currentPath = fileNavigatorUseCase.currentPath.value ?: return
        val conflictPaths = skipFiles.map { it.path }.toSet()
        val targets = selectedList.filterNot { it.path in conflictPaths }
        if (targets.isEmpty()) return
        when (selectMode) {
            AppConstants.CONFIRM_MODE_COPY -> {
                val instanceId = progressUseCase.nextInstanceId()
                CopyOperation(
                    targetFiles = targets.map { it.path },
                    targetPath = currentPath,
                    fileActionUseCase = fileActionUseCase,
                    myNoti = myNoti,
                    completionMessage = context.getString(R.string.noti_complete),
                    onRefresh = { fileNavigatorUseCase.refresh() },
                    onEmitCompletion = { msg -> progressUseCase.emitCompletion(msg) },
                    onEmitError = { msg -> progressUseCase.emitError(msg) },
                    setProgress = { state -> progressUseCase.setProgressState(instanceId, state) },
                    clearProgress = { progressUseCase.removeProgress(instanceId) }
                ).execute(appScope)
            }
            AppConstants.CONFIRM_MODE_MOVE -> {
                val instanceId = progressUseCase.nextInstanceId()
                MoveOperation(
                    targetFiles = targets.map { it.path },
                    targetPath = currentPath,
                    fileActionUseCase = fileActionUseCase,
                    myNoti = myNoti,
                    completionMessage = context.getString(R.string.noti_move_complete),
                    onRefresh = { fileNavigatorUseCase.refresh() },
                    onEmitCompletion = { msg -> progressUseCase.emitCompletion(msg) },
                    onEmitError = { msg -> progressUseCase.emitError(msg) },
                    setProgress = { state -> progressUseCase.setProgressState(instanceId, state) },
                    clearProgress = { progressUseCase.removeProgress(instanceId) }
                ).execute(appScope)
            }
            AppConstants.CONFIRM_MODE_UNZIP -> {
                val instanceId = progressUseCase.nextInstanceId()
                UnzipOperation(
                    zipFiles = targets.map { it.path },
                    destPath = currentPath,
                    unzipHere = false,
                    fileActionUseCase = fileActionUseCase,
                    myNoti = myNoti,
                    completionMessage = context.getString(R.string.noti_complete),
                    onRefresh = { fileNavigatorUseCase.refresh() },
                    onEmitCompletion = { msg -> progressUseCase.emitCompletion(msg) },
                    onEmitError = { msg -> progressUseCase.emitError(msg) },
                    setProgress = { state -> progressUseCase.setProgressState(instanceId, state) },
                    clearProgress = { progressUseCase.removeProgress(instanceId) }
                ).execute(appScope)
            }
            AppConstants.CONFIRM_MODE_UNZIP_HERE -> {
                val instanceId = progressUseCase.nextInstanceId()
                UnzipOperation(
                    zipFiles = targets.map { it.path },
                    destPath = currentPath,
                    unzipHere = true,
                    fileActionUseCase = fileActionUseCase,
                    myNoti = myNoti,
                    completionMessage = context.getString(R.string.noti_complete),
                    onRefresh = { fileNavigatorUseCase.refresh() },
                    onEmitCompletion = { msg -> progressUseCase.emitCompletion(msg) },
                    onEmitError = { msg -> progressUseCase.emitError(msg) },
                    setProgress = { state -> progressUseCase.setProgressState(instanceId, state) },
                    clearProgress = { progressUseCase.removeProgress(instanceId) }
                ).execute(appScope)
            }
        }
    }

    fun zip(selectedList: List<FileHolderItem>, name: String) {
        val parent = selectedList.firstOrNull()?.path?.let { File(it).parent } ?: return
        val instanceId = progressUseCase.nextInstanceId()
        ZipOperation(
            sourceFiles = selectedList.map { it.path },
            zipFilePath = "$parent/$name.zip",
            fileActionUseCase = fileActionUseCase,
            myNoti = myNoti,
            completionMessage = context.getString(R.string.noti_complete),
            onRefresh = { fileNavigatorUseCase.refresh() },
            onEmitCompletion = { msg -> progressUseCase.emitCompletion(msg) },
            onEmitError = { msg -> progressUseCase.emitError(msg) },
            setProgress = { state -> progressUseCase.setProgressState(instanceId, state) },
            clearProgress = { progressUseCase.removeProgress(instanceId) }
        ).execute(appScope)
    }

    fun rename(item: FileHolderItem, name: String) {
        appScope.launch {
            try {
                fileActionUseCase.renameFile(item.path, name).collect {}
                val message = context.getString(R.string.noti_complete)
                myNoti.completedNotification("", message)
                progressUseCase.emitCompletion(message)
            } catch (e: Exception) {
                progressUseCase.emitError(e.message ?: "이름 변경 중 오류")
            } finally {
                fileNavigatorUseCase.refresh()
            }
        }
    }

    fun delete(selectedList: List<FileHolderItem>) {
        val instanceId = progressUseCase.nextInstanceId()
        DeleteOperation(
            targetFiles = selectedList.map { it.path },
            fileActionUseCase = fileActionUseCase,
            myNoti = myNoti,
            completionMessage = context.getString(R.string.noti_delete_complete),
            onRefresh = { fileNavigatorUseCase.refresh() },
            onEmitCompletion = { msg -> progressUseCase.emitCompletion(msg) },
            onEmitError = { msg -> progressUseCase.emitError(msg) },
            setProgress = { state -> progressUseCase.setProgressState(instanceId, state) },
            clearProgress = { progressUseCase.removeProgress(instanceId) }
        ).execute(appScope)
    }
}
