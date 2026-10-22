package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.operation.forEachCollectingFailures
import com.todokanai.composepracticenew.repository.FileActionRepository

/**
 * 로컬 파일 작업(복사·이동·삭제·압축·압축해제·이름변경·새폴더)을 조율하는 UseCase.
 * 새 폴더·이름 변경·압축처럼 사용자가 정한 이름을 쓰는 작업은 실행 전에 [fileNameUseCase] 규칙을 강제한다.
 * @param orchestrator 인스턴스 ID·진행 핸들·콜백·실행 스코프를 제공하는 공통 오케스트레이터
 * @param fileActionRepo 로컬 파일 변경 작업을 실행하는 FileActionRepository
 * @param fileNameUseCase 파일 이름 규칙을 검증하는 UseCase
 */
class LocalFileOperationUseCase(
    private val orchestrator: FileOperationOrchestrator,
    private val fileActionRepo: FileActionRepository,
    private val fileNameUseCase: FileNameUseCase
) {
    /**
     * 새 폴더·이름 변경에 쓸 이름을 검증한다.
     * @param name 사용자가 입력한 이름
     * @return 위반한 규칙의 집합. 비어 있으면 유효한 이름이다
     */
    fun validateEntryName(name: String): Set<FileNameError> = fileNameUseCase.validate(name)

    /**
     * 압축 파일 이름을 검증한다. 길이 한도는 [ZIP_EXTENSION]을 포함해 판정한다.
     * @param name 사용자가 입력한 압축 파일 이름(확장자 제외)
     * @return 위반한 규칙의 집합. 비어 있으면 유효한 이름이다
     */
    fun validateZipName(name: String): Set<FileNameError> = fileNameUseCase.validate(name, ZIP_EXTENSION)

    /**
     * 대상 폴더의 목록을 확인하지 못해 이름 충돌을 판정할 수 없어 작업을 실행하지 않았음을 오류로 알린다.
     * 확인되지 않은 목록으로 판정하면 같은 이름의 항목을 확인 없이 덮어쓸 수 있으므로, 호출부는 이 경우 작업을 실행하지 않는다.
     * @param message 표시할 오류 메시지
     * @return Unit
     */
    fun reportUnverifiedDestination(message: String) = orchestrator.emitError(message)

    private fun isSamePath(files: Set<String>, destPath: String): Boolean =
        files.any { srcPath ->
            val srcName = srcPath.substringAfterLast('/')
            "$destPath/$srcName" == srcPath ||
            destPath.startsWith("$srcPath/")
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
            orchestrator.emitError("복사 실패: 출발지와 목적지가 동일합니다.")
            return
        }
        orchestrator.launch(completionMessage, FileOperationType.COPY, onRefresh) { onProgress ->
            fileActionRepo.copyAction(files, destPath, onProgress)
        }
    }

    /**
     * [files]를 삭제한다. 일부 항목이 실패해도 나머지를 끝까지 삭제한 뒤 실패한 항목을 모아 알린다.
     * @param files 삭제할 파일의 절대 경로 집합
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun delete(files: Set<String>, completionMessage: String, onRefresh: suspend () -> Unit) {
        orchestrator.launch(completionMessage, FileOperationType.DELETE, onRefresh) { onProgress ->
            forEachCollectingFailures(files, "삭제 실패", nameOf = { it.substringAfterLast('/') }) { path ->
                fileActionRepo.deleteFile(path, onProgress)
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
            orchestrator.emitError("이동 실패: 출발지와 목적지가 동일합니다.")
            return
        }
        orchestrator.launch(completionMessage, FileOperationType.MOVE, onRefresh) { onProgress ->
            fileActionRepo.moveFile(files, destPath, onProgress)
        }
    }

    /**
     * [files]를 [parentPath] 아래 [name][ZIP_EXTENSION] 파일로 압축한다. 이름이 규칙을 위반하면 실행하지 않고 오류를 방출한다.
     * 같은 이름의 파일이 이미 있으면 저장소가 `이름 (n).zip` 형태의 새 이름으로 만든다(기존 파일은 덮어쓰지 않는다).
     * @param files 압축할 파일의 절대 경로 집합
     * @param parentPath zip 파일을 생성할 디렉터리의 절대 경로
     * @param name 생성할 zip 파일 이름(확장자 제외)
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun zip(files: Set<String>, parentPath: String, name: String, completionMessage: String, onRefresh: suspend () -> Unit) {
        if (rejectsInvalidName(validateZipName(name), "$name$ZIP_EXTENSION", "압축 실패")) return
        val zipFilePath = "$parentPath/$name$ZIP_EXTENSION"
        orchestrator.launch(completionMessage, FileOperationType.ZIP, onRefresh) { onProgress ->
            fileActionRepo.zipAction(files, zipFilePath, onProgress)
        }
    }

    /**
     * [zipFiles]를 [destPath]에 압축 해제한다.
     * @param zipFiles 압축 해제할 zip/rar 파일의 절대 경로 집합
     * @param destPath 압축 해제 대상 디렉터리의 절대 경로
     * @param unzipHere true이면 [destPath]에 직접 해제, false이면 파일명 하위 폴더 생성 후 해제
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun unzip(zipFiles: Set<String>, destPath: String, unzipHere: Boolean, completionMessage: String, onRefresh: suspend () -> Unit) {
        orchestrator.launch(completionMessage, FileOperationType.UNZIP, onRefresh) { onProgress ->
            fileActionRepo.unzipAction(zipFiles, destPath, unzipHere, onProgress)
        }
    }

    /**
     * [path]의 파일 이름을 [newName]으로 변경한다. 이름이 규칙을 위반하면 실행하지 않고 오류를 방출한다.
     * 같은 이름의 항목이 이미 있으면 [overwrite]가 true일 때만 덮어쓴다(파일은 교체, 폴더는 합침). false면 실패를 알린다.
     * @param path 이름을 변경할 파일의 절대 경로
     * @param newName 변경할 새 파일 이름 (전체 경로가 아닌 이름만)
     * @param overwrite 사용자가 덮어쓰기를 확인했으면 true
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun rename(path: String, newName: String, overwrite: Boolean, completionMessage: String, onRefresh: suspend () -> Unit) {
        if (rejectsInvalidName(validateEntryName(newName), newName, "이름 변경 실패")) return
        orchestrator.launch(completionMessage, onRefresh = onRefresh) { _ ->
            fileActionRepo.renameFile(path, newName, overwrite)
        }
    }

    /**
     * [parentPath] 아래에 [name] 이름의 디렉터리를 생성한다. 이름이 규칙을 위반하면 실행하지 않고 오류를 방출한다.
     * @param parentPath 디렉터리를 생성할 부모 디렉터리의 절대 경로
     * @param name 생성할 디렉터리 이름
     * @param completionMessage 작업 완료 시 표시할 메시지
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun makeDirectory(parentPath: String, name: String, completionMessage: String, onRefresh: suspend () -> Unit) {
        if (rejectsInvalidName(validateEntryName(name), name, "폴더 생성 실패")) return
        orchestrator.launch(completionMessage, onRefresh = onRefresh) { _ ->
            fileActionRepo.makeDirectory(parentPath, name)
        }
    }

    /**
     * 검증 결과에 위반이 있으면 오류를 방출하고 작업을 중단해야 함을 알린다. 오류는 copy·move의 동일 경로 거부와 같은 경로로 표시된다.
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

    companion object {
        /** 압축 파일 이름 뒤에 붙는 확장자. */
        const val ZIP_EXTENSION = ".zip"
    }
}
