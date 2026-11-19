package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.InstantOperation
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.NamingOperation
import com.todokanai.composepracticenew.model.OperationKind
import com.todokanai.composepracticenew.model.RemoteSession
import com.todokanai.composepracticenew.model.RenameOverwrite
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.repository.OperationNoticeReporter
import com.todokanai.composepracticenew.repository.RemoteFileMutationRepository
import com.todokanai.composepracticenew.repository.RemoteFileTransferRepository
import com.todokanai.composepracticenew.repository.RemoteSessionProvider

/**
 * 원격 파일 작업(다운로드·업로드·이름변경·삭제·새폴더)을 조율하는 단일 진입점 UseCase.
 * 이름 변경·새 폴더처럼 사용자가 정한 이름을 쓰는 작업은 실행 전에 [fileNameUseCase] 규칙을 강제한다.
 * 작업은 실행을 요청할 때(보호를 기다리기 전에) 지금 연결된 세션에 묶는다 — 그 뒤 다른 서버에 접속해도 작업은 고른 세션에서만 실행된다(#436).
 * 요청할 때 연결돼 있지 않으면 저장소를 부르지 않고 [FailureReason.NotConnected] 실패로 끝낸다.
 * @param orchestrator 인스턴스 ID·진행 핸들·콜백·실행 스코프를 제공하는 공통 오케스트레이터
 * @param notices 실행 전 거부 메시지를 화면에 알리도록 맡기는 OperationNoticeReporter
 * @param transferRepo 원격↔로컬 파일 전송을 실행하는 RemoteFileTransferRepository
 * @param mutationRepo 원격 파일 이름변경·삭제·생성을 실행하는 RemoteFileMutationRepository
 * @param sessions 작업을 묶을 지금의 원격 세션을 읽는 RemoteSessionProvider
 * @param fileNameUseCase 원격 화면이 다루는 저장소의 제약을 포함한 파일 이름 규칙을 검증하는 UseCase
 */
class RemoteFileOperationUseCase(
    private val orchestrator: FileOperationOrchestrator,
    private val notices: OperationNoticeReporter,
    private val transferRepo: RemoteFileTransferRepository,
    private val mutationRepo: RemoteFileMutationRepository,
    private val sessions: RemoteSessionProvider,
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
        val session = sessions.current()
        orchestrator.launchAsking(FileOperationType.DOWNLOAD, onRefresh) { onProgress, prompts ->
            onSession(session) { transferRepo.download(it, items, localDestPath, prompts, onProgress) }
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
        val session = sessions.current()
        orchestrator.launch(FileOperationType.UPLOAD, onRefresh) { onProgress ->
            onSession(session) { transferRepo.upload(it, items, remoteDestPath, onProgress) }
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
        val session = sessions.current()
        orchestrator.launchInstant(OperationKind.Instant(InstantOperation.RENAME, newName), onRefresh) {
            onSession(session) { mutationRepo.rename(it, path, newName) }
        }
    }

    /**
     * 같은 이름의 같은 종류 항목 위로 [renameRemote]를 실행할 때 일어나는 일을 알려 준다. 확인 뒤에도 이름 변경 명령만 보내므로, 덮어쓸지와 폴더를 어떻게 다룰지는 서버 동작에 따른다.
     * @param isDirectory 이름을 바꿀 항목이 폴더인지 — 원격은 종류와 무관하게 서버가 정한다
     * @return 덮어쓰기의 결과
     */
    fun renameOverwriteOf(isDirectory: Boolean): RenameOverwrite = RenameOverwrite.STORAGE_DEFINED

    /**
     * [targets]의 원격 파일·디렉터리를 하나의 작업으로 삭제한다.
     * 일부 항목이 실패해도 나머지를 끝까지 삭제한 뒤 실패한 항목을 모아 알린다. 연결이 끊기면 즉시 중단한다.
     * 폴더는 하위 항목 수에 비례해 오래 걸릴 수 있으므로 진행 표시·보호·취소가 있는 삭제 작업으로 실행한다(#334). 취소하면 이미 지운 항목은 그대로 둔다.
     * @param targets 삭제할 원격 파일·디렉터리 항목
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun deleteRemote(targets: List<FileHolderItem>, onRefresh: suspend () -> Unit) {
        val session = sessions.current()
        orchestrator.launch(FileOperationType.DELETE, onRefresh) { onProgress -> onSession(session) { mutationRepo.delete(it, targets, onProgress) } }
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
        val session = sessions.current()
        orchestrator.launchInstant(OperationKind.Instant(InstantOperation.MAKE_FOLDER, dirName), onRefresh) {
            onSession(session) { mutationRepo.makeDirectory(it, parentPath, dirName) }
        }
    }

    /**
     * 실행을 요청할 때 묶은 [session]으로 [run]을 실행한다.
     * @param session 요청할 때 읽은 세션. 연결돼 있지 않았으면 null
     * @param run 묶은 세션으로 저장소를 부르는 동작
     * @return run의 결과. [session]이 null이면 연결 없음 실패
     */
    private inline fun onSession(session: RemoteSession?, run: (RemoteSession) -> IoResult<Unit>): IoResult<Unit> =
        if (session == null) IoResult.ConnectionLost(FailureReason.NotConnected) else run(session)
}
