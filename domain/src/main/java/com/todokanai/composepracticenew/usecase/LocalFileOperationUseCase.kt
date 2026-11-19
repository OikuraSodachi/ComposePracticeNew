package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.ArchiveFormat
import com.todokanai.composepracticenew.model.ExtractLayout
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.InstantOperation
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.NamingOperation
import com.todokanai.composepracticenew.model.NoticeMessage
import com.todokanai.composepracticenew.model.OperationKind
import com.todokanai.composepracticenew.model.RelocateOperation
import com.todokanai.composepracticenew.model.RenameOverwrite
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.repository.FileActionRepository
import com.todokanai.composepracticenew.repository.OperationNoticeReporter
import com.todokanai.composepracticenew.repository.StoragePathRules

/**
 * 로컬 파일 작업(복사·이동·삭제·압축·압축해제·이름변경·새폴더)을 조율하는 UseCase.
 * 새 폴더·이름 변경·압축처럼 사용자가 정한 이름을 쓰는 작업은 실행 전에 [fileNameUseCase] 규칙을 강제한다.
 * @param orchestrator 인스턴스 ID·진행 핸들·콜백·실행 스코프를 제공하는 공통 오케스트레이터
 * @param notices 실행 전 거부 메시지를 화면에 알리도록 맡기는 OperationNoticeReporter
 * @param fileActionRepo 로컬 파일 변경 작업을 실행하는 FileActionRepository
 * @param fileNameUseCase 로컬 저장소의 제약을 포함한 파일 이름 규칙을 검증하는 UseCase
 * @param paths 출발지와 목적지의 위치 관계를 판별할 로컬 저장소의 경로 규칙(StoragePathRules 구현체)
 */
