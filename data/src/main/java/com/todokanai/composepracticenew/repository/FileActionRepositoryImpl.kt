package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.datasource.LocalArchiveExtraction
import com.todokanai.composepracticenew.datasource.LocalEntryMutator
import com.todokanai.composepracticenew.datasource.LocalTreeTransfer
import com.todokanai.composepracticenew.datasource.LocalZipCompressor
import com.todokanai.composepracticenew.model.ExtractLayout
import com.todokanai.composepracticenew.model.ProgressState
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 로컬 파일 시스템에서 FileActionRepository 작업을 작업 단위 구현에 나눠 맡기는 저장소.
 * 스토리지 백엔드는 FileActionRepository 바인딩으로 바꾼다.
 * @param treeTransfer 복사·이동을 수행하는 LocalTreeTransfer
 * @param zipCompressor ZIP 압축을 수행하는 LocalZipCompressor
 * @param archiveExtraction 형식별 압축 해제를 조율하는 LocalArchiveExtraction
 * @param entryMutator 이름변경·삭제·폴더 생성을 수행하는 LocalEntryMutator
 */
@Singleton
class FileActionRepositoryImpl @Inject constructor(
    private val treeTransfer: LocalTreeTransfer,
    private val zipCompressor: LocalZipCompressor,
    private val archiveExtraction: LocalArchiveExtraction,
    private val entryMutator: LocalEntryMutator
) : FileActionRepository {
    override suspend fun zipAction(targetFiles: Set<String>, parentPath: String, zipName: String, onProgress: (ProgressState) -> Unit) =
        zipCompressor.zip(targetFiles, File(parentPath, zipName).path, onProgress)
    override suspend fun copyAction(targetFiles: Set<String>, targetPath: String, onProgress: (ProgressState) -> Unit) = treeTransfer.copy(targetFiles, targetPath, onProgress)
    override suspend fun renameFile(targetFile: String, newName: String, overwrite: Boolean) = entryMutator.rename(targetFile, newName, overwrite)
    override suspend fun deleteFile(targetFile: String, onProgress: (ProgressState) -> Unit) = entryMutator.delete(targetFile, onProgress)
    override suspend fun moveFile(targetFiles: Set<String>, targetPath: String, onProgress: (ProgressState) -> Unit) = treeTransfer.move(targetFiles, targetPath, onProgress)
    override suspend fun unzipAction(zipFiles: Set<String>, destPath: String, layout: ExtractLayout, onProgress: (ProgressState) -> Unit) =
        archiveExtraction.unzip(zipFiles, destPath, layout, onProgress)
    override suspend fun extractedNames(zipFile: String, destPath: String, layout: ExtractLayout) = archiveExtraction.extractedNames(zipFile, destPath, layout)
    override fun canExtract(path: String) = archiveExtraction.canExtract(path)
    override suspend fun makeDirectory(parentPath: String, name: String) = entryMutator.makeDirectory(parentPath, name)
}
