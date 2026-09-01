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
import com.todokanai.composepracticenew.tools.TransferCoordinator
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.usecase.FileActionUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.ProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject

@HiltViewModel
class BottomButtonsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
    private val fileNavigatorUseCase: FileNavigatorUseCase,
    private val fileActionUseCase: FileActionUseCase,
    private val progressUseCase: ProgressUseCase,
    private val myNoti: MyNotification,
    private val coordinator: TransferCoordinator
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

    /** 선택 목록에서 skipFiles를 제외한 대상에 대해 selectMode에 맞는 파일 작업을 실행한다. @param skipFiles 충돌로 건너뛸 파일 목록 */
    fun confirm(selectedList: List<FileHolderItem>, selectMode: Int, skipFiles: List<FileHolderItem> = emptyList()) {
        val currentPath = fileNavigatorUseCase.currentPath.value ?: return
        val conflictPaths = skipFiles.map { it.path }.toSet()
        val targets = selectedList.filterNot { it.path in conflictPaths }
        if (targets.isEmpty()) return
        when (selectMode) {
            CONFIRM_MODE_COPY -> launchFlows(ACTION_KEY_COPY) { onProgress ->
                listOf(fileActionUseCase.copyAction(targets.map { it.path }, currentPath, onProgress))
            }
            CONFIRM_MODE_MOVE -> launchFlows(ACTION_KEY_MOVE, context.getString(R.string.noti_move_complete)) { onProgress ->
                listOf(fileActionUseCase.moveFile(targets.map { it.path }, currentPath, onProgress))
            }
            CONFIRM_MODE_UNZIP -> launchFlows(ACTION_KEY_UNZIP) { onProgress ->
                listOf(fileActionUseCase.unzipAction(targets.map { it.path }, currentPath, unzipHere = false, onProgress = onProgress))
            }
            CONFIRM_MODE_UNZIP_HERE -> launchFlows(ACTION_KEY_UNZIP) { onProgress ->
                listOf(fileActionUseCase.unzipAction(targets.map { it.path }, currentPath, unzipHere = true, onProgress = onProgress))
            }
        }
    }

    fun zip(selectedList: List<FileHolderItem>, name: String) {
        val parent = selectedList.firstOrNull()?.path?.let { File(it).parent } ?: return
        launchFlows(ACTION_KEY_ZIP) { onProgress ->
            listOf(fileActionUseCase.zipAction(selectedList.map { it.path }, "$parent/$name.zip", onProgress))
        }
    }

    fun rename(item: FileHolderItem, name: String) {
        launchFlows(actionType = null) { _ ->
            listOf(fileActionUseCase.renameFile(item.path, name))
        }
    }

    fun delete(selectedList: List<FileHolderItem>) {
        launchFlows(ACTION_KEY_DELETE, context.getString(R.string.noti_delete_complete)) { onProgress ->
            selectedList.map { fileActionUseCase.deleteFile(it.path, onProgress) }
        }
    }

    /**
     * instanceId와 onProgress 람다를 구성한 뒤 buildFlows에 전달해 Flow를 생성하고, TransferCoordinator에서 수집한다.
     * @param buildFlows onProgress를 받아 IO 작업 Flow 목록을 반환하는 팩토리
     */
    private fun launchFlows(
        actionType: Int?,
        completionMessage: String? = null,
        buildFlows: (onProgress: (ProgressState) -> Unit) -> List<Flow<ProgressState>>
    ) {
        val instanceId = progressUseCase.nextInstanceId()
        // instanceId 캡처 후 람다 구성 — IO 스레드에서 직접 호출되므로 non-suspending
        val onProgress: (ProgressState) -> Unit = { state ->
            if (actionType != null) {
                progressUseCase.setProgressState(instanceId, state.copy(actionKey = actionType))
                sendProgressNoti(actionType, state)
            }
        }
        coordinator.launch(
            scope = appScope,
            sources = buildFlows(onProgress),
            onRemove = { if (actionType != null) progressUseCase.removeProgress(instanceId) },
            onSuccess = {
                val message = completionMessage ?: context.getString(R.string.noti_complete)
                myNoti.completedNotification("", message, actionType)
                progressUseCase.emitCompletion(message)
                fileNavigatorUseCase.refresh()
            },
            onError = { message ->
                message?.let { progressUseCase.emitError(it) }
                fileNavigatorUseCase.refresh()
            }
        )
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
