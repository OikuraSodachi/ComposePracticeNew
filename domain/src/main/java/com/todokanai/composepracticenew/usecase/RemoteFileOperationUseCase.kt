package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.repository.RemoteFileMutationRepository
import com.todokanai.composepracticenew.repository.RemoteFileTransferRepository

/**
 * 원격 파일 작업(다운로드·업로드·이름변경·삭제·새폴더)을 조율하는 단일 진입점 UseCase.
 * @param orchestrator 인스턴스 ID·진행 핸들·콜백·실행 스코프를 제공하는 공통 오케스트레이터
 * @param transferRepo 원격↔로컬 파일 전송을 실행하는 RemoteFileTransferRepository
 * @param mutationRepo 원격 파일 이름변경·삭제·생성을 실행하는 RemoteFileMutationRepository
 */
class RemoteFileOperationUseCase(
    private val orchestrator: FileOperationOrchestrator,
    private val transferRepo: RemoteFileTransferRepository,
    private val mutationRepo: RemoteFileMutationRepository
) {
    /**
     * [remotePath]를 [localDestPath]에 다운로드한다.
     * @param remotePath 다운로드할 원격 파일 또는 디렉터리의 경로
     * @param localDestPath 로컬 저장 대상 디렉터리의 절대 경로
     * @param isDirectory 원격 경로가 디렉터리이면 true
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun download(
        remotePath: String,
        localDestPath: String,
        isDirectory: Boolean,
        completionMessage: String,
        onRefresh: suspend () -> Unit
    ) {
        val localFilePath = "$localDestPath/${remotePath.substringAfterLast("/")}"
        orchestrator.launch(completionMessage, FileOperationType.DOWNLOAD, onRefresh) { onProgress ->
            transferRepo.download(remotePath, localFilePath, isDirectory, onProgress).collect {}
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
        val remoteFilePath = "$remoteDestPath/${localPath.substringAfterLast("/")}"
        orchestrator.launch(completionMessage, FileOperationType.UPLOAD, onRefresh) { onProgress ->
            transferRepo.upload(localPath, remoteFilePath, onProgress).collect {}
        }
    }

    /**
     * [path]의 원격 파일 이름을 [newName]으로 변경한다.
     * @param path 이름을 변경할 원격 파일의 경로
     * @param newName 변경할 새 파일 이름 (전체 경로가 아닌 이름만)
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param errorMessage 이름 변경 실패 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun renameRemote(path: String, newName: String, completionMessage: String, errorMessage: String, onRefresh: suspend () -> Unit) {
        orchestrator.launch(completionMessage, onRefresh = onRefresh) { _ ->
            val toPath = "${path.substringBeforeLast("/")}/$newName"
            if (!mutationRepo.rename(path, toPath)) throw Exception(errorMessage)
        }
    }

    /**
     * [path]의 원격 파일 또는 디렉터리를 삭제한다.
     * @param path 삭제할 원격 파일 또는 디렉터리의 경로
     * @param isDirectory 삭제 대상이 디렉터리이면 true
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param errorMessage 삭제 실패 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun deleteRemote(path: String, isDirectory: Boolean, completionMessage: String, errorMessage: String, onRefresh: suspend () -> Unit) {
        orchestrator.launch(completionMessage, onRefresh = onRefresh) { _ ->
            val ok = if (isDirectory) mutationRepo.removeDirectoryRecursive(path) else mutationRepo.deleteFile(path)
            if (!ok) throw Exception(errorMessage)
        }
    }

    /**
     * [parentPath] 아래에 [dirName] 디렉터리를 원격 서버에 생성한다.
     * @param parentPath 디렉터리를 생성할 원격 부모 경로
     * @param dirName 생성할 디렉터리 이름
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param errorMessage 디렉터리 생성 실패 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun makeDirectoryRemote(parentPath: String, dirName: String, completionMessage: String, errorMessage: String, onRefresh: suspend () -> Unit) {
        orchestrator.launch(completionMessage, onRefresh = onRefresh) { _ ->
            if (!mutationRepo.makeDirectory("$parentPath/$dirName")) throw Exception(errorMessage)
        }
    }
}
