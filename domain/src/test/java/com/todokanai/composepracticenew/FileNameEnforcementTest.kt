package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.repository.FileActionRepository
import com.todokanai.composepracticenew.repository.ProgressRepository
import com.todokanai.composepracticenew.repository.RemoteFileMutationRepository
import com.todokanai.composepracticenew.repository.RemoteFileTransferRepository
import com.todokanai.composepracticenew.usecase.FileNameUseCase
import com.todokanai.composepracticenew.usecase.LocalFileOperationUseCase
import com.todokanai.composepracticenew.usecase.RemoteFileOperationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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
    private val fileActionRepo = RecordingFileActionRepository()
    private val mutationRepo = RecordingMutationRepository()
    private val orchestrator = FileOperationOrchestrator(NoOpNotifier, progressRepo, scope)
    private val local = LocalFileOperationUseCase(orchestrator, fileActionRepo, FileNameUseCase())
    private val remote = RemoteFileOperationUseCase(orchestrator, NoOpTransferRepository, mutationRepo, FileNameUseCase())

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `로컬 이름 변경·새 폴더·압축은 규칙 위반 이름을 거부하고 오류를 방출한다`() {
        local.rename("/dir/a.txt", "x/y", false, "done") {}
        local.makeDirectory("/dir", "..", "done") {}
        local.zip(setOf("/dir/a.txt"), "/dir", " archive", "done") {}

        assertEquals(3, progressRepo.errors.size)
        assertTrue(progressRepo.errors[0].startsWith("이름 변경 실패"))
        assertTrue(progressRepo.errors[1].startsWith("폴더 생성 실패"))
        assertTrue(progressRepo.errors[2].startsWith("압축 실패"))
        assertTrue("저장소가 호출되면 안 됨: ${fileActionRepo.calls}", fileActionRepo.calls.isEmpty())
    }

    @Test
    fun `원격 이름 변경·새 폴더는 규칙 위반 이름을 거부하고 오류를 방출한다`() {
        remote.renameRemote("/r/a.txt", "a\\b", "done", "fail") {}
        remote.makeDirectoryRemote("/r", "", "done", "fail") {}

        assertEquals(2, progressRepo.errors.size)
        assertTrue(progressRepo.errors[0].startsWith("이름 변경 실패"))
        assertTrue(progressRepo.errors[1].startsWith("디렉터리 생성 실패"))
        assertTrue("저장소가 호출되면 안 됨: ${mutationRepo.calls}", mutationRepo.calls.isEmpty())
    }

    @Test
    fun `규칙을 지킨 이름은 오류 없이 실행 단계로 넘어간다`() {
        local.rename("/dir/a.txt", "a.md", false, "done") {}
        local.makeDirectory("/dir", "new folder", "done") {}
        remote.renameRemote("/r/a.txt", "b.txt", "done", "fail") {}

        assertTrue(progressRepo.errors.toString(), progressRepo.errors.isEmpty())
    }

    @Test
    fun `압축 이름 검증은 확장자를 포함한 길이로 판정한다`() {
        assertTrue(local.validateZipName("a".repeat(251)).isEmpty())
        assertTrue(local.validateZipName("a".repeat(252)).isNotEmpty())
    }

    /** 방출된 오류 메시지를 기록하는 테스트용 진행 저장소. */
    private class RecordingProgressRepository : ProgressRepository {
        val errors = mutableListOf<String>()
        override val progressMap: StateFlow<Map<Int, ProgressState>> = MutableStateFlow(emptyMap())
        override val operationErrors: SharedFlow<String> = MutableSharedFlow()
        override val operationCompletions: SharedFlow<String> = MutableSharedFlow()
        override fun setProgressState(instanceId: Int, state: ProgressState) {}
        override fun removeProgress(instanceId: Int) {}
        override fun emitError(message: String) { errors += message }
        override fun emitCompletion(message: String) {}
    }

    /** 호출된 작업 이름을 기록하는 테스트용 로컬 파일 작업 저장소. */
    private class RecordingFileActionRepository : FileActionRepository {
        val calls = mutableListOf<String>()
        override suspend fun zipAction(targetFiles: Set<String>, zipFile: String, onProgress: (ProgressState) -> Unit) { calls += "zip" }
        override suspend fun copyAction(targetFiles: Set<String>, targetPath: String, onProgress: (ProgressState) -> Unit) { calls += "copy" }
        override suspend fun renameFile(targetFile: String, newName: String, overwrite: Boolean) { calls += "rename" }
        override suspend fun deleteFile(targetFile: String, onProgress: (ProgressState) -> Unit) { calls += "delete" }
        override suspend fun moveFile(targetFiles: Set<String>, targetPath: String, onProgress: (ProgressState) -> Unit) { calls += "move" }
        override suspend fun unzipAction(zipFiles: Set<String>, destPath: String, unzipHere: Boolean, onProgress: (ProgressState) -> Unit) { calls += "unzip" }
        override suspend fun makeDirectory(parentPath: String, name: String) { calls += "mkdir" }
    }

    /** 호출된 작업 이름을 기록하는 테스트용 원격 변경 저장소. */
    private class RecordingMutationRepository : RemoteFileMutationRepository {
        val calls = mutableListOf<String>()
        override suspend fun rename(fromPath: String, toPath: String) { calls += "rename" }
        override suspend fun deleteFile(path: String) { calls += "delete" }
        override suspend fun makeDirectory(path: String) { calls += "mkdir" }
        override suspend fun removeDirectoryRecursive(path: String) { calls += "rmdir" }
    }

    /** 전송을 수행하지 않는 테스트용 원격 전송 저장소. 이 테스트의 대상 작업은 전송을 쓰지 않는다. */
    private object NoOpTransferRepository : RemoteFileTransferRepository {
        override suspend fun download(remotePath: String, localPath: String, isDirectory: Boolean, onProgress: (ProgressState) -> Unit) {}
        override suspend fun upload(localPath: String, remotePath: String, onProgress: (ProgressState) -> Unit) {}
    }

    /** 알림을 표시하지 않는 테스트용 알림기. */
    private object NoOpNotifier : FileOperationNotifier {
        override fun progressNoti(state: ProgressState, instanceId: Int) {}
        override fun completedNotification(title: String, message: String, operationType: FileOperationType?, notifId: Int) {}
        override fun cancelNotification(notifId: Int) {}
    }
}
