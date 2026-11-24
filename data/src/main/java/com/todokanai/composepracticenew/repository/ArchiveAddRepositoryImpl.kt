package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.datasource.ZipArchiveAppend
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressPhase
import javax.inject.Inject

/**
 * 로컬 파일·폴더를 로컬 ZIP 압축 파일에 넣는 ArchiveAddRepository. 넣을 수 있는 형식은 ZIP뿐이라 [ZipArchiveAppend]에 바로 맡긴다.
 * @param zipAppend ZIP에 항목을 넣는 ZipArchiveAppend
 */
class ArchiveAddRepositoryImpl @Inject constructor(
    private val zipAppend: ZipArchiveAppend
) : ArchiveAddRepository {

    override fun canAddTo(archivePath: String): Boolean = zipAppend.canAddTo(archivePath)

    override suspend fun addFiles(
        sources: Set<String>,
        archivePath: String,
        folderNames: List<String>,
        resolveConflict: WriteConflictResolver,
        askPassword: ArchivePasswordProvider,
        onProgress: (ProgressPhase) -> Unit
    ): IoResult<Unit> = zipAppend.addFiles(sources, archivePath, folderNames, resolveConflict, askPassword, onProgress)
}
