package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.operation.CopyOperation
import com.todokanai.composepracticenew.operation.DeleteOperation
import com.todokanai.composepracticenew.operation.FtpDownloadOperation
import com.todokanai.composepracticenew.operation.FtpUploadOperation
import com.todokanai.composepracticenew.operation.MoveOperation
import com.todokanai.composepracticenew.operation.UnzipOperation
import com.todokanai.composepracticenew.operation.ZipOperation
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.repository.FileActionRepository
import com.todokanai.composepracticenew.repository.FtpRepository
import com.todokanai.composepracticenew.repository.ProgressRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * 파일 작업의 알림·진행 상태·실행 생명주기를 통합 조율하는 UseCase.
 * @param notifier 파일 작업 시스템 알림을 전송하는 인터페이스
 * @param progressRepo 작업 진행 상태 및 완료·오류 이벤트를 저장·방출하는 ProgressRepository
 * @param fileActionRepo 로컬 파일 변경 작업을 실행하는 FileActionRepository
 * @param ftpRepo FTP 파일 전송 작업을 실행하는 FtpRepository
 * @param appScope 파일 작업 코루틴을 실행할 애플리케이션 수명 CoroutineScope
 */
class FileOperationUseCase(
    private val notifier: FileOperationNotifier,
    private val progressRepo: ProgressRepository,
    private val fileActionRepo: FileActionRepository,
    private val ftpRepo: FtpRepository,
    private val appScope: CoroutineScope
) {
    private val instanceCounter = AtomicInteger(0)
    private val onEmitCompletion: (String) -> Unit = { progressRepo.emitCompletion(it) }
    private val onEmitError: (String) -> Unit = { progressRepo.emitError(it) }

    private fun nextInstanceId(): Int = instanceCounter.incrementAndGet()

    /** 출발지 파일 중 하나라도 목적지와 동일한 경로이면 true를 반환한다. */
    private fun isSamePath(files: Set<String>, destPath: String): Boolean =
        files.any { srcPath ->
            val srcName = srcPath.substringAfterLast('/')
            "$destPath/$srcName" == srcPath
        }

    /** [files]를 [destPath]에 복사한다. */
    fun copy(files: Set<String>, destPath: String, completionMessage: String, onRefresh: suspend () -> Unit) {
        if (isSamePath(files, destPath)) {
            progressRepo.emitError("복사 실패: 출발지와 목적지가 동일합니다.")
            return
        }
        val instanceId = nextInstanceId()
        CopyOperation(
            targetFiles = files,
            targetPath = destPath,
            fileActionRepo = fileActionRepo,
            notifier = notifier,
            instanceId = instanceId,
            completionMessage = completionMessage,
            onRefresh = onRefresh,
            onEmitCompletion = onEmitCompletion,
            onEmitError = onEmitError,
            setProgress = { progressRepo.setProgressState(instanceId, it) },
            clearProgress = { progressRepo.removeProgress(instanceId) }
        ).execute(appScope)
    }

    /** [files]를 삭제한다. */
    fun delete(files: Set<String>, completionMessage: String, onRefresh: suspend () -> Unit) {
        val instanceId = nextInstanceId()
        DeleteOperation(
            targetFiles = files,
            fileActionRepo = fileActionRepo,
            notifier = notifier,
            instanceId = instanceId,
            completionMessage = completionMessage,
            onRefresh = onRefresh,
            onEmitCompletion = onEmitCompletion,
            onEmitError = onEmitError,
            setProgress = { progressRepo.setProgressState(instanceId, it) },
            clearProgress = { progressRepo.removeProgress(instanceId) }
        ).execute(appScope)
    }

    /** [files]를 [destPath]로 이동한다. */
    fun move(files: Set<String>, destPath: String, completionMessage: String, onRefresh: suspend () -> Unit) {
        if (isSamePath(files, destPath)) {
            progressRepo.emitError("이동 실패: 출발지와 목적지가 동일합니다.")
            return
        }
        val instanceId = nextInstanceId()
        MoveOperation(
            targetFiles = files,
            targetPath = destPath,
            fileActionRepo = fileActionRepo,
            notifier = notifier,
            instanceId = instanceId,
            completionMessage = completionMessage,
            onRefresh = onRefresh,
            onEmitCompletion = onEmitCompletion,
            onEmitError = onEmitError,
            setProgress = { progressRepo.setProgressState(instanceId, it) },
            clearProgress = { progressRepo.removeProgress(instanceId) }
        ).execute(appScope)
    }

    /** [files]를 [zipFilePath]로 압축한다. */
    fun zip(files: Set<String>, zipFilePath: String, completionMessage: String, onRefresh: suspend () -> Unit) {
        val instanceId = nextInstanceId()
        ZipOperation(
            sourceFiles = files,
            zipFilePath = zipFilePath,
            fileActionRepo = fileActionRepo,
            notifier = notifier,
            instanceId = instanceId,
            completionMessage = completionMessage,
            onRefresh = onRefresh,
            onEmitCompletion = onEmitCompletion,
            onEmitError = onEmitError,
            setProgress = { progressRepo.setProgressState(instanceId, it) },
            clearProgress = { progressRepo.removeProgress(instanceId) }
        ).execute(appScope)
    }

    /** [zipFiles]를 [destPath]에 압축 해제한다. */
    fun unzip(zipFiles: Set<String>, destPath: String, unzipHere: Boolean, completionMessage: String, onRefresh: suspend () -> Unit) {
        val instanceId = nextInstanceId()
        UnzipOperation(
            zipFiles = zipFiles,
            destPath = destPath,
            unzipHere = unzipHere,
            fileActionRepo = fileActionRepo,
            notifier = notifier,
            instanceId = instanceId,
            completionMessage = completionMessage,
            onRefresh = onRefresh,
            onEmitCompletion = onEmitCompletion,
            onEmitError = onEmitError,
            setProgress = { progressRepo.setProgressState(instanceId, it) },
            clearProgress = { progressRepo.removeProgress(instanceId) }
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
        val instanceId = nextInstanceId()
        val localFilePath = "$localDestPath/${remotePath.substringAfterLast("/")}"
        FtpDownloadOperation(
            remotePath = remotePath,
            localDestPath = localFilePath,
            isDirectory = isDirectory,
            ftpRepo = ftpRepo,
            notifier = notifier,
            instanceId = instanceId,
            completionMessage = completionMessage,
            onRefresh = onRefresh,
            onEmitCompletion = onEmitCompletion,
            onEmitError = { onError(it) },
            setProgress = { progressRepo.setProgressState(instanceId, it) },
            clearProgress = { progressRepo.removeProgress(instanceId) }
        ).execute(appScope)
    }

    /** [localPath]를 [remoteDestPath]에 업로드한다. */
    fun upload(localPath: String, remoteDestPath: String, completionMessage: String, onRefresh: suspend () -> Unit) {
        val instanceId = nextInstanceId()
        val remoteFilePath = "$remoteDestPath/${localPath.substringAfterLast("/")}"
        FtpUploadOperation(
            localPath = localPath,
            remoteDestPath = remoteFilePath,
            ftpRepo = ftpRepo,
            notifier = notifier,
            instanceId = instanceId,
            completionMessage = completionMessage,
            onRefresh = onRefresh,
            onEmitCompletion = onEmitCompletion,
            onEmitError = onEmitError,
            setProgress = { progressRepo.setProgressState(instanceId, it) },
            clearProgress = { progressRepo.removeProgress(instanceId) }
        ).execute(appScope)
    }

    /** [path]의 원격 파일 이름을 [newName]으로 변경한다. 성공 시 시스템 알림·완료 메시지를 발행하고 onRefresh를 호출한다. */
    fun renameRemote(path: String, newName: String, completionMessage: String, onError: () -> Unit, onRefresh: suspend () -> Unit) {
        appScope.launch {
            val toPath = "${path.substringBeforeLast("/")}/$newName"
            val ok = ftpRepo.rename(path, toPath)
            if (ok) {
                notifier.completedNotification("", completionMessage)
                progressRepo.emitCompletion(completionMessage)
                onRefresh()
            } else onError()
        }
    }

    /** [path]의 원격 파일 또는 디렉터리를 삭제한다. 성공 시 시스템 알림·완료 메시지를 발행하고 onRefresh를 호출한다. */
    fun deleteRemote(path: String, isDirectory: Boolean, completionMessage: String, onError: () -> Unit, onRefresh: suspend () -> Unit) {
        appScope.launch {
            val ok = if (isDirectory) {
                try { ftpRepo.removeDirectoryRecursive(path) } catch (e: CancellationException) { false }
            } else ftpRepo.deleteFile(path)
            if (ok) {
                notifier.completedNotification("", completionMessage)
                progressRepo.emitCompletion(completionMessage)
                onRefresh()
            } else onError()
        }
    }

    /** [parentPath] 아래에 [dirName] 디렉터리를 원격 서버에 생성한다. 성공 시 시스템 알림·완료 메시지를 발행하고 onRefresh를 호출한다. */
    fun makeDirectoryRemote(parentPath: String, dirName: String, completionMessage: String, onError: () -> Unit, onRefresh: suspend () -> Unit) {
        appScope.launch {
            val ok = ftpRepo.makeDirectory("$parentPath/$dirName")
            if (ok) {
                notifier.completedNotification("", completionMessage)
                progressRepo.emitCompletion(completionMessage)
                onRefresh()
            } else onError()
        }
    }

    /** [path]의 파일 이름을 [newName]으로 변경한다. */
    fun rename(path: String, newName: String, completionMessage: String, onRefresh: suspend () -> Unit) {
        appScope.launch {
            try {
                fileActionRepo.renameFile(path, newName).collect {}
                notifier.completedNotification("", completionMessage)
                progressRepo.emitCompletion(completionMessage)
            } catch (e: Exception) {
                progressRepo.emitError(e.message ?: "이름 변경 중 오류")
            } finally {
                onRefresh()
            }
        }
    }
}
