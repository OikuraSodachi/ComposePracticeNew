package com.todokanai.composepracticenew.tools.fileaction

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_DELETE
import com.todokanai.composepracticenew.tools.independent.getFileAndFoldersNumber_td
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** ViewModel 이상 단계에서 보이면 안됨 */
class DeleteAction {
    private val actionKey = ACTION_KEY_DELETE

    fun deleteFiles(
        files: Array<File>,
        progressCallback: (progress: ProgressState) -> Unit?
    ) {
        val listSize = getFileAndFoldersNumber_td(files)
        val deleteProgress = MutableStateFlow(
            ProgressState(0, listSize = listSize, currentIndex = 0, actionKey = actionKey)
        )

        files.forEach { file ->
            deleteRecursivePart(
                file = file,
                deleteProgress = deleteProgress,
                callback = { progressCallback(deleteProgress.value) },
                listSize = listSize
            )
        }
    }

    private fun deleteRecursivePart(
        file: File,
        deleteProgress: MutableStateFlow<ProgressState>,
        callback: () -> Unit,
        listSize: Int
    ) {
        if (file.isDirectory) {
            file.listFiles()?.forEach {
                deleteRecursivePart(it, deleteProgress, callback, listSize)
            }
        }
        file.delete()

        val currentIndex = deleteProgress.value.currentIndex!! + 1
        val progressFloat = (100 * currentIndex.toDouble() / listSize.toDouble()).toFloat()

        deleteProgress.value = ProgressState(
            progressFloat = progressFloat,
            currentIndex = currentIndex,
            actionKey = actionKey
        )
        callback()
    }
}
