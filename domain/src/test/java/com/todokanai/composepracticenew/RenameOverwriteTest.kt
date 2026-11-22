package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.ProgressPhase
import com.todokanai.composepracticenew.model.ArchiveFormat
import com.todokanai.composepracticenew.model.ExtractLayout
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.OperationKind
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.QuestionKind
import com.todokanai.composepracticenew.model.RemoteSession
import com.todokanai.composepracticenew.model.RenameOverwrite
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.repository.ArchivePasswordProvider
import com.todokanai.composepracticenew.repository.FileActionRepository
import com.todokanai.composepracticenew.repository.ProgressReporter
import com.todokanai.composepracticenew.repository.RemoteFileMutationRepository
import com.todokanai.composepracticenew.repository.RemoteFileTransferRepository
import com.todokanai.composepracticenew.repository.WriteConflictResolver
import com.todokanai.composepracticenew.usecase.FileNameUseCase
import com.todokanai.composepracticenew.usecase.LocalFileOperationUseCase
import com.todokanai.composepracticenew.usecase.RemoteFileOperationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 같은 이름의 같은 종류 항목 위로 이름을 바꿀 때 일어나는 일을 각 저장소의 UseCase가 알맞게 알려 주는지 검증하는 단위 테스트(#447).
 * 로컬은 파일이면 덮어쓰고 폴더면 합치며, 원격은 종류와 무관하게 서버 동작에 따른다.
 */
class RenameOverwriteTest {

    private val scope = CoroutineScope(Job())

    /** 넣은 실패·완료 메시지를 기록하는 메시지 저장소. */
    private val notices = RecordingOperationNotices()
    private val orchestrator = FileOperationOrchestrator(NoOpNotifier, NoOpProgressReporter, notices, GrantedProtection, UnexpectedQuestions, scope)
    private val local = LocalFileOperationUseCase(orchestrator, notices, NoOpFileActionRepository, FileNameUseCase(NoStorageNameRules), LocalPathRules())
    private val remote = RemoteFileOperationUseCase(orchestrator, notices, NoOpTransferRepository, NoOpMutationRepository, ConnectedSessionProvider, FileNameUseCase(NoStorageNameRules))

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `로컬 파일은 기존 파일을 덮어쓴다`() {
        assertEquals(RenameOverwrite.REPLACE_FILE, local.renameOverwriteOf(isDirectory = false))
    }

    @Test
    fun `로컬 폴더는 두 폴더를 합친다`() {
        assertEquals(RenameOverwrite.MERGE_FOLDER, local.renameOverwriteOf(isDirectory = true))
    }

    @Test
    fun `원격은 파일과 폴더 모두 서버 동작에 따른다`() {
        assertEquals(RenameOverwrite.STORAGE_DEFINED, remote.renameOverwriteOf(isDirectory = false))
        assertEquals(RenameOverwrite.STORAGE_DEFINED, remote.renameOverwriteOf(isDirectory = true))
    }

    /** 작업을 수행하지 않는 테스트용 로컬 파일 작업 저장소. 이 테스트는 저장소를 쓰지 않는다. */
    private object NoOpFileActionRepository : FileActionRepository {
        override suspend fun compressAction(targetFiles: Set<String>, parentPath: String, baseName: String, format: ArchiveFormat, password: String?, onProgress: (ProgressPhase) -> Unit) = IoResult.Done
        override fun supportedArchiveFormats() = ArchiveFormat.entries.toSet()
        override suspend fun copyAction(targetFiles: Set<String>, targetPath: String, resolveConflict: WriteConflictResolver, onProgress: (ProgressPhase) -> Unit) = IoResult.Done
        override suspend fun renameFile(targetFile: String, newName: String, overwrite: Boolean) = IoResult.Done
        override suspend fun deleteFiles(targetFiles: Set<String>, onProgress: (ProgressPhase) -> Unit) = IoResult.Done
        override suspend fun moveFile(targetFiles: Set<String>, targetPath: String, resolveConflict: WriteConflictResolver, onProgress: (ProgressPhase) -> Unit) = IoResult.Done
        override suspend fun unzipAction(zipFiles: Set<String>, destPath: String, layout: ExtractLayout, resolveConflict: WriteConflictResolver, askPassword: ArchivePasswordProvider, onProgress: (ProgressPhase) -> Unit) = IoResult.Done
        override fun extractionTargetOf(paths: Set<String>): String? = null
        override suspend fun makeDirectory(parentPath: String, name: String) = IoResult.Done
    }

    /** 변경을 수행하지 않는 테스트용 원격 변경 저장소. */
    private object NoOpMutationRepository : RemoteFileMutationRepository {
        override suspend fun rename(session: RemoteSession, path: String, newName: String) = IoResult.Done
        override suspend fun delete(session: RemoteSession, items: List<FileHolderItem>, onProgress: (ProgressPhase) -> Unit) = IoResult.Done
        override suspend fun makeDirectory(session: RemoteSession, parentPath: String, name: String) = IoResult.Done
    }

    /** 전송을 수행하지 않는 테스트용 원격 전송 저장소. */
    private object NoOpTransferRepository : RemoteFileTransferRepository {
        override suspend fun download(session: RemoteSession, items: List<FileHolderItem>, localDirPath: String, resolveConflict: WriteConflictResolver, onProgress: (ProgressPhase) -> Unit) = IoResult.Done
        override suspend fun upload(session: RemoteSession, items: List<FileHolderItem>, remoteDirPath: String, onProgress: (ProgressPhase) -> Unit) = IoResult.Done
    }

    /** 진행 상태를 기록하지 않는 테스트용 진행 저장소. */
    private object NoOpProgressReporter : ProgressReporter {
        override fun setProgressState(instanceId: Int, state: ProgressState) {}
        override fun removeProgress(instanceId: Int) {}
    }

    /** 알림을 표시하지 않는 테스트용 알림기. */
    private object NoOpNotifier : FileOperationNotifier {
        override fun progressNotification(state: ProgressState, instanceId: Int) {}
        override fun completedNotification(kind: OperationKind, instanceId: Int) {}
        override fun cancelNotification(instanceId: Int) {}
        override fun awaitingAnswerNotification(operationType: FileOperationType, kind: QuestionKind, instanceId: Int) {}
    }
}
