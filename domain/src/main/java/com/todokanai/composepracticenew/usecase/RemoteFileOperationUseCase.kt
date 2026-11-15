package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.NamingOperation
import com.todokanai.composepracticenew.model.wrappingFailureReason
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.repository.OperationNoticeReporter
import com.todokanai.composepracticenew.repository.RemoteFileMutationRepository
import com.todokanai.composepracticenew.repository.RemoteFileTransferRepository

/**
 * 원격 파일 작업(다운로드·업로드·이름변경·삭제·새폴더)을 조율하는 단일 진입점 UseCase.
 * 이름 변경·새 폴더처럼 사용자가 정한 이름을 쓰는 작업은 실행 전에 [fileNameUseCase] 규칙을 강제한다.
 * @param orchestrator 인스턴스 ID·진행 핸들·콜백·실행 스코프를 제공하는 공통 오케스트레이터
 * @param notices 실행 전 거부 메시지를 화면에 알리도록 맡기는 OperationNoticeReporter
 * @param transferRepo 원격↔로컬 파일 전송을 실행하는 RemoteFileTransferRepository
 * @param mutationRepo 원격 파일 이름변경·삭제·생성을 실행하는 RemoteFileMutationRepository
 * @param fileNameUseCase 원격 화면이 다루는 저장소의 제약을 포함한 파일 이름 규칙을 검증하는 UseCase
 */
class RemoteFileOperationUseCase(
    private val orchestrator: FileOperationOrchestrator,
    private val notices: OperationNoticeReporter,
    private val transferRepo: RemoteFileTransferRepository,
    private val mutationRepo: RemoteFileMutationRepository,
    private val fileNameUseCase: FileNameUseCase
) {
    /**
     * 원격 새 폴더·이름 변경에 쓸 이름을 검증한다.
     * @param name 사용자가 입력한 이름
     * @param location 이름이 놓일 경로 — 새 폴더면 만들 폴더, 이름 변경이면 그 항목 자신
     * @return 위반한 규칙의 집합. 비어 있으면 유효한 이름이다
     */
    fun validateEntryName(name: String, location: String): Set<FileNameError> = fileNameUseCase.validate(name, location)

    /**
     * 원격 항목 [items]를 [localDestPath]에 같은 이름으로 다운로드한다. 한 번의 확인으로 고른 항목들이므로 작업 하나로 실행한다
     * (취소·"나머지에도 적용"·진행률이 모두 이 묶음 전체에 걸리도록).
     * 같은 이름의 로컬 항목은 받는 중에 그 항목을 쓰려 할 때 사용자에게 묻는다.
     * @param items 다운로드할 원격 파일 또는 디렉터리 항목
     * @param localDestPath 로컬 저장 대상 디렉터리의 절대 경로
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun download(items: List<FileHolderItem>, localDestPath: String, onRefresh: suspend () -> Unit) {
        orchestrator.launchResolvingConflicts(FileOperationType.DOWNLOAD, onRefresh) { onProgress, resolveConflict ->
            transferRepo.download(items, localDestPath, resolveConflict, onProgress)
        }
    }

    /**
     * 로컬 항목 [items]를 [remoteDestPath]에 같은 이름으로 업로드한다. 한 번의 확인으로 고른 항목들이므로 작업 하나로 실행한다
     * (취소·진행률이 모두 이 묶음 전체에 걸리도록, #356).
     * @param items 업로드할 로컬 파일 또는 디렉터리 항목
     * @param remoteDestPath 업로드 대상 원격 디렉터리 경로
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun upload(items: List<FileHolderItem>, remoteDestPath: String, onRefresh: suspend () -> Unit) {
        orchestrator.launch(FileOperationType.UPLOAD, onRefresh) { onProgress ->
            transferRepo.upload(items, remoteDestPath, onProgress)
        }
    }

    /**
     * [path]의 원격 파일 이름을 [newName]으로 변경한다. 이름이 규칙을 위반하면 실행하지 않고 오류를 방출한다.
     * @param path 이름을 변경할 원격 파일의 경로
     * @param newName 변경할 새 파일 이름 (전체 경로가 아닌 이름만)
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun renameRemote(path: String, newName: String, onRefresh: suspend () -> Unit) {
        if (notices.rejectsInvalidName(validateEntryName(newName, path), newName, NamingOperation.RENAME)) return
        orchestrator.launch(onRefresh = onRefresh) { _ ->
            mutationRepo.rename(path, newName).wrappingFailureReason { FailureReason.RenameFailed(newName, it) }
        }
    }

    /**
     * [targets]의 원격 파일·디렉터리를 하나의 작업으로 삭제한다.
     * 일부 항목이 실패해도 나머지를 끝까지 삭제한 뒤 실패한 항목을 모아 알린다. 연결이 끊기면 즉시 중단한다.
     * 폴더는 하위 항목 수에 비례해 오래 걸릴 수 있으므로 진행 표시·보호·취소가 있는 삭제 작업으로 실행한다(#334). 취소하면 이미 지운 항목은 그대로 둔다.
     * @param targets 삭제할 원격 파일·디렉터리 항목
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun deleteRemote(targets: List<FileHolderItem>, onRefresh: suspend () -> Unit) {
        orchestrator.launch(FileOperationType.DELETE, onRefresh) { onProgress -> mutationRepo.delete(targets, onProgress) }
    }

    /**
     * [parentPath] 아래에 [dirName] 디렉터리를 원격 서버에 생성한다. 이름이 규칙을 위반하면 실행하지 않고 오류를 방출한다.
     * @param parentPath 디렉터리를 생성할 원격 부모 경로
     * @param dirName 생성할 디렉터리 이름
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun makeDirectoryRemote(parentPath: String, dirName: String, onRefresh: suspend () -> Unit) {
        if (notices.rejectsInvalidName(validateEntryName(dirName, parentPath), dirName, NamingOperation.MAKE_FOLDER)) return
        orchestrator.launch(onRefresh = onRefresh) { _ ->
            mutationRepo.makeDirectory(parentPath, dirName).wrappingFailureReason { FailureReason.MakeFolderFailed(dirName, it) }
        }
    }
}
