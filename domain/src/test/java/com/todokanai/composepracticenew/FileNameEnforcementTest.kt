package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.repository.WriteConflictResolver
import com.todokanai.composepracticenew.model.ArchiveFormat
import com.todokanai.composepracticenew.model.ExtractLayout
import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.repository.FileActionRepository
import com.todokanai.composepracticenew.repository.ProgressRepository
import com.todokanai.composepracticenew.repository.RemoteFileMutationRepository
import com.todokanai.composepracticenew.repository.RemoteFileTransferRepository
import com.todokanai.composepracticenew.usecase.FileNameUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.LocalFileOperationUseCase
import com.todokanai.composepracticenew.usecase.RemoteFileOperationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 실행 UseCase(로컬·원격 파일 작업)가 파일 이름 규칙을 직접 강제하는지 검증하는 단위 테스트.
 * 규칙 위반 이름은 저장소를 호출하지 않고 오류를 방출해야 하며, 다이얼로그를 거치지 않는 호출도 같은 규칙을 통과해야 한다.
 */
class FileNameEnforcementTest {

    private val scope = CoroutineScope(Job())
    private val progressRepo = RecordingProgressRepository()

    /** 넣은 실패·완료 메시지를 기록하는 메시지 저장소. */
    private val notices = RecordingOperationNotices()
    private val fileActionRepo = RecordingFileActionRepository()
    private val mutationRepo = RecordingMutationRepository()
    private val orchestrator = FileOperationOrchestrator(NoOpNotifier, progressRepo, notices, GrantedProtection, UnexpectedConflictQuestions, scope)
    /** 저장소의 제약을 물은 이름과 위치를 기록하는 이름 규칙. */
    private val storageNames = RecordingStorageNameRules()
    private val local = LocalFileOperationUseCase(orchestrator, notices, fileActionRepo, FileNameUseCase(storageNames), LocalPathRules())
    private val remote = RemoteFileOperationUseCase(orchestrator, notices, NoOpTransferRepository, mutationRepo, FileNameUseCase(storageNames))

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `로컬 이름 변경·새 폴더·압축은 규칙 위반 이름을 거부하고 오류를 방출한다`() {
        local.rename("/dir/a.txt", "x/y", false, "done") {}
        local.makeDirectory("/dir", "..", "done") {}
        local.compress(setOf("/dir/a.txt"), "/dir", " archive", ArchiveFormat.ZIP, "done") {}

        assertEquals(3, notices.failures.size)
        assertTrue(notices.failures[0].startsWith("이름 변경 실패"))
        assertTrue(notices.failures[1].startsWith("폴더 생성 실패"))
        assertTrue(notices.failures[2].startsWith("압축 실패"))
        assertTrue("저장소가 호출되면 안 됨: ${fileActionRepo.calls}", fileActionRepo.calls.isEmpty())
    }

    @Test
    fun `원격 이름 변경·새 폴더는 규칙 위반 이름을 거부하고 오류를 방출한다`() {
        remote.renameRemote("/r/a.txt", "a\\b", "done", "fail") {}
        remote.makeDirectoryRemote("/r", "", "done", "fail") {}

        assertEquals(2, notices.failures.size)
        assertTrue(notices.failures[0].startsWith("이름 변경 실패"))
        assertTrue(notices.failures[1].startsWith("디렉터리 생성 실패"))
        assertTrue("저장소가 호출되면 안 됨: ${mutationRepo.calls}", mutationRepo.calls.isEmpty())
    }

    @Test
    fun `규칙을 지킨 이름은 오류 없이 실행 단계로 넘어간다`() {
        local.rename("/dir/a.txt", "a.md", false, "done") {}
        local.makeDirectory("/dir", "new folder", "done") {}
        remote.renameRemote("/r/a.txt", "b.txt", "done", "fail") {}

        assertTrue(notices.failures.toString(), notices.failures.isEmpty())
    }

    @Test
    fun `저장소의 제약은 이름이 놓일 위치로 묻는다 — 새 항목은 만들 폴더, 이름 변경은 그 항목 자신`() {
        local.rename("/dir/a.txt", "a.md", false, "done") {}
        local.makeDirectory("/dir", "new folder", "done") {}
        local.compress(setOf("/dir/a.txt"), "/dir", "archive", ArchiveFormat.ZIP, "done") {}
        remote.renameRemote("/r/a.txt", "b.txt", "done", "fail") {}
        remote.makeDirectoryRemote("/r", "sub", "done", "fail") {}

        assertEquals(
            listOf("a.md" to "/dir/a.txt", "new folder" to "/dir", "archive.zip" to "/dir", "b.txt" to "/r/a.txt", "sub" to "/r"),
            storageNames.calls
        )
    }

