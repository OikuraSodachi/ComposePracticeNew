package com.todokanai.composepracticenew.tools

import android.content.Context
import com.todokanai.composepracticenew.data.dataclass.ProgressState
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_COPY
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_DELETE
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_MOVE
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_UNZIP
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_ZIP
import com.todokanai.composepracticenew.tools.fileaction.CopyAction
import com.todokanai.composepracticenew.tools.fileaction.DeleteAction
import com.todokanai.composepracticenew.tools.fileaction.MoveAction
import com.todokanai.composepracticenew.tools.fileaction.NewFolderAction
import com.todokanai.composepracticenew.tools.fileaction.OpenAction
import com.todokanai.composepracticenew.tools.fileaction.RenameAction
import com.todokanai.composepracticenew.tools.fileaction.UnzipAction
import com.todokanai.composepracticenew.tools.fileaction.ZipAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.zip.ZipFile

class FileAction(
    val setCurrentPath: (File) -> Unit,
    private val currentPathFlow: StateFlow<File>,
    private val myNoti: MyNotification,
    private val logTool: LogTool
) {
    private val notiSorter = CompletedNotiSorter(myNoti)

    suspend fun openAction(context: Context, selected: File) {
        if (selected.isDirectory) {
            setCurrentPath(selected)
        } else {
            OpenAction().openFile(context, selected)
        }
    }

    fun renameAction(selectedFile: File, name: String) {
        actionWrapper(
            action = { RenameAction().renameAction(selectedFile = selectedFile, name = name) },
            completionCallback = { onComplete(selectedFile.parentFile, null) }
        )
    }

    fun copyAction(files: Array<File>, currentPath: File, setProgressState: (ProgressState) -> Unit) {
        actionWrapper(
            action = {
                CopyAction(onSpaceRequired = { onSpaceRequired() }).copyFiles(
                    files = files,
                    target = currentPath,
                    progressCallback = { setProgressState(it) }
                )
            },
            completionCallback = { onComplete(currentPath, ACTION_KEY_COPY) }
        )
    }

    fun moveAction(files: Array<File>, currentPath: File, setProgressState: (ProgressState) -> Unit) {
        actionWrapper(
            action = {
                MoveAction(onSpaceRequired = { onSpaceRequired() }).moveAction(
                    files = files,
                    path = currentPath,
                    progressCallback = { setProgressState(it) }
                )
            },
            completionCallback = { onComplete(currentPath, ACTION_KEY_MOVE) }
        )
    }

    fun deleteAction(files: Array<File>, setProgressState: (ProgressState) -> Unit) {
        actionWrapper(
            action = { DeleteAction().deleteFiles(files = files, progressCallback = { setProgressState(it) }) },
            completionCallback = { onComplete(files.first().parentFile, ACTION_KEY_DELETE) }
        )
    }

    fun newFolderAction(currentPath: File, folderName: String) {
        actionWrapper(
            action = { NewFolderAction().newFolderAction(path = currentPath, folderName = folderName) },
            completionCallback = { onComplete(currentPath, null) }
        )
    }

    fun zipAction(files: Array<File>, zipFileName: String, setProgressState: (ProgressState) -> Unit) {
        val targetPath = currentPathFlow.value.parentFile
        val zipFile = File("$targetPath/$zipFileName.zip")
        actionWrapper(
            action = {
                ZipAction(onSpaceRequired = { onSpaceRequired() }).zipFiles(
                    files = files,
                    zipFile = zipFile,
                    progressCallback = { setProgressState(it) }
                )
            },
            completionCallback = { onComplete(targetPath, ACTION_KEY_ZIP) }
        )
    }

    fun unzipAction(zipFile: ZipFile, currentPath: File, unzipHere: Boolean, setProgressState: (ProgressState) -> Unit) {
        actionWrapper(
            action = {
                UnzipAction(onSpaceRequired = { onSpaceRequired() }).unzip(
                    zipFile = zipFile,
                    target = currentPath,
                    progressCallback = { setProgressState(it) },
                    unzipHere = unzipHere
                )
            },
            completionCallback = { onComplete(currentPath.parentFile, ACTION_KEY_UNZIP) }
        )
    }

    private fun actionWrapper(action: () -> Unit, completionCallback: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            action()
        }.invokeOnCompletion {
            completionCallback()
        }
    }

    private fun onSpaceRequired() = logTool.makeShortToast("Not enough space")

    private fun onComplete(path: File?, actionKey: Int?) {
        CoroutineScope(Dispatchers.IO).launch {
            if (currentPathFlow.value == path) {
                path.listFiles()?.let {
                    setCurrentPath(path)
                }
            }
            actionKey?.let {
                notiSorter.callback(actionKey)
            }
        }
    }
}
