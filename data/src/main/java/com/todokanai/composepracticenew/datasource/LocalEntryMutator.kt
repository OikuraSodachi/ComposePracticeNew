package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** 단일 로컬 항목의 이름변경·삭제·폴더 생성을 수행한다. */
class LocalEntryMutator @Inject constructor() {

    /**
     * [targetFile]의 이름을 [newName]으로 변경한다.
     * @param targetFile 이름을 변경할 파일의 절대 경로
     * @param newName 변경할 새 이름
     */
    suspend fun rename(targetFile: String, newName: String) = withContext(Dispatchers.IO) {
        val file = File(targetFile)
        val success = file.renameTo(File("${file.parent}/$newName"))
        if (!success) throw Exception("이름 변경 실패: ${file.name} → $newName")
    }

    /**
     * [targetFile]과 하위 항목을 리프부터 삭제하며 항목 수 기준으로 진행률을 보고한다.
     * @param targetFile 삭제할 파일 또는 디렉터리의 절대 경로
     * @param onProgress 진행 상태를 수신하는 콜백
     */
    suspend fun delete(targetFile: String, onProgress: (ProgressState) -> Unit) = withContext(Dispatchers.IO) {
        onProgress(ProgressState(progress = 0))
        val files = File(targetFile).walkBottomUp().toList()
        val total = files.size.coerceAtLeast(1)
        files.forEachIndexed { index, file ->
            file.delete()
            onProgress(ProgressState(
                progress = (index + 1) * 100 / total,
                listSize = total,
                currentIndex = index + 1,
                currentFileName = file.name
            ))
        }
    }

    /**
     * [parentPath] 아래에 [name] 디렉터리를 생성한다.
     * @param parentPath 새 폴더를 생성할 부모 디렉터리 경로
     * @param name 생성할 폴더 이름
     */
    suspend fun makeDirectory(parentPath: String, name: String) = withContext(Dispatchers.IO) {
        val dir = File(parentPath, name)
        if (!dir.mkdir()) throw Exception("폴더 생성 실패: $name")
    }
}
