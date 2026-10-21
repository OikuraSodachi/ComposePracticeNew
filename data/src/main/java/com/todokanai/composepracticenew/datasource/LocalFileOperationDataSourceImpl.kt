package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ProgressState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LocalFileOperationDataSource 계약을 작업 단위 구현에 위임하는 파사드.
 * @param treeTransfer 복사·이동을 수행하는 LocalTreeTransfer
 * @param zipCompressor ZIP 압축을 수행하는 LocalZipCompressor
 * @param archiveExtraction 형식별 압축 해제를 조율하는 LocalArchiveExtraction
 * @param entryMutator 이름변경·삭제·폴더 생성을 수행하는 LocalEntryMutator
 */
@Singleton
class LocalFileOperationDataSourceImpl @Inject constructor(
    private val treeTransfer: LocalTreeTransfer,
    private val zipCompressor: LocalZipCompressor,
    private val archiveExtraction: LocalArchiveExtraction,
    private val entryMutator: LocalEntryMutator
) : LocalFileOperationDataSource {

    override suspend fun zipAction(targetFiles: Set<String>, zipFile: String, onProgress: (ProgressState) -> Unit) =
        zipCompressor.zip(targetFiles, zipFile, onProgress)

    override suspend fun copyAction(targetFiles: Set<String>, targetPath: String, onProgress: (ProgressState) -> Unit) =
        treeTransfer.copy(targetFiles, targetPath, onProgress)

    override suspend fun renameFile(targetFile: String, newName: String, overwrite: Boolean) =
        entryMutator.rename(targetFile, newName, overwrite)

    override suspend fun deleteFile(targetFile: String, onProgress: (ProgressState) -> Unit) =
        entryMutator.delete(targetFile, onProgress)

    override suspend fun moveFile(targetFiles: Set<String>, targetPath: String, onProgress: (ProgressState) -> Unit) =
        treeTransfer.move(targetFiles, targetPath, onProgress)

    override suspend fun unzipAction(zipFiles: Set<String>, destPath: String, unzipHere: Boolean, onProgress: (ProgressState) -> Unit) =
        archiveExtraction.unzip(zipFiles, destPath, unzipHere, onProgress)

    override suspend fun makeDirectory(parentPath: String, name: String) =
        entryMutator.makeDirectory(parentPath, name)
}
