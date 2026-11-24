package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.datasource.LocalArchiveExtraction
import com.todokanai.composepracticenew.model.ArchiveEntrySelection
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressPhase
import javax.inject.Inject

/**
 * 로컬 압축 파일에서 고른 항목만 로컬 폴더로 푸는 ArchiveEntryCopyRepository. 압축 해제와 같은 [LocalArchiveExtraction]에 맡긴다.
 * @param archiveExtraction 형식별 압축 해제를 조율하는 LocalArchiveExtraction
 */
class ArchiveEntryCopyRepositoryImpl @Inject constructor(
    private val archiveExtraction: LocalArchiveExtraction
) : ArchiveEntryCopyRepository {
    override suspend fun extractEntries(
        selection: ArchiveEntrySelection,
        destPath: String,
        resolveConflict: WriteConflictResolver,
        askPassword: ArchivePasswordProvider,
        onProgress: (ProgressPhase) -> Unit
    ): IoResult<Unit> = archiveExtraction.extractEntries(selection, destPath, resolveConflict, askPassword, onProgress)
}