class LocalFileOperationUseCase(
    private val orchestrator: FileOperationOrchestrator,
    private val notices: OperationNoticeReporter,
    private val fileActionRepo: FileActionRepository,
    private val fileNameUseCase: FileNameUseCase,
    private val paths: StoragePathRules
) {
    /**
     * 새 폴더·이름 변경에 쓸 이름을 검증한다.
     * @param name 사용자가 입력한 이름
     * @param location 이름이 놓일 경로 — 새 폴더면 만들 폴더, 이름 변경이면 그 항목 자신
     * @return 위반한 규칙의 집합. 비어 있으면 유효한 이름이다
     */
    fun validateEntryName(name: String, location: String): Set<FileNameError> = fileNameUseCase.validate(name, location)

    /**
     * 압축 파일 이름을 검증한다. 저장소의 제약(길이 한도 등)은 [format]의 확장자를 포함해 판정한다.
     * @param name 사용자가 입력한 압축 파일 이름(확장자 제외)
     * @param location 압축 파일을 만들 폴더의 경로
     * @param format 만들 아카이브 형식
     * @return 위반한 규칙의 집합. 비어 있으면 유효한 이름이다
     */
    fun validateArchiveName(name: String, location: String, format: ArchiveFormat): Set<FileNameError> =
        fileNameUseCase.validate(name, location, format.extension)

    /**
     * 목적지가 원본 중 하나와 맞지 않는 위치면 거부 메시지를 보관하고 작업을 중단해야 함을 알린다.
     * 맞지 않는 위치는 원본의 부모(제자리)와 원본 자신이거나 그 안쪽(자기 안으로) 두 가지이며, 사유마다 다른 메시지로 알린다.
     * @param files 복사·이동할 원본 경로 집합
     * @param destPath 목적지 디렉터리 경로
     * @param operation 실행하지 않을 작업. 거부 메시지의 머리말을 정한다
     * @return 맞지 않는 위치라 작업을 실행하지 않아야 하면 true
     */
    private fun rejectsDestination(files: Set<String>, destPath: String, operation: RelocateOperation): Boolean {
        files.firstOrNull { isIntoItself(it, destPath) }?.let { srcPath ->
            notices.report(NoticeMessage.DestinationInsideSource(operation, paths.displayNameOf(srcPath)))
            return true
        }
        if (files.any { isInPlace(it, destPath) }) {
            notices.report(NoticeMessage.SameDestination(operation))
            return true
        }
        return false
    }

    /**
     * [destPath]가 [srcPath]의 부모(제자리 복사·이동)인지 판별한다.
     * @param srcPath 원본 경로
     * @param destPath 목적지 디렉터리 경로
     * @return [destPath]가 [srcPath]의 부모면 true
     */
    private fun isInPlace(srcPath: String, destPath: String): Boolean = paths.getParentPath(srcPath) == destPath

    /**
     * [destPath]가 [srcPath] 자신이거나 그 안쪽인지 [destPath]에서 [StoragePathRules.getParentPath]를 따라 올라가며 판별한다.
     * 자기 자신에서 시작해 올라가므로 경계(자기 자신)가 따로 빠지지 않는다. 부모는 항상 위의 경로이므로 루트(null)에서 멈춘다.
     * @param srcPath 원본 경로
     * @param destPath 목적지 디렉터리 경로
     * @return [destPath] 자신이나 그 상위 경로 중 [srcPath]가 있으면 true
     */
    private fun isIntoItself(srcPath: String, destPath: String): Boolean =
        generateSequence(destPath) { paths.getParentPath(it) }.any { it == srcPath }

    /**
     * [files]를 [destPath]에 복사한다. 목적지가 원본의 부모이거나 원본 자신·그 안쪽이면 실행하지 않고 거부를 알린다.
     * 같은 이름의 항목은 복사하는 중에 그 항목을 쓰려 할 때 사용자에게 묻는다.
     * @param files 복사할 파일의 절대 경로 집합
     * @param destPath 복사 대상 디렉터리의 절대 경로
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun copy(files: Set<String>, destPath: String, onRefresh: suspend () -> Unit) {
        if (rejectsDestination(files, destPath, RelocateOperation.COPY)) return
        orchestrator.launchAsking(FileOperationType.COPY, onRefresh) { onProgress, prompts ->
            fileActionRepo.copyAction(files, destPath, prompts, onProgress)
        }
    }

    /**
     * [files]를 삭제한다. 일부 항목이 실패해도 나머지를 끝까지 삭제한 뒤 실패한 항목을 모아 알린다.
     * @param files 삭제할 파일·디렉터리 항목
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun delete(files: List<FileHolderItem>, onRefresh: suspend () -> Unit) {
        orchestrator.launch(FileOperationType.DELETE, onRefresh) { onProgress ->
            fileActionRepo.deleteFiles(files.mapTo(LinkedHashSet()) { it.path }, onProgress)
        }
    }

    /**
     * [files]를 [destPath]로 이동한다. 목적지가 원본의 부모이거나 원본 자신·그 안쪽이면 실행하지 않고 거부를 알린다.
     * 같은 이름의 항목은 이동하는 중에 그 항목을 쓰려 할 때 사용자에게 묻는다. 건너뛴 항목은 원래 자리에 남는다.
     * @param files 이동할 파일의 절대 경로 집합
     * @param destPath 이동 대상 디렉터리의 절대 경로
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun move(files: Set<String>, destPath: String, onRefresh: suspend () -> Unit) {
        if (rejectsDestination(files, destPath, RelocateOperation.MOVE)) return
        orchestrator.launchAsking(FileOperationType.MOVE, onRefresh) { onProgress, prompts ->
            fileActionRepo.moveFile(files, destPath, prompts, onProgress)
        }
    }

    /**
     * [files]를 [parentPath] 아래 [name]에 [format]의 확장자를 붙인 아카이브로 압축한다. 저장소가 [format]을 지원하지 않거나 이름이 규칙을 위반하면 실행하지 않고 오류를 방출한다.
     * 같은 이름의 파일이 이미 있으면 저장소가 `이름 (n)`에 [format]의 확장자를 붙인 형태의 새 이름으로 만든다(기존 파일은 덮어쓰지 않는다).
     * @param files 압축할 파일의 절대 경로 집합
     * @param parentPath 아카이브를 생성할 디렉터리의 절대 경로
     * @param name 생성할 아카이브 이름(확장자 제외)
     * @param format 만들 아카이브 형식
     * @param password 아카이브를 암호화할 비밀번호(AES-256). null이면 암호화하지 않는다. 빈 문자열은 받지 않는다
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun compress(files: Set<String>, parentPath: String, name: String, format: ArchiveFormat, password: String?, onRefresh: suspend () -> Unit) {
        // 빈 비밀번호는 입력 창이 막는다 — 여기 닿았다면 구현 오류다(암호화를 원한 압축을 암호 없이 만들지 않도록 실패시킨다)
        require(password == null || password.isNotEmpty()) { "빈 비밀번호로 압축할 수 없습니다" }
        if (rejectsUnsupportedFormat(format)) return
        if (notices.rejectsInvalidName(validateArchiveName(name, parentPath, format), "$name${format.extension}", NamingOperation.COMPRESS)) return
        orchestrator.launch(FileOperationType.COMPRESS, onRefresh) { onProgress ->
            fileActionRepo.compressAction(files, parentPath, name, format, password, onProgress)
        }
    }

    /**
     * [zipFiles]를 [destPath]에 압축 해제한다. 같은 이름의 파일은 그 항목을 풀려 할 때 사용자에게 묻고, 폴더는 묻지 않고 합친다.
     * 암호화된 항목을 만나면 압축 파일마다 비밀번호를 묻고, 틀리면 다시 묻는다.
     * @param zipFiles 압축 해제할 압축 파일의 절대 경로 집합. 분할 압축의 다른 볼륨은 함께 받은 시작 볼륨으로 한 번 푼다
     * @param destPath 압축 해제 대상 디렉터리의 절대 경로
     * @param layout 압축 해제한 항목을 [destPath]의 어디에 둘지 정하는 배치 방식
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun unzip(zipFiles: Set<String>, destPath: String, layout: ExtractLayout, onRefresh: suspend () -> Unit) {
        orchestrator.launchAsking(FileOperationType.UNZIP, onRefresh) { onProgress, prompts ->
            fileActionRepo.unzipAction(zipFiles, destPath, layout, resolveConflict = prompts, askPassword = prompts, onProgress = onProgress)
        }
    }

    /**
     * [items]를 [unzip]으로 압축 해제 한 번에 풀 수 있는지 판별한다. 형식과 분할 볼륨의 묶음은 압축 해제 구현이 정한 것으로만 정한다.
     * @param items 확인할 항목
     * @return 폴더가 없고, 모두 합쳐 아카이브 하나(단일 아카이브, 또는 시작 볼륨과 같은 분할 압축의 다른 볼륨)이면 true
     */
    fun canUnzip(items: List<FileHolderItem>): Boolean =
        items.none { it.isDirectory } && fileActionRepo.extractionTargetsOf(items.mapTo(HashSet()) { it.path })?.size == 1

    /**
     * [path]의 파일 이름을 [newName]으로 변경한다. 이름이 규칙을 위반하면 실행하지 않고 오류를 방출한다.
     * 같은 이름의 항목이 이미 있으면 [overwrite]가 true일 때만 덮어쓴다(파일은 교체, 폴더는 합침). false면 실패를 알린다.
     * @param path 이름을 변경할 파일의 절대 경로
     * @param newName 변경할 새 파일 이름 (전체 경로가 아닌 이름만)
     * @param overwrite 사용자가 덮어쓰기를 확인했으면 true
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun rename(path: String, newName: String, overwrite: Boolean, onRefresh: suspend () -> Unit) {
        if (notices.rejectsInvalidName(validateEntryName(newName, path), newName, NamingOperation.RENAME)) return
        orchestrator.launchInstant(OperationKind.Instant(InstantOperation.RENAME, newName), onRefresh) {
            fileActionRepo.renameFile(path, newName, overwrite)
        }
    }

    /**
     * 같은 이름의 같은 종류 항목 위로 [rename]을 덮어쓰기로 실행할 때 일어나는 일을 알려 준다. 파일은 기존 파일을 덮어쓰고, 폴더는 두 폴더를 합친다.
     * @param isDirectory 이름을 바꿀 항목이 폴더인지
     * @return 덮어쓰기의 결과
     */
    fun renameOverwriteOf(isDirectory: Boolean): RenameOverwrite =
        if (isDirectory) RenameOverwrite.MERGE_FOLDER else RenameOverwrite.REPLACE_FILE

    /**
     * [parentPath] 아래에 [name] 이름의 디렉터리를 생성한다. 이름이 규칙을 위반하면 실행하지 않고 오류를 방출한다.
     * @param parentPath 디렉터리를 생성할 부모 디렉터리의 절대 경로
     * @param name 생성할 디렉터리 이름
     * @param onRefresh 작업 완료 후 디렉터리 목록을 갱신하는 suspend 콜백
     * @return Unit
     */
    fun makeDirectory(parentPath: String, name: String, onRefresh: suspend () -> Unit) {
        if (notices.rejectsInvalidName(validateEntryName(name, parentPath), name, NamingOperation.MAKE_FOLDER)) return
        orchestrator.launchInstant(OperationKind.Instant(InstantOperation.MAKE_FOLDER, name), onRefresh) {
            fileActionRepo.makeDirectory(parentPath, name)
        }
    }

    /**
     * 저장소가 [format]을 만들 수 없으면 거부 메시지를 보관하고 작업을 중단해야 함을 알린다 — 진행 표시·보호를 얻기 전에 거부하도록 실행 전에 판별한다.
     * @param format 만들 아카이브 형식
     * @return 지원하지 않는 형식이라 작업을 실행하지 않아야 하면 true
     */
    private fun rejectsUnsupportedFormat(format: ArchiveFormat): Boolean {
        if (format in fileActionRepo.supportedArchiveFormats()) return false
        notices.report(NoticeMessage.UnsupportedArchiveFormat(format.extension))
        return true
    }
}