    @Test
    fun `저장소의 제약을 위반한 이름은 저장소를 호출하지 않고 거부한다`() {
        val tooLong = RecordingStorageNameRules(setOf(FileNameError.TOO_LONG))
        val strictLocal = LocalFileOperationUseCase(orchestrator, notices, fileActionRepo, FileNameUseCase(tooLong), LocalPathRules())

        strictLocal.makeDirectory("/dir", "new folder", "done") {}

        assertEquals(1, notices.failures.size)
        assertTrue(notices.failures[0].startsWith("폴더 생성 실패"))
        assertTrue("저장소가 호출되면 안 됨: ${fileActionRepo.calls}", fileActionRepo.calls.isEmpty())
    }

    /** 진행 상태를 기록하지 않는 테스트용 진행 저장소. */
    private class RecordingProgressRepository : ProgressRepository {
        override val progressMap: StateFlow<Map<Int, ProgressState>> = MutableStateFlow(emptyMap())
        override fun setProgressState(instanceId: Int, state: ProgressState) {}
        override fun removeProgress(instanceId: Int) {}
    }

    /** 호출된 작업 이름을 기록하는 테스트용 로컬 파일 작업 저장소. */
    private class RecordingFileActionRepository : FileActionRepository {
        val calls = mutableListOf<String>()
        override suspend fun compressAction(targetFiles: Set<String>, parentPath: String, baseName: String, format: ArchiveFormat, onProgress: (ProgressState) -> Unit) = IoResult.Done.also { calls += "compress" }
        override fun supportedArchiveFormats() = ArchiveFormat.entries.toSet()
        override suspend fun copyAction(targetFiles: Set<String>, targetPath: String, resolveConflict: WriteConflictResolver, onProgress: (ProgressState) -> Unit) = IoResult.Done.also { calls += "copy" }
        override suspend fun renameFile(targetFile: String, newName: String, overwrite: Boolean) = IoResult.Done.also { calls += "rename" }
        override suspend fun deleteFiles(targetFiles: Set<String>, onProgress: (ProgressState) -> Unit) = IoResult.Done.also { calls += "delete" }
        override suspend fun moveFile(targetFiles: Set<String>, targetPath: String, resolveConflict: WriteConflictResolver, onProgress: (ProgressState) -> Unit) = IoResult.Done.also { calls += "move" }
        override suspend fun unzipAction(zipFiles: Set<String>, destPath: String, layout: ExtractLayout, resolveConflict: WriteConflictResolver, onProgress: (ProgressState) -> Unit) = IoResult.Done.also { calls += "unzip" }
        override fun canExtract(path: String) = false
        override suspend fun makeDirectory(parentPath: String, name: String) = IoResult.Done.also { calls += "mkdir" }
    }

    /** 호출된 작업 이름을 기록하는 테스트용 원격 변경 저장소. */
    private class RecordingMutationRepository : RemoteFileMutationRepository {
        val calls = mutableListOf<String>()
        override suspend fun rename(path: String, newName: String) = IoResult.Done.also { calls += "rename" }
        override suspend fun delete(items: List<com.todokanai.composepracticenew.model.FileHolderItem>, onProgress: (ProgressState) -> Unit) = IoResult.Done.also { calls += "delete" }
        override suspend fun makeDirectory(parentPath: String, name: String) = IoResult.Done.also { calls += "mkdir" }
    }

    /** 전송을 수행하지 않는 테스트용 원격 전송 저장소. 이 테스트의 대상 작업은 전송을 쓰지 않는다. */
    private object NoOpTransferRepository : RemoteFileTransferRepository {
        override suspend fun download(items: List<com.todokanai.composepracticenew.model.FileHolderItem>, localDirPath: String, resolveConflict: WriteConflictResolver, onProgress: (ProgressState) -> Unit) = IoResult.Done
        override suspend fun upload(items: List<com.todokanai.composepracticenew.model.FileHolderItem>, remoteDirPath: String, onProgress: (ProgressState) -> Unit) = IoResult.Done
    }

    /** 알림을 표시하지 않는 테스트용 알림기. */
    private object NoOpNotifier : FileOperationNotifier {
        override fun progressNoti(state: ProgressState, instanceId: Int) {}
        override fun completedNotification(message: String, instanceId: Int) {}
        override fun cancelNotification(instanceId: Int) {}
        override fun awaitingAnswerNoti(operationType: FileOperationType, instanceId: Int) {}
    }
}
