package com.todokanai.composepracticenew.tools.fileaction

import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.tools.independent.getPhysicalStorage_td
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

class MoveAction(val onSpaceRequired: () -> Unit) {

    fun moveAction(
        files: Array<File>,
        path: File,
        progressCallback: (progress: ProgressState) -> Unit,
    ) {
        if (getPhysicalStorage_td(files.first()) == getPhysicalStorage_td(path)) {
            CoroutineScope(Dispatchers.IO).launch {
                files.forEach { file ->
                    Files.move(
                        Paths.get(file.absolutePath),
                        Paths.get(path.toPath().absolutePathString() + "/${file.name}")
                    )
                }
            }.invokeOnCompletion {
                CoroutineScope(Dispatchers.IO).launch {
                    files.forEach { file ->
                        file.deleteRecursively()
                    }
                }
            }
        } else {
            CopyAction(
                fromMoveAction = true,
                onSpaceRequired = onSpaceRequired
            ).copyFiles(
                files = files,
                target = path,
                progressCallback = { progressCallback(it) },
            )
        }
    }
}
