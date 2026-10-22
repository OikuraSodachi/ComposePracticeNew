package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.RemoteConnectionLostException
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.repository.ProgressRepository
import com.todokanai.composepracticenew.repository.RemoteFileMutationRepository
import com.todokanai.composepracticenew.repository.RemoteFileTransferRepository
import com.todokanai.composepracticenew.usecase.FileNameUseCase
import com.todokanai.composepracticenew.usecase.RemoteFileOperationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
 * 원격 전송·삭제가 실패를 "완료"로 처리하지 않고 오류로 알리는지 검증하는 단위 테스트(#169).
 * 저장소 가짜가 일시 중단하지 않으므로 Unconfined 스코프에서 작업이 호출 즉시 끝난다.
 */
class RemoteFileOperationFailureTest {

    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
    private val progressRepo = RecordingProgressRepository()
    private val transferRepo = FakeTransferRepository()
    private val mutationRepo = FakeMutationRepository()
    private val orchestrator = FileOperationOrchestrator(NoOpNotifier, progressRepo, scope)
    private val remote = RemoteFileOperationUseCase(orchestrator, transferRepo, mutationRepo, FileNameUseCase())

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `다운로드가 실패하면 완료 대신 오류를 알린다`() {
        transferRepo.failure = java.io.IOException("retrieveFileStream 실패: 550 No such file")

        remote.download("ftp://h/r/a.txt", "/local", isDirectory = false, completionMessage = "done") {}

        assertEquals(listOf("retrieveFileStream 실패: 550 No such file"), progressRepo.errors)
        assertTrue(progressRepo.completions.isEmpty())
    }

    @Test
    fun `업로드 중 연결이 끊기면 완료 대신 오류를 알린다`() {
        transferRepo.failure = RemoteConnectionLostException("FTP 서버와의 연결이 끊겼습니다")

        remote.upload("/local/a.txt", "ftp://h/r", completionMessage = "done") {}

        assertEquals(listOf("FTP 서버와의 연결이 끊겼습니다"), progressRepo.errors)
        assertTrue(progressRepo.completions.isEmpty())
    }

    @Test
    fun `전송이 성공하면 완료를 알린다`() {
        remote.download("ftp://h/r/a.txt", "/local", isDirectory = false, completionMessage = "done") {}

        assertEquals(listOf("done"), progressRepo.completions)
        assertTrue(progressRepo.errors.isEmpty())
    }

    @Test
    fun `원격 삭제는 일부가 실패해도 나머지를 끝까지 삭제하고 실패를 알린다`() {
        mutationRepo.failingPaths += "ftp://h/r/b.txt"

        remote.deleteRemote(
            targets = linkedMapOf("ftp://h/r/a.txt" to false, "ftp://h/r/b.txt" to false, "ftp://h/r/dir" to true),
            completionMessage = "done"
        ) {}

        assertEquals(listOf("delete a.txt", "delete b.txt", "rmdir dir"), mutationRepo.calls)
        assertEquals(1, progressRepo.errors.size)
        assertTrue(progressRepo.errors.single(), progressRepo.errors.single().startsWith("삭제 실패: b.txt"))
        assertTrue(progressRepo.completions.isEmpty())
    }

    @Test
    fun `원격 삭제 중 연결이 끊기면 남은 항목을 시도하지 않는다`() {
        mutationRepo.connectionLostAt = "ftp://h/r/a.txt"

        remote.deleteRemote(
            targets = linkedMapOf("ftp://h/r/a.txt" to false, "ftp://h/r/b.txt" to false),
            completionMessage = "done"
        ) {}

        assertEquals(listOf("delete a.txt"), mutationRepo.calls)
        assertEquals(listOf("끊김"), progressRepo.errors)
    }

    /** 방출된 완료·오류 메시지를 기록하는 테스트용 진행 저장소. */
    private class RecordingProgressRepository : ProgressRepository {
        val errors = mutableListOf<String>()
        val completions = mutableListOf<String>()
        override val progressMap: StateFlow<Map<Int, ProgressState>> = MutableStateFlow(emptyMap())
        override val operationErrors: SharedFlow<String> = MutableSharedFlow()
        override val operationCompletions: SharedFlow<String> = MutableSharedFlow()
        override fun setProgressState(instanceId: Int, state: ProgressState) {}
        override fun removeProgress(instanceId: Int) {}
        override fun emitError(message: String) { errors += message }
        override fun emitCompletion(message: String) { completions += message }
    }

    /** [failure]가 있으면 전송 시 그 예외를 던지는 테스트용 원격 전송 저장소. */
    private class FakeTransferRepository : RemoteFileTransferRepository {
        var failure: Exception? = null
        override suspend fun download(remotePath: String, localPath: String, isDirectory: Boolean, onProgress: (ProgressState) -> Unit) {
            failure?.let { throw it }
        }
        override suspend fun upload(localPath: String, remotePath: String, onProgress: (ProgressState) -> Unit) {
            failure?.let { throw it }
        }
    }

    /** 호출을 기록하고, 지정한 경로에서 실패 또는 연결 끊김을 일으키는 테스트용 원격 변경 저장소. */
    private class FakeMutationRepository : RemoteFileMutationRepository {
        val calls = mutableListOf<String>()
        val failingPaths = mutableSetOf<String>()
        var connectionLostAt: String? = null
        override suspend fun rename(fromPath: String, toPath: String) { record("rename", fromPath) }
        override suspend fun deleteFile(path: String) { record("delete", path) }
        override suspend fun makeDirectory(path: String) { record("mkdir", path) }
        override suspend fun removeDirectoryRecursive(path: String) { record("rmdir", path) }

        private fun record(command: String, path: String) {
            calls += "$command ${path.substringAfterLast('/')}"
            if (path == connectionLostAt) throw RemoteConnectionLostException("끊김")
            if (path in failingPaths) throw java.io.IOException("$command 실패: 550")
        }
    }

    /** 알림을 표시하지 않는 테스트용 알림기. */
    private object NoOpNotifier : FileOperationNotifier {
        override fun progressNoti(state: ProgressState, instanceId: Int) {}
        override fun completedNotification(title: String, message: String, operationType: FileOperationType?, notifId: Int) {}
        override fun cancelNotification(notifId: Int) {}
    }
}
