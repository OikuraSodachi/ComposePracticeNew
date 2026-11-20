package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.ftp.UploadAction
import com.todokanai.composepracticenew.data.ftp.uploadActionOf
import com.todokanai.composepracticenew.datasource.LocalEntryKind
import com.todokanai.composepracticenew.datasource.entryKind
import com.todokanai.composepracticenew.datasource.localTreeTopDown
import com.todokanai.composepracticenew.datasource.slashRelativePathOf
import com.todokanai.composepracticenew.model.FolderEntryNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * 폴더 업로드가 로컬 폴더 아래에 만들 이름을 읽는 FolderEntryNamesRepository 구현체. 업로드 전 충돌 확인(`UploadConflictCheckUseCase`)이 쓴다.
 * 폴더 업로드(`FtpUploader.uploadDirectory`)와 같은 순회(`localTreeTopDown`)와 같은 항목 규칙([uploadActionOf])으로 읽어, 업로드가 만들 이름을 그대로 얻는다.
 */
class UploadEntryNamesRepositoryImpl @Inject constructor() : FolderEntryNamesRepository {

    /**
     * [paths] 중 폴더마다 그 아래 모든 폴더(자신 포함)의 바로 안 항목 이름을 읽는다. 폴더가 아닌 경로는 건너뛰고, 코루틴이 취소되면 멈춘다.
     * 업로드가 건너뛰는 항목(링크, 나열하지 못한 폴더)은 이름에 넣지 않는다. 경로 자신이 링크면 읽지 않는다.
     * @param paths 항목의 절대 경로 목록
     * @return 폴더별 항목 이름(바로 안에 항목이 있는 폴더만, 위 폴더부터)
     */
    override suspend fun folderEntryNames(paths: List<String>): List<FolderEntryNames> = withContext(Dispatchers.IO) {
        val job = coroutineContext.job
        paths.map(::File).filter { it.entryKind == LocalEntryKind.DIRECTORY }.flatMap { root -> entryNamesUnder(root, job) }
    }

    /**
     * [root] 아래 폴더마다 바로 안 항목 이름을 모은다.
     * @param root 읽기 시작할 폴더
     * @param job 취소를 확인할 작업
     * @return 폴더별 항목 이름(처음 나온 순서)
     */
    private fun entryNamesUnder(root: File, job: Job): List<FolderEntryNames> {
        val namesByFolder = LinkedHashMap<File, MutableList<String>>()
        root.localTreeTopDown().drop(1).forEach { entry ->
            job.ensureActive()
            if (uploadActionOf(entry.kind) !is UploadAction.Skip) namesByFolder.getOrPut(entry.file.parentFile) { mutableListOf() } += entry.file.name
        }
        return namesByFolder.map { (folder, names) -> FolderEntryNames(folderLabelOf(root, folder), names) }
    }

    /**
     * [folder]의 표시용 경로를 [root]의 이름부터 '/'로 이어 만든다.
     * @param root 읽기 시작한 폴더
     * @param folder [root] 자신 또는 그 아래 폴더
     * @return 표시용 경로
     */
    private fun folderLabelOf(root: File, folder: File): String =
        root.slashRelativePathOf(folder).let { relative -> if (relative.isEmpty()) root.name else "${root.name}/$relative" }
}
