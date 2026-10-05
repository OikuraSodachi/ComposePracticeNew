package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_COPY
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_DELETE
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_DOWNLOAD
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_MOVE
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_UNZIP
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_UPLOAD
import com.todokanai.composepracticenew.myobjects.OperationConstants.ACTION_KEY_ZIP
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.operation.OperationCallback
import com.todokanai.composepracticenew.operation.ProgressHandle
import com.todokanai.composepracticenew.operation.launchOperation
import com.todokanai.composepracticenew.repository.FileActionRepository
import com.todokanai.composepracticenew.repository.FtpRepository
import com.todokanai.composepracticenew.repository.ProgressRepository
import kotlinx.coroutines.CoroutineScope
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

    private fun nextInstanceId() = instanceCounter.incrementAndGet()

    private fun progressHandle() = ProgressHandle(nextInstanceId(), progressRepo)

    private fun callback(
        completionMessage: String,
        actionKey: Int? = null,
        onRefresh: suspend () -> Unit,
        onErrorOverride: ((String) -> Unit)? = null
    ) = OperationCallback(notifier, progressRepo, completionMessage, actionKey, onRefresh, onErrorOverride)

    /** 출발지 파일 중 하나라도 목적지와 동일한 경로이면 true를 반환한다. */
    private fun isSamePath(files: Set<String>, destPath: String): Boolean =
        files.any { srcPath ->
            val srcName = srcPath.substringAfterLast('/')
            "$destPath/$srcName" == srcPath
        }

    /**
     * [files]를 [destPath]에 복사한다.
     * @param files 복사할 파일의 절대 경로 집합
     * @param destPath 복사 대상 디렉터리의 절대 경로
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun copy(files: Set<String>, destPath: String, completionMessage: String, onRefresh: suspend () -> Unit) {
        if (isSamePath(files, destPath)) {
            progressRepo.emitError("복사 실패: 출발지와 목적지가 동일합니다.")
            return
        }
        val handle = progressHandle()
        appScope.launchOperation(handle, callback(completionMessage, ACTION_KEY_COPY, onRefresh)) {
            fileActionRepo.copyAction(files, destPath) { state ->
                handle.set(state.copy(actionKey = ACTION_KEY_COPY))
                state.progress?.let { notifier.copyProgressNoti(it, handle.instanceId) }
            }
        }
    }

    /**
     * [files]를 삭제한다.
     * @param files 삭제할 파일의 절대 경로 집합
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun delete(files: Set<String>, completionMessage: String, onRefresh: suspend () -> Unit) {
        val handle = progressHandle()
        appScope.launchOperation(handle, callback(completionMessage, ACTION_KEY_DELETE, onRefresh)) {
            files.forEach { path ->
                fileActionRepo.deleteFile(path) { state ->
                    handle.set(state.copy(actionKey = ACTION_KEY_DELETE))
                    val idx = state.currentIndex ?: return@deleteFile
                    val total = state.listSize ?: return@deleteFile
                    notifier.deleteProgressNoti(idx, total, handle.instanceId)
                }
            }
        }
    }

    /**
     * [files]를 [destPath]로 이동한다.
     * @param files 이동할 파일의 절대 경로 집합
     * @param destPath 이동 대상 디렉터리의 절대 경로
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun move(files: Set<String>, destPath: String, completionMessage: String, onRefresh: suspend () -> Unit) {
        if (isSamePath(files, destPath)) {
            progressRepo.emitError("이동 실패: 출발지와 목적지가 동일합니다.")
            return
        }
        val handle = progressHandle()
        appScope.launchOperation(handle, callback(completionMessage, ACTION_KEY_MOVE, onRefresh)) {
            fileActionRepo.moveFile(files, destPath) { state ->
                handle.set(state.copy(actionKey = ACTION_KEY_MOVE))
                state.progress?.let { notifier.moveProgressNoti(it, handle.instanceId) }
            }
        }
    }

    /**
     * [files]를 [zipFilePath]로 압축한다.
     * @param files 압축할 파일의 절대 경로 집합
     * @param zipFilePath 생성할 zip 파일의 절대 경로
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun zip(files: Set<String>, zipFilePath: String, completionMessage: String, onRefresh: suspend () -> Unit) {
        val handle = progressHandle()
        appScope.launchOperation(handle, callback(completionMessage, ACTION_KEY_ZIP, onRefresh)) {
            fileActionRepo.zipAction(files, zipFilePath) { state ->
                handle.set(state.copy(actionKey = ACTION_KEY_ZIP))
                state.progress?.let { notifier.zipProgressNoti(it, handle.instanceId) }
            }
        }
    }

    /**
     * [zipFiles]를 [destPath]에 압축 해제한다.
     * @param zipFiles 압축 해제할 zip 파일의 절대 경로 집합
     * @param destPath 압축 해제 대상 디렉터리의 절대 경로
     * @param unzipHere true이면 [destPath]에 직접 해제, false이면 zip 파일명 하위 폴더 생성 후 해제
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun unzip(zipFiles: Set<String>, destPath: String, unzipHere: Boolean, completionMessage: String, onRefresh: suspend () -> Unit) {
        val handle = progressHandle()
        appScope.launchOperation(handle, callback(completionMessage, ACTION_KEY_UNZIP, onRefresh)) {
            fileActionRepo.unzipAction(zipFiles, destPath, unzipHere) { state ->
                handle.set(state.copy(actionKey = ACTION_KEY_UNZIP))
                state.progress?.let { notifier.unzipProgressNoti(it, handle.instanceId) }
            }
        }
    }

    /**
     * [remotePath]를 [localDestPath]에 다운로드한다.
     * @param remotePath 다운로드할 원격 파일 또는 디렉터리의 경로
     * @param localDestPath 로컬 저장 대상 디렉터리의 절대 경로
     * @param isDirectory 원격 경로가 디렉터리이면 true
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @param onError 다운로드 오류 시 호출되는 콜백 — 호출부마다 오류 라우팅이 다르므로 위임한다.
     * @return Unit
     */
    fun download(
        remotePath: String,
        localDestPath: String,
        isDirectory: Boolean,
        completionMessage: String,
        onRefresh: suspend () -> Unit,
        onError: (String) -> Unit
    ) {
        val handle = progressHandle()
        val localFilePath = "$localDestPath/${remotePath.substringAfterLast("/")}"
        appScope.launchOperation(handle, callback(completionMessage, ACTION_KEY_DOWNLOAD, onRefresh, onErrorOverride = onError)) {
            ftpRepo.download(remotePath, localFilePath, isDirectory) { state ->
                handle.set(state.copy(actionKey = ACTION_KEY_DOWNLOAD))
                state.progress?.let { notifier.downloadProgressNoti(it, handle.instanceId) }
            }.collect {}
        }
    }

    /**
     * [localPath]를 [remoteDestPath]에 업로드한다.
     * @param localPath 업로드할 로컬 파일의 절대 경로
     * @param remoteDestPath 업로드 대상 원격 디렉터리 경로
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun upload(localPath: String, remoteDestPath: String, completionMessage: String, onRefresh: suspend () -> Unit) {
        val handle = progressHandle()
        val remoteFilePath = "$remoteDestPath/${localPath.substringAfterLast("/")}"
        appScope.launchOperation(handle, callback(completionMessage, ACTION_KEY_UPLOAD, onRefresh)) {
            ftpRepo.upload(localPath, remoteFilePath) { state ->
                handle.set(state.copy(actionKey = ACTION_KEY_UPLOAD))
                state.progress?.let { notifier.uploadProgressNoti(it, handle.instanceId) }
            }.collect {}
        }
    }

    /**
     * [path]의 파일 이름을 [newName]으로 변경한다.
     * @param path 이름을 변경할 파일의 절대 경로
     * @param newName 변경할 새 파일 이름 (전체 경로가 아닌 이름만)
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun rename(path: String, newName: String, completionMessage: String, onRefresh: suspend () -> Unit) {
        val handle = progressHandle()
        appScope.launchOperation(handle, callback(completionMessage, onRefresh = onRefresh)) {
            fileActionRepo.renameFile(path, newName)
        }
    }

    /**
     * [parentPath] 아래에 [name] 이름의 디렉터리를 생성한다.
     * @param parentPath 디렉터리를 생성할 부모 디렉터리의 절대 경로
     * @param name 생성할 디렉터리 이름
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun makeDirectory(parentPath: String, name: String, completionMessage: String, onRefresh: suspend () -> Unit) {
        val handle = progressHandle()
        appScope.launchOperation(handle, callback(completionMessage, onRefresh = onRefresh)) {
            fileActionRepo.makeDirectory(parentPath, name)
        }
    }

    /**
     * [path]의 원격 파일 이름을 [newName]으로 변경한다.
     * @param path 이름을 변경할 원격 파일의 경로
     * @param newName 변경할 새 파일 이름 (전체 경로가 아닌 이름만)
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun renameRemote(path: String, newName: String, completionMessage: String, onRefresh: suspend () -> Unit) {
        val handle = progressHandle()
        appScope.launchOperation(handle, callback(completionMessage, onRefresh = onRefresh)) {
            val toPath = "${path.substringBeforeLast("/")}/$newName"
            if (!ftpRepo.rename(path, toPath)) throw Exception("rename 실패: ${path.substringAfterLast("/")}")
        }
    }

    /**
     * [path]의 원격 파일 또는 디렉터리를 삭제한다.
     * @param path 삭제할 원격 파일 또는 디렉터리의 경로
     * @param isDirectory 삭제 대상이 디렉터리이면 true
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun deleteRemote(path: String, isDirectory: Boolean, completionMessage: String, onRefresh: suspend () -> Unit) {
        val handle = progressHandle()
        appScope.launchOperation(handle, callback(completionMessage, onRefresh = onRefresh)) {
            val ok = if (isDirectory) ftpRepo.removeDirectoryRecursive(path) else ftpRepo.deleteFile(path)
            if (!ok) throw Exception("삭제 실패: ${path.substringAfterLast("/")}")
        }
    }

    /**
     * [parentPath] 아래에 [dirName] 디렉터리를 원격 서버에 생성한다.
     * @param parentPath 디렉터리를 생성할 원격 부모 경로
     * @param dirName 생성할 디렉터리 이름
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun makeDirectoryRemote(parentPath: String, dirName: String, completionMessage: String, onRefresh: suspend () -> Unit) {
        val handle = progressHandle()
        appScope.launchOperation(handle, callback(completionMessage, onRefresh = onRefresh)) {
            if (!ftpRepo.makeDirectory("$parentPath/$dirName")) throw Exception("디렉터리 생성 실패: $dirName")
        }
    }
}
