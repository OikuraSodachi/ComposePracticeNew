package com.todokanai.composepracticenew.tools

import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_COPY
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_DELETE
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_MOVE
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_UNZIP
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_ZIP
import com.todokanai.composepracticenew.repository.FileActionRepository
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.ProgressTracker
import com.todokanai.composepracticenew.tools.fileaction.CopyAction
import com.todokanai.composepracticenew.tools.fileaction.DeleteAction
import com.todokanai.composepracticenew.tools.fileaction.MoveAction
import com.todokanai.composepracticenew.tools.fileaction.NewFolderAction
import com.todokanai.composepracticenew.tools.fileaction.RenameAction
import com.todokanai.composepracticenew.tools.fileaction.UnzipAction
import com.todokanai.composepracticenew.tools.fileaction.ZipAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/** Orchestrates all file I/O operations, delegating to per-action classes and refreshing the navigator on completion. */
@Singleton
class FileAction @Inject constructor(
    private val nav: FileNavigatorRepository,
    private val prog: ProgressTracker,
    private val myNoti: MyNotification,
    private val logTool: LogTool
) : FileActionRepository {
    private val notiSorter = CompletedNotiSorter(myNoti)

    override fun renameAction(selectedFile: File, name: String) {
        actionWrapper(
            action = { RenameAction().renameAction(selectedFile = selectedFile, name = name) },
            completionCallback = { onComplete(selectedFile.parentFile, null) }
        )
    }

    override fun copyAction(files: Array<File>, currentPath: File) {
        actionWrapper(
            action = {
                CopyAction(onSpaceRequired = { onSpaceRequired() }).copyFiles(
                    files = files,
                    target = currentPath,
                    progressCallback = { prog.setProgressState(it) }
                )
            },
            completionCallback = { onComplete(currentPath, ACTION_KEY_COPY) }
        )
    }

    override fun moveAction(files: Array<File>, currentPath: File) {
        actionWrapper(
            action = {
                MoveAction(onSpaceRequired = { onSpaceRequired() }).moveAction(
                    files = files,
                    path = currentPath,
                    progressCallback = { prog.setProgressState(it) }
                )
            },
            completionCallback = { onComplete(currentPath, ACTION_KEY_MOVE) }
        )
    }

    override fun deleteAction(files: Array<File>) {
        actionWrapper(
            action = { DeleteAction().deleteFiles(files = files, progressCallback = { prog.setProgressState(it) }) },
            completionCallback = { onComplete(files.first().parentFile, ACTION_KEY_DELETE) }
        )
    }

    override fun newFolderAction(currentPath: File, folderName: String) {
        actionWrapper(
            action = { NewFolderAction().newFolderAction(path = currentPath, folderName = folderName) },
            completionCallback = { onComplete(currentPath, null) }
        )
    }

    override fun zipAction(files: Array<File>, zipFileName: String) {
        val targetPath = nav.currentPath.value
        val zipFile = File("$targetPath/$zipFileName.zip")
        actionWrapper(
            action = {
                ZipAction(onSpaceRequired = { onSpaceRequired() }).zipFiles(
                    files = files,
                    zipFile = zipFile,
                    progressCallback = { prog.setProgressState(it) }
                )
            },
            completionCallback = { onComplete(targetPath, ACTION_KEY_ZIP) }
        )
    }

    override fun unzipAction(zipFile: ZipFile, currentPath: File, unzipHere: Boolean) {
        actionWrapper(
            action = {
                UnzipAction(onSpaceRequired = { onSpaceRequired() }).unzip(
                    zipFile = zipFile,
                    target = currentPath,
                    progressCallback = { prog.setProgressState(it) },
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
            if (nav.currentPath.value == path) {
                path.listFiles()?.let {
                    nav.setCurrentPath(path)
                }
            }
            actionKey?.let {
                notiSorter.callback(actionKey)
            }
        }
    }
}
