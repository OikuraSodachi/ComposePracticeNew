package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.repository.WriteConflictResolver
import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState
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
import kotlinx.coroutines.flow.MutableStateFlow
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

    /** 넣은 실패·완료 메시지를 기록하는 메시지 저장소. */
    private val notices = RecordingOperationNotices()
    private val transferRepo = FakeTransferRepository()
    private val mutationRepo = FakeMutationRepository()
    private val orchestrator = FileOperationOrchestrator(NoOpNotifier, progressRepo, notices, GrantedProtection, UnexpectedConflictQuestions, scope)
    private val remote = RemoteFileOperationUseCase(orchestrator, notices, transferRepo, mutationRepo, FileNameUseCase(NoStorageNameRules))

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `다운로드가 실패하면 완료 대신 오류를 알린다`() {
        transferRepo.failure = IoResult.ItemFailed("RETR 실패: 550 No such file")

        remote.download(listOf(item("ftp://h/r/a.txt", isDirectory = false)), "/local", completionMessage = "done") {}

        assertEquals(listOf("RETR 실패: 550 No such file"), notices.failures)
        assertTrue(notices.completions.isEmpty())
    }

    @Test
    fun `업로드 중 연결이 끊기면 완료 대신 오류를 알린다`() {
        transferRepo.failure = IoResult.ConnectionLost("FTP 서버와의 연결이 끊겼습니다")

        remote.upload(item("/local/a.txt", isDirectory = false), "ftp://h/r", completionMessage = "done") {}

        assertEquals(listOf("FTP 서버와의 연결이 끊겼습니다"), notices.failures)
        assertTrue(notices.completions.isEmpty())
    }

    @Test
    fun `전송이 성공하면 완료를 알린다`() {
        remote.download(listOf(item("ftp://h/r/a.txt", isDirectory = false)), "/local", completionMessage = "done") {}

        assertEquals(listOf("done"), notices.completions)
        assertTrue(notices.failures.isEmpty())
    }

    @Test
    fun `한 번에 고른 여러 항목은 작업 하나로 저장소에 함께 맡긴다`() {
        val items = listOf(item("ftp://h/r/a.txt", isDirectory = false), item("ftp://h/r/d", isDirectory = true))

        remote.download(items, "/local", completionMessage = "done") {}

        assertEquals(listOf(items), transferRepo.downloads)
        assertEquals(listOf("done"), notices.completions)
    }

    @Test
    fun `원격 삭제는 고른 항목을 작업 하나로 저장소에 함께 맡긴다`() {
        // 진행을 작업 전체 기준으로 보고하려면 저장소가 대상 전체를 알아야 한다(폴더 안쪽 수는 구현만 안다)
        val items = listOf(item("ftp://h/r/a.txt", false), item("ftp://h/r/dir", true))

        remote.deleteRemote(targets = items, completionMessage = "done") {}

        assertEquals(listOf(items), mutationRepo.deletes)
        assertEquals(listOf("done"), notices.completions)
    }

    @Test
    fun `원격 삭제가 일부 실패하면 완료 대신 저장소가 모은 실패를 알린다`() {
        mutationRepo.deleteResult = IoResult.PartiallyFailed("삭제 실패", listOf(FailedItem("b.txt", "DELE 실패: 550")))

        remote.deleteRemote(targets = listOf(item("ftp://h/r/b.txt", false)), completionMessage = "done") {}

        assertEquals(listOf("삭제 실패: b.txt (DELE 실패: 550)"), notices.failures)
        assertTrue(notices.completions.isEmpty())
    }

    @Test
    fun `원격 삭제는 진행 표시가 있는 삭제 작업으로 실행한다`() {
        // 폴더 삭제는 하위 항목 수에 비례해 오래 걸리므로 진행 표시·취소가 있어야 한다(#334)
        remote.deleteRemote(targets = listOf(item("ftp://h/r/dir", true)), completionMessage = "done") {}

        assertEquals(FileOperationType.DELETE, progressRepo.states.first().operationType)
    }

    /** [path]의 마지막 경로 조각을 이름으로 갖는 테스트 항목을 만든다. */
    private fun item(path: String, isDirectory: Boolean) =
        FileHolderItem(path = path, isDirectory = isDirectory, name = path.substringAfterLast('/'), sizeBytes = null, lastModifiedMillis = 0L)

    /** 등록된 진행 상태를 기록하는 테스트용 진행 저장소. */
    private class RecordingProgressRepository : ProgressRepository {
        /** 등록된 진행 상태(등록 순서). */
        val states = mutableListOf<ProgressState>()
        override val progressMap: StateFlow<Map<Int, ProgressState>> = MutableStateFlow(emptyMap())
        override fun setProgressState(instanceId: Int, state: ProgressState) { states += state }
        override fun removeProgress(instanceId: Int) {}
    }

    /** [failure]가 있으면 전송 시 그 실패를 돌려주고, 다운로드 요청을 [downloads]에 기록하는 테스트용 원격 전송 저장소. */
    private class FakeTransferRepository : RemoteFileTransferRepository {
        var failure: IoResult.Failure? = null
        /** 다운로드 요청마다 받은 항목 목록(요청 순서). */
        val downloads = mutableListOf<List<FileHolderItem>>()
        override suspend fun download(items: List<FileHolderItem>, localDirPath: String, resolveConflict: WriteConflictResolver, onProgress: (ProgressState) -> Unit): IoResult<Unit> {
            downloads += items
            return failure ?: IoResult.Done
        }
        override suspend fun upload(localPath: String, remoteDirPath: String, name: String, onProgress: (ProgressState) -> Unit): IoResult<Unit> {
            return failure ?: IoResult.Done
        }
    }

    /** 삭제 요청을 기록하고 정해 둔 결과를 돌려주는 테스트용 원격 변경 저장소. */
    private class FakeMutationRepository : RemoteFileMutationRepository {
        /** 삭제 요청마다 받은 항목 목록. */
        val deletes = mutableListOf<List<FileHolderItem>>()
        /** 삭제 요청이 돌려줄 결과. */
        var deleteResult: IoResult<Unit> = IoResult.Done
        override suspend fun rename(path: String, newName: String) = IoResult.Done
        override suspend fun delete(items: List<FileHolderItem>, onProgress: (ProgressState) -> Unit) = deleteResult.also { deletes += items }
        override suspend fun makeDirectory(parentPath: String, name: String) = IoResult.Done
    }

    /** 알림을 표시하지 않는 테스트용 알림기. */
    private object NoOpNotifier : FileOperationNotifier {
        override fun progressNoti(state: ProgressState, instanceId: Int) {}
        override fun completedNotification(message: String, instanceId: Int) {}
        override fun cancelNotification(instanceId: Int) {}
        override fun awaitingAnswerNoti(operationType: FileOperationType, instanceId: Int) {}
    }
}
