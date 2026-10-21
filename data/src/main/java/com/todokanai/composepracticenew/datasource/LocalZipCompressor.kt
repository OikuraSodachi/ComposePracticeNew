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
     * 폴더는 `이름/` 항목으로 함께 담아, 빈 폴더도 압축 해제 시 그대로 복원된다.
     * @param targetFiles 압축 대상 파일 경로 집합
     * @param zipFile 생성할 ZIP 파일의 절대 경로(희망 경로)
     * @param onProgress 진행 상태를 수신하는 콜백
     */
    suspend fun zip(targetFiles: Set<String>, zipFile: String, onProgress: (ProgressState) -> Unit) = withContext(Dispatchers.IO) {
        val roots = targetFiles.map(::File)
        // 위에서 아래로 순회하므로 폴더 항목이 항상 그 하위 항목보다 먼저 온다
        val allEntries = roots.flatMap { root -> root.walkTopDown().map { root to it }.toList() }
        if (allEntries.isEmpty()) throw Exception("압축할 항목이 없습니다.")
        val allFiles = allEntries.filter { (_, f) -> !f.isDirectory }
        val totalBytes = allFiles.sumOf { (_, f) -> f.length() }.coerceAtLeast(1)
        checkDiskSpace(totalBytes, File(zipFile).parentFile ?: File(zipFile))
            ?.let { throw Exception(it) }
        onProgress(ProgressState(progress = 0))
        val tracker = ByteProgressTracker(totalBytes, allFiles.size, onProgress)
        // 압축 대상 목록을 만든 뒤에 새 파일을 확보 — 기존 파일(선택 항목 자신 포함)은 절대 열지 않으므로 잘라내기·자기 참조가 생기지 않는다
        val output = createUniqueFile(File(zipFile))

        ZipOutputStream(output.outputStream()).use { zos ->
            allEntries.forEach { (root, sFile) ->
                if (sFile.isDirectory) {
                    zos.putNextEntry(ZipEntry("${entryNameOf(root, sFile)}/"))
                    zos.closeEntry()
                    return@forEach
                }
                tracker.startFile()
                zos.putNextEntry(ZipEntry(entryNameOf(root, sFile)))
                sFile.inputStream().use { input ->
                    tracker.pump(input, zos, sFile.name, sFile.length())
                }
                zos.closeEntry()
            }
        }
        tracker.reportComplete()
    }

    /**
     * [file]의 zip 항목 이름을 선택 항목 [root]의 부모 폴더 기준 상대 경로로 만든다.
     * 선택한 파일은 `a.txt`, 선택한 폴더의 하위 파일은 `src/a.txt`처럼 선택 항목 이름부터 시작한다.
     * @param root 사용자가 선택한 압축 대상(파일 또는 폴더)
     * @param file [root] 자신 또는 그 하위 파일
     * @return `/`로 구분한 zip 항목 이름
     */
    private fun entryNameOf(root: File, file: File): String {
        // 부모가 없는 경우는 파일시스템 루트를 선택한 것뿐이므로 루트 자신을 기준으로 한다
        val base = (root.absoluteFile.parentFile ?: root.absoluteFile).toPath()
        return base.relativize(file.absoluteFile.toPath()).toString().replace("\\", "/")
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
