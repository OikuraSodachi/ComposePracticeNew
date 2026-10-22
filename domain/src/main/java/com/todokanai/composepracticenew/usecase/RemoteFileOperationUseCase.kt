package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.operation.forEachCollectingFailures
import com.todokanai.composepracticenew.repository.RemoteFileMutationRepository
import com.todokanai.composepracticenew.repository.RemoteFileTransferRepository
import kotlinx.coroutines.CancellationException

/**
 * 원격 파일 작업(다운로드·업로드·이름변경·삭제·새폴더)을 조율하는 단일 진입점 UseCase.
 * 이름 변경·새 폴더처럼 사용자가 정한 이름을 쓰는 작업은 실행 전에 [fileNameUseCase] 규칙을 강제한다.
 * @param orchestrator 인스턴스 ID·진행 핸들·콜백·실행 스코프를 제공하는 공통 오케스트레이터
 * @param transferRepo 원격↔로컬 파일 전송을 실행하는 RemoteFileTransferRepository
 * @param mutationRepo 원격 파일 이름변경·삭제·생성을 실행하는 RemoteFileMutationRepository
 * @param fileNameUseCase 파일 이름 규칙을 검증하는 UseCase
 */
class RemoteFileOperationUseCase(
    private val orchestrator: FileOperationOrchestrator,
    private val transferRepo: RemoteFileTransferRepository,
    private val mutationRepo: RemoteFileMutationRepository,
    private val fileNameUseCase: FileNameUseCase
) {
    /**
     * 원격 새 폴더·이름 변경에 쓸 이름을 검증한다.
     * @param name 사용자가 입력한 이름
     * @return 위반한 규칙의 집합. 비어 있으면 유효한 이름이다
     */
    fun validateEntryName(name: String): Set<FileNameError> = fileNameUseCase.validate(name)

    /**
     * 대상 폴더의 목록을 확인하지 못해 이름 충돌을 판정할 수 없어 작업을 실행하지 않았음을 오류로 알린다.
     * 확인되지 않은 목록으로 판정하면 같은 이름의 항목을 확인 없이 덮어쓸 수 있으므로, 호출부는 이 경우 작업을 실행하지 않는다.
     * @param message 표시할 오류 메시지
     * @return Unit
     */
    fun reportUnverifiedDestination(message: String) = orchestrator.emitError(message)

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
            transferRepo.download(remotePath, localFilePath, isDirectory, onProgress)
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
            transferRepo.upload(localPath, remoteFilePath, onProgress)
        }
    }

    /**
     * [path]의 원격 파일 이름을 [newName]으로 변경한다. 이름이 규칙을 위반하면 실행하지 않고 오류를 방출한다.
     * @param path 이름을 변경할 원격 파일의 경로
     * @param newName 변경할 새 파일 이름 (전체 경로가 아닌 이름만)
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param errorMessage 이름 변경 실패 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun renameRemote(path: String, newName: String, completionMessage: String, errorMessage: String, onRefresh: suspend () -> Unit) {
        if (rejectsInvalidName(validateEntryName(newName), newName, "이름 변경 실패")) return
        orchestrator.launch(completionMessage, onRefresh = onRefresh) { _ ->
            val toPath = "${path.substringBeforeLast("/")}/$newName"
            rethrowAs(errorMessage) { mutationRepo.rename(path, toPath) }
        }
    }

    /**
     * [targets]의 원격 파일·디렉터리를 하나의 작업으로 삭제한다.
     * 일부 항목이 실패해도 나머지를 끝까지 삭제한 뒤 실패한 항목을 모아 알린다. 연결이 끊기면 즉시 중단한다.
     * @param targets 삭제할 원격 경로 → 디렉터리 여부
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun deleteRemote(targets: Map<String, Boolean>, completionMessage: String, onRefresh: suspend () -> Unit) {
        orchestrator.launch(completionMessage, onRefresh = onRefresh) { _ ->
            forEachCollectingFailures(targets.entries, "삭제 실패", nameOf = { it.key.substringAfterLast("/") }) { (path, isDirectory) ->
                if (isDirectory) mutationRepo.removeDirectoryRecursive(path) else mutationRepo.deleteFile(path)
            }
        }
    }

    /**
     * [parentPath] 아래에 [dirName] 디렉터리를 원격 서버에 생성한다. 이름이 규칙을 위반하면 실행하지 않고 오류를 방출한다.
     * @param parentPath 디렉터리를 생성할 원격 부모 경로
     * @param dirName 생성할 디렉터리 이름
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param errorMessage 디렉터리 생성 실패 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun makeDirectoryRemote(parentPath: String, dirName: String, completionMessage: String, errorMessage: String, onRefresh: suspend () -> Unit) {
        if (rejectsInvalidName(validateEntryName(dirName), dirName, "디렉터리 생성 실패")) return
        orchestrator.launch(completionMessage, onRefresh = onRefresh) { _ ->
            rethrowAs(errorMessage) { mutationRepo.makeDirectory("$parentPath/$dirName") }
        }
    }

    /**
     * [block]이 실패하면 [message]를 메시지로, 원래 예외를 원인으로 하는 예외로 바꿔 던진다. 취소는 그대로 전파한다.
     * @param message 실패 시 표시할 메시지
     * @param block 실행할 원격 작업
     * @return Unit
     */
    private suspend fun rethrowAs(message: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw Exception(message, e)
        }
    }

    /**
     * 검증 결과에 위반이 있으면 오류를 방출하고 작업을 중단해야 함을 알린다.
     * @param errors [FileNameUseCase.validate]가 반환한 위반 규칙 집합
     * @param displayName 오류 메시지에 표시할 이름
     * @param failurePrefix 오류 메시지 머리말(예: "이름 변경 실패")
     * @return 위반이 있어 작업을 실행하지 않아야 하면 true
     */
    private fun rejectsInvalidName(errors: Set<FileNameError>, displayName: String, failurePrefix: String): Boolean {
        if (errors.isEmpty()) return false
        orchestrator.emitError("$failurePrefix: 사용할 수 없는 이름입니다 ($displayName)")
        return true
    }
}
