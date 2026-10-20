package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.datasource.LocalFileChecks.checkDiskSpace
import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/** 로컬 파일·디렉터리를 하나의 ZIP 파일로 압축한다. */
class LocalZipCompressor @Inject constructor() {

    /**
     * [targetFiles]를 [zipFile]로 압축한다. [zipFile]이 이미 있으면 덮어쓰지 않고 `이름 (n).zip` 형태의 새 이름으로 만든다.
     * @param targetFiles 압축 대상 파일 경로 집합
     * @param zipFile 생성할 ZIP 파일의 절대 경로(희망 경로)
     * @param onProgress 진행 상태를 수신하는 콜백
     */
    suspend fun zip(targetFiles: Set<String>, zipFile: String, onProgress: (ProgressState) -> Unit) = withContext(Dispatchers.IO) {
        val roots = targetFiles.map(::File)
        var totalBytesAcc = 0L
        val allFiles = roots.flatMap { root ->
            root.walkTopDown().filter { f -> !f.isDirectory }
                .onEach { totalBytesAcc += it.length() }
                .map { root to it }
                .toList()
        }
        if (allFiles.isEmpty()) throw Exception("압축할 파일이 없습니다.")
        val totalBytes = totalBytesAcc.coerceAtLeast(1)
        checkDiskSpace(totalBytes, File(zipFile).parentFile ?: File(zipFile))
            ?.let { throw Exception(it) }
        onProgress(ProgressState(progress = 0))
        val tracker = ByteProgressTracker(totalBytes, allFiles.size, onProgress)
        // 압축 대상 목록을 만든 뒤에 새 파일을 확보 — 기존 파일(선택 항목 자신 포함)은 절대 열지 않으므로 잘라내기·자기 참조가 생기지 않는다
        val output = createUniqueFile(File(zipFile))

        ZipOutputStream(output.outputStream()).use { zos ->
            allFiles.forEach { (root, sFile) ->
                tracker.startFile()
                val entryName = root.toPath().relativize(sFile.toPath())
                    .toString().replace("\\", "/")
                zos.putNextEntry(ZipEntry(entryName))
                sFile.inputStream().use { input ->
                    tracker.pump(input, zos, sFile.name, sFile.length())
                }
                zos.closeEntry()
            }
        }
        tracker.reportComplete()
    }

    /**
     * [desired] 경로에 새 파일을 만들어 반환한다. 이미 있으면 `이름 (1).확장자`, `이름 (2).확장자` … 순으로 비어 있는 이름을 찾는다.
     * 존재 확인과 생성을 [File.createNewFile] 한 번으로 처리해, 확인 직후 다른 곳에서 같은 이름이 생겨도 덮어쓰지 않는다.
     * @param desired 사용자가 지정한 생성 경로
     * @return 이 호출이 새로 만든 빈 파일
     */
    private fun createUniqueFile(desired: File): File =
        generateSequence(0) { it + 1 }
            .map { index -> numberedFile(desired, index) }
            .first { it.createNewFile() }

    /**
     * [desired]에 번호 [index]를 붙인 후보 경로를 만든다. 0이면 원래 경로 그대로다.
     * @param desired 사용자가 지정한 생성 경로
     * @param index 붙일 번호
     * @return 후보 파일 경로
     */
    private fun numberedFile(desired: File, index: Int): File {
        if (index == 0) return desired
        val extension = desired.extension.let { if (it.isEmpty()) "" else ".$it" }
        return File(desired.parentFile, "${desired.nameWithoutExtension} ($index)$extension")
    }
}
