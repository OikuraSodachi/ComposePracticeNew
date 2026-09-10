package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.operation.CopyOperation
import com.todokanai.composepracticenew.operation.DeleteOperation
import com.todokanai.composepracticenew.operation.FtpDownloadOperation
import com.todokanai.composepracticenew.operation.FtpUploadOperation
import com.todokanai.composepracticenew.operation.MoveOperation
import com.todokanai.composepracticenew.operation.UnzipOperation
import com.todokanai.composepracticenew.operation.ZipOperation
import com.todokanai.composepracticenew.repository.FileOperationNotifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 파일 작업의 알림·진행 상태·실행 생명주기를 통합 조율하는 UseCase. */
class FileOperationUseCase(
    private val notifier: FileOperationNotifier,
    private val progressUseCase: ProgressUseCase,
    private val fileActionUseCase: FileActionUseCase,
    private val ftpUseCase: FtpUseCase,
    private val appScope: CoroutineScope
) {
    /** [files]를 [destPath]에 복사한다. */
    fun copy(files: List<String>, destPath: String, completionMessage: String, onRefresh: suspend () -> Unit) {
        val instanceId = progressUseCase.nextInstanceId()
        CopyOperation(
            targetFiles = files,
            targetPath = destPath,
            fileActionUseCase = fileActionUseCase,
            notifier = notifier,
            instanceId = instanceId,
            completionMessage = completionMessage,
            onRefresh = onRefresh,
            onEmitCompletion = { progressUseCase.emitCompletion(it) },
            onEmitError = { progressUseCase.emitError(it) },
            setProgress = { progressUseCase.setProgressState(instanceId, it) },
            clearProgress = { progressUseCase.removeProgress(instanceId) }
        ).execute(appScope)
    }

    /** [files]를 삭제한다. */
    fun delete(files: List<String>, completionMessage: String, onRefresh: suspend () -> Unit) {
        val instanceId = progressUseCase.nextInstanceId()
        DeleteOperation(
            targetFiles = files,
            fileActionUseCase = fileActionUseCase,
            notifier = notifier,
            instanceId = instanceId,
            completionMessage = completionMessage,
            onRefresh = onRefresh,
            onEmitCompletion = { progressUseCase.emitCompletion(it) },
            onEmitError = { progressUseCase.emitError(it) },
            setProgress = { progressUseCase.setProgressState(instanceId, it) },
            clearProgress = { progressUseCase.removeProgress(instanceId) }
        ).execute(appScope)
    }

    /** [files]를 [destPath]로 이동한다. */
    fun move(files: List<String>, destPath: String, completionMessage: String, onRefresh: suspend () -> Unit) {
        val instanceId = progressUseCase.nextInstanceId()
        MoveOperation(
            targetFiles = files,
            targetPath = destPath,
            fileActionUseCase = fileActionUseCase,
            notifier = notifier,
            instanceId = instanceId,
            completionMessage = completionMessage,
            onRefresh = onRefresh,
            onEmitCompletion = { progressUseCase.emitCompletion(it) },
            onEmitError = { progressUseCase.emitError(it) },
            setProgress = { progressUseCase.setProgressState(instanceId, it) },
            clearProgress = { progressUseCase.removeProgress(instanceId) }
        ).execute(appScope)
    }

    /** [files]를 [zipFilePath]로 압축한다. */
    fun zip(files: List<String>, zipFilePath: String, completionMessage: String, onRefresh: suspend () -> Unit) {
        val instanceId = progressUseCase.nextInstanceId()
        ZipOperation(
            sourceFiles = files,
            zipFilePath = zipFilePath,
            fileActionUseCase = fileActionUseCase,
            notifier = notifier,
            instanceId = instanceId,
            completionMessage = completionMessage,
            onRefresh = onRefresh,
            onEmitCompletion = { progressUseCase.emitCompletion(it) },
            onEmitError = { progressUseCase.emitError(it) },
            setProgress = { progressUseCase.setProgressState(instanceId, it) },
            clearProgress = { progressUseCase.removeProgress(instanceId) }
        ).execute(appScope)
    }

    /** [zipFiles]를 [destPath]에 압축 해제한다. */
    fun unzip(zipFiles: List<String>, destPath: String, unzipHere: Boolean, completionMessage: String, onRefresh: suspend () -> Unit) {
        val instanceId = progressUseCase.nextInstanceId()
        UnzipOperation(
            zipFiles = zipFiles,
            destPath = destPath,
            unzipHere = unzipHere,
            fileActionUseCase = fileActionUseCase,
            notifier = notifier,
            instanceId = instanceId,
            completionMessage = completionMessage,
            onRefresh = onRefresh,
            onEmitCompletion = { progressUseCase.emitCompletion(it) },
            onEmitError = { progressUseCase.emitError(it) },
            setProgress = { progressUseCase.setProgressState(instanceId, it) },
            clearProgress = { progressUseCase.removeProgress(instanceId) }
        ).execute(appScope)
    }

    /**
     * [remotePath]를 [localDestPath]에 다운로드한다.
     * @param onError 다운로드 오류 시 호출되는 콜백 — 호출부마다 오류 라우팅이 다르므로 위임한다.
     */
    fun download(
        remotePath: String,
        localDestPath: String,
        isDirectory: Boolean,
        completionMessage: String,
        onRefresh: suspend () -> Unit,
        onError: (String) -> Unit
    ) {
        val instanceId = remotePath.hashCode()
        FtpDownloadOperation(
            remotePath = remotePath,
            localDestPath = localDestPath,
            isDirectory = isDirectory,
            ftpUseCase = ftpUseCase,
            notifier = notifier,
            instanceId = instanceId,
            completionMessage = completionMessage,
            onRefresh = onRefresh,
            onEmitCompletion = { progressUseCase.emitCompletion(it) },
            onEmitError = { onError(it) },
            setProgress = { progressUseCase.setProgressState(instanceId, it) },
            clearProgress = { progressUseCase.removeProgress(instanceId) }
        ).execute(appScope)
    }

    /** [localPath]를 [remoteDestPath]에 업로드한다. */
    fun upload(localPath: String, remoteDestPath: String, completionMessage: String, onRefresh: suspend () -> Unit) {
        val instanceId = localPath.hashCode()
        FtpUploadOperation(
            localPath = localPath,
            remoteDestPath = remoteDestPath,
            ftpUseCase = ftpUseCase,
            notifier = notifier,
            instanceId = instanceId,
            completionMessage = completionMessage,
            onRefresh = onRefresh,
            onEmitCompletion = { progressUseCase.emitCompletion(it) },
            onEmitError = { progressUseCase.emitError(it) },
            setProgress = { progressUseCase.setProgressState(instanceId, it) },
            clearProgress = { progressUseCase.removeProgress(instanceId) }
        ).execute(appScope)
    }

    /** [path]의 파일 이름을 [newName]으로 변경한다. */
    fun rename(path: String, newName: String, completionMessage: String, onRefresh: suspend () -> Unit) {
        appScope.launch {
            try {
                fileActionUseCase.renameFile(path, newName).collect {}
                notifier.completedNotification("", completionMessage)
                progressUseCase.emitCompletion(completionMessage)
                onRefresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                progressUseCase.emitError(e.message ?: "이름 변경 중 오류")
                onRefresh()
            }
        }
    }
}
