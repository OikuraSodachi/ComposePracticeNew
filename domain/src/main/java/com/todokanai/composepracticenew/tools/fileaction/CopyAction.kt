package com.todokanai.composepracticenew.tools.fileaction

import com.todokanai.composepracticenew.data.dataclass.ProgressState
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_COPY
import com.todokanai.composepracticenew.myobjects.Constants.ACTION_KEY_MOVE
import com.todokanai.composepracticenew.tools.independent.getTotalSize_td
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** ViewModel 이상 단계에서 보이면 안됨 */
class CopyAction(val fromMoveAction:Boolean = false,val onSpaceRequired: () -> Unit?) {

    private val actionKey =
        if(fromMoveAction){
            ACTION_KEY_MOVE
        }else {
            ACTION_KEY_COPY
        }

    fun copyFiles(
        files:Array<File>,
        target: File,
        progressCallback:(progress:ProgressState)->Unit,
    ){
        val totalSize = getTotalSize_td(files)

        if (totalSize >= target.freeSpace) {
            onSpaceRequired()
        } else {
            val byteProgress = MutableStateFlow<Long>(0L)
            val prevProgress = MutableStateFlow<Int>(0)

            files.forEach { file ->

                val tempTarget = File("${target.toPath()}/${file.name}")
                copyFileRecursively(
                    file = file,
                    target = tempTarget,
                    bytesProgress = byteProgress,
                    prevProgress = prevProgress,
                    totalSize = totalSize,
                    callback = { progressCallback(it) },
                )
            }
        }
    }       // Done


    private fun copyFileRecursively(
        file: File,
        target: File,
        bytesProgress : MutableStateFlow<Long>,
        prevProgress: MutableStateFlow<Int>,
        totalSize: Long,
        //callback: (percent: Int) -> Unit,
        callback:(progressState:ProgressState)->Unit
    ) {

        if (file.isDirectory) {
            if (!target.exists()) {
                target.mkdir()
            }
            file.listFiles()?.forEach {
                val newFile = target.toPath().resolve(it.name)
                copyFileRecursively(
                    file = it,
                    target = File("$newFile"),
                    bytesProgress = bytesProgress,
                    prevProgress = prevProgress,
                    totalSize = totalSize,
                    callback = callback
                )
            }
        } else {
            val inputStream = FileInputStream(file)
            val outputStream = FileOutputStream(target)
            val buffer = ByteArray(1024)
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } > 0) {
                outputStream.write(buffer, 0, bytesRead)
                bytesProgress.value += bytesRead
                /** */
                val progress = (100 * bytesProgress.value / totalSize).toInt()
                if (prevProgress.value != progress || bytesProgress.value == totalSize) {      // 진행도 변경되거나 전체 파일의 끝일때

                    val state = ProgressState(
                        progress = progress,
                        actionKey = actionKey
                    )
                //    callback(progress)

                    callback(state)
                    prevProgress.value = progress
                }
            }
            inputStream.close()
            outputStream.close()
        }
    }
}