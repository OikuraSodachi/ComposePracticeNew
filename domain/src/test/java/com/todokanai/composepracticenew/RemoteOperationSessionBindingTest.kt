package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.NoticeMessage
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.QuestionKind
import com.todokanai.composepracticenew.model.RemoteProtocolSettings
import com.todokanai.composepracticenew.model.RemoteSession
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.operation.OperationProtection
import com.todokanai.composepracticenew.repository.ProgressRepository
import com.todokanai.composepracticenew.repository.RemoteFileMutationRepository
import com.todokanai.composepracticenew.repository.RemoteFileTransferRepository
import com.todokanai.composepracticenew.repository.RemoteSessionProvider
import com.todokanai.composepracticenew.repository.WriteConflictResolver
import com.todokanai.composepracticenew.usecase.FileNameUseCase
import com.todokanai.composepracticenew.usecase.RemoteFileOperationUseCase
import kotlinx.coroutines.CompletableDeferred
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
 * 원격 작업이 실행을 요청할 때(보호를 기다리기 전에) 지금의 세션에 묶이는지, 요청할 때 연결돼 있지 않으면 저장소를 부르지 않고 실패로 끝나는지 검증하는 단위 테스트(#436).
 * 보호는 테스트가 풀 때까지 기다리게 해, 요청과 실행 사이에 다른 서버로 접속한 상황을 만든다. 저장소 가짜가 일시 중단하지 않으므로 Unconfined 스코프에서 실행한다.
 */
class RemoteOperationSessionBindingTest {

    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
    /** 넣은 실패·완료 메시지를 기록하는 메시지 저장소. */
    private val notices = RecordingOperationNotices()
    /** 테스트가 바꿀 수 있는 지금의 세션. */
    private val sessions = SwitchableSessions()
    /** 보호를 주기 전까지 작업을 기다리게 하는 문. */
    private val protectionGate = CompletableDeferred<Unit>()
    private val transferRepo = SessionRecordingTransfers()
    private val mutationRepo = SessionRecordingMutations()
    private val orchestrator = FileOperationOrchestrator(NoOpNotifier, NoOpProgress, notices, GatedProtection(protectionGate), UnexpectedQuestions, scope)
    private val remote = RemoteFileOperationUseCase(orchestrator, notices, transferRepo, mutationRepo, sessions, FileNameUseCase(NoStorageNameRules))

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `보호를 기다리는 동안 다른 서버에 접속해도 업로드는 요청할 때의 세션으로 실행한다`() {
        sessions.current = FIRST

        remote.upload(listOf(item("/local/a.txt")), "ftp://h/r") {}
        sessions.current = SECOND
        protectionGate.complete(Unit)

        assertEquals(listOf(FIRST.id), transferRepo.sessionIds)
    }

    @Test
    fun `다운로드·삭제·이름 변경·새 폴더도 요청할 때의 세션으로 실행한다`() {
        sessions.current = FIRST

        remote.download(listOf(item("ftp://h/r/a.txt")), "/local") {}
        remote.deleteRemote(listOf(item("ftp://h/r/b.txt"))) {}
        remote.renameRemote("ftp://h/r/c.txt", "d.txt") {}
        remote.makeDirectoryRemote("ftp://h/r", "new") {}
        sessions.current = SECOND
        protectionGate.complete(Unit)

        assertEquals(listOf(FIRST.id), transferRepo.sessionIds)
        assertEquals(listOf(FIRST.id, FIRST.id, FIRST.id), mutationRepo.sessionIds)
    }

    @Test
    fun `요청할 때 연결돼 있지 않으면 저장소를 부르지 않고 연결 없음으로 알린다`() {
        sessions.current = null

        remote.upload(listOf(item("/local/a.txt")), "ftp://h/r") {}
        // 보호를 기다리는 사이에 연결돼도 요청할 때 정한 결과를 따른다
        sessions.current = FIRST
        protectionGate.complete(Unit)

        assertTrue(transferRepo.sessionIds.isEmpty())
        assertEquals(listOf(NoticeMessage.OperationFailed(FileOperationType.UPLOAD, IoResult.ConnectionLost(FailureReason.NotConnected))), notices.failures)
    }

    private fun item(path: String) =
        FileHolderItem(path = path, isDirectory = false, name = path.substringAfterLast('/'), sizeBytes = null, lastModifiedMillis = 0L)

    /** 테스트가 정한 세션을 돌려주는 세션 제공자. */
    private class SwitchableSessions : RemoteSessionProvider {
        /** 지금의 세션. null이면 연결돼 있지 않다. */
        var current: RemoteSession? = null
        override fun current(): RemoteSession? = current
    }

    /**
     * [gate]가 열릴 때까지 기다린 뒤 보호를 주는 작업 보호.
     * @param gate 보호를 줄 때를 정하는 문
     */
    private class GatedProtection(private val gate: CompletableDeferred<Unit>) : OperationProtection {
        override suspend fun acquire(): IoResult<Unit> {
            gate.await()
            return IoResult.Done
        }
    }

    /** 전송 요청이 받은 세션 id를 기록하는 원격 전송 저장소. */
    private class SessionRecordingTransfers : RemoteFileTransferRepository {
        /** 요청마다 받은 세션 id(요청 순서). */
        val sessionIds = mutableListOf<Long>()
        override suspend fun download(session: RemoteSession, items: List<FileHolderItem>, localDirPath: String, resolveConflict: WriteConflictResolver, onProgress: (ProgressState) -> Unit) =
            IoResult.Done.also { sessionIds += session.id }
        override suspend fun upload(session: RemoteSession, items: List<FileHolderItem>, remoteDirPath: String, onProgress: (ProgressState) -> Unit) =
            IoResult.Done.also { sessionIds += session.id }
    }

    /** 변경 요청이 받은 세션 id를 기록하는 원격 변경 저장소. */
    private class SessionRecordingMutations : RemoteFileMutationRepository {
        /** 요청마다 받은 세션 id(요청 순서). */
        val sessionIds = mutableListOf<Long>()
        override suspend fun rename(session: RemoteSession, path: String, newName: String) = IoResult.Done.also { sessionIds += session.id }
        override suspend fun delete(session: RemoteSession, items: List<FileHolderItem>, onProgress: (ProgressState) -> Unit) = IoResult.Done.also { sessionIds += session.id }
        override suspend fun makeDirectory(session: RemoteSession, parentPath: String, name: String) = IoResult.Done.also { sessionIds += session.id }
    }

    /** 진행 상태를 버리는 진행 저장소. */
    private object NoOpProgress : ProgressRepository {
        override val progressMap: StateFlow<Map<Int, ProgressState>> = MutableStateFlow(emptyMap())
        override fun setProgressState(instanceId: Int, state: ProgressState) {}
        override fun removeProgress(instanceId: Int) {}
    }

    /** 알림을 표시하지 않는 테스트용 알림기. */
    private object NoOpNotifier : FileOperationNotifier {
        override fun progressNoti(state: ProgressState, instanceId: Int) {}
        override fun completedNotification(operationType: FileOperationType?, instanceId: Int) {}
        override fun cancelNotification(instanceId: Int) {}
        override fun awaitingAnswerNoti(operationType: FileOperationType, kind: QuestionKind, instanceId: Int) {}
    }

    private companion object {
        /** 처음 연결한 서버의 세션. */
        val FIRST = RemoteSession(1, RemoteStorageItem(name = "first", address = "first", port = 21, userId = "u", password = "p", settings = RemoteProtocolSettings.Ftp("UTF-8")))
        /** 그 뒤 연결한 다른 서버의 세션. */
        val SECOND = RemoteSession(2, RemoteStorageItem(name = "second", address = "second", port = 21, userId = "u", password = "p", settings = RemoteProtocolSettings.Ftp("UTF-8")))
    }
}
