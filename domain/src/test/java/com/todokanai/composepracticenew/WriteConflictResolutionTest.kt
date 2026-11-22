package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.ProgressPhase
import com.todokanai.composepracticenew.model.ArchivePasswordPrompt
import com.todokanai.composepracticenew.model.ConflictAnswer
import com.todokanai.composepracticenew.model.ConflictChoice
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.NoticeMessage
import com.todokanai.composepracticenew.model.OperationKind
import com.todokanai.composepracticenew.model.PasswordAnswer
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.QuestionKind
import com.todokanai.composepracticenew.model.QuestionRequest
import com.todokanai.composepracticenew.model.WriteConflict
import com.todokanai.composepracticenew.model.WriteConflictKind
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.repository.OperationQuestionAsker
import com.todokanai.composepracticenew.repository.ProgressReporter
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * 쓰기 충돌과 압축 파일 비밀번호를 작업 도중 묻는 흐름([FileOperationOrchestrator.launchAsking])을 검증하는 단위 테스트(#316).
 * 답대로 처리하는지, "나머지에도 적용"을 겹침 종류별·기억 범위별로 쓰는지, 취소·무응답을 각각 취소·실패로 끝내는지, 묻는 동안 알림을 질문 종류에 맞게 바꾸는지 확인한다.
 */
class WriteConflictResolutionTest {

    private val fileConflict = WriteConflict("a.txt", "/dest", WriteConflictKind.FILE)
    private val otherFileConflict = WriteConflict("b.txt", "/dest", WriteConflictKind.FILE)
    private val folderConflict = WriteConflict("dir", "/dest", WriteConflictKind.FOLDER)
    private val mismatch = WriteConflict("c", "/dest", WriteConflictKind.TYPE_MISMATCH)

    private val notices = RecordingOperationNotices()
    private val notifier = RecordingNotifier()

    @Test
    fun `답한 처리 방법을 쓰기 구현에 돌려준다`() = runTest {
        val asker = ScriptedAsker(ConflictAnswer.Choose(ConflictChoice.SKIP, applyToRest = false))
        val choices = mutableListOf<ConflictChoice>()

        orchestrator(asker).launchAsking(FileOperationType.COPY, onRefresh = {}) { _, resolve ->
            choices += resolve.resolve(fileConflict)
            IoResult.Done
        }
        runCurrent()

        assertEquals(listOf(ConflictChoice.SKIP), choices)
        assertEquals(listOf(fileConflict), asker.asked)
    }

    @Test
    fun `나머지에도 적용한 답은 같은 종류의 겹침에만 묻지 않고 쓴다`() = runTest {
        val asker = ScriptedAsker(
            ConflictAnswer.Choose(ConflictChoice.PROCEED, applyToRest = true),
            ConflictAnswer.Choose(ConflictChoice.SKIP, applyToRest = false),
        )
        val choices = mutableListOf<ConflictChoice>()

        orchestrator(asker).launchAsking(FileOperationType.COPY, onRefresh = {}) { _, resolve ->
            choices += resolve.resolve(fileConflict)
            choices += resolve.resolve(otherFileConflict)
            choices += resolve.resolve(folderConflict)
            IoResult.Done
        }
        runCurrent()

        assertEquals(listOf(ConflictChoice.PROCEED, ConflictChoice.PROCEED, ConflictChoice.SKIP), choices)
        assertEquals(listOf(fileConflict, folderConflict), asker.asked)
    }

    @Test
    fun `나머지에도 적용한 답은 다른 작업에서 쓰지 않고 다시 묻는다`() = runTest {
        val asker = ScriptedAsker(
            ConflictAnswer.Choose(ConflictChoice.SKIP, applyToRest = true),
            ConflictAnswer.Choose(ConflictChoice.SKIP, applyToRest = true),
        )
        val orchestrator = orchestrator(asker)

        repeat(2) {
            orchestrator.launchAsking(FileOperationType.COPY, onRefresh = {}) { _, resolve ->
                resolve.resolve(fileConflict)
                IoResult.Done
            }
        }
        runCurrent()

        assertEquals(2, asker.asked.size)
    }

    @Test
    fun `질문에서 취소하면 작업을 취소로 끝낸다`() = runTest {
        val asker = ScriptedAsker(ConflictAnswer.Cancel)

        orchestrator(asker).launchAsking(FileOperationType.COPY, onRefresh = {}) { _, resolve ->
            resolve.resolve(fileConflict)
            IoResult.Done
        }
        runCurrent()

        assertEquals(listOf(NoticeMessage.Cancelled(OperationKind.Tracked(FileOperationType.COPY))), notices.completions)
        assertEquals(emptyList<NoticeMessage>(), notices.failures)
    }

    @Test
    fun `질문에 6시간 동안 답이 없으면 작업을 실패로 끝낸다`() = runTest {
        orchestrator(NeverAnsweringAsker).launchAsking(FileOperationType.MOVE, onRefresh = {}) { _, resolve ->
            resolve.resolve(fileConflict)
            IoResult.Done
        }

        advanceTimeBy((6.hours - 1.seconds).inWholeMilliseconds)
        runCurrent()
        assertEquals(emptyList<NoticeMessage>(), notices.failures)

        advanceTimeBy(2.seconds.inWholeMilliseconds)
        runCurrent()
        assertEquals(
            listOf(NoticeMessage.UnansweredQuestion(FileOperationType.MOVE, QuestionKind.CONFLICT, "a.txt", 6)),
            notices.failures
        )
        assertEquals(emptyList<NoticeMessage>(), notices.completions)
    }

    @Test
    fun `종류가 엇갈린 겹침에 진행으로 답하면 구현 오류로 실패한다`() = runTest {
        val asker = ScriptedAsker(ConflictAnswer.Choose(ConflictChoice.PROCEED, applyToRest = false))

        orchestrator(asker).launchAsking(FileOperationType.COPY, onRefresh = {}) { _, resolve ->
            resolve.resolve(mismatch)
            IoResult.Done
        }
        runCurrent()

        assertEquals(listOf(NoticeMessage.OperationFailed(OperationKind.Tracked(FileOperationType.COPY), IoResult.ItemFailed(FailureReason.Unexpected("TYPE_MISMATCH 겹침은 건너뛰기만 할 수 있습니다")))), notices.failures)
    }

    @Test
    fun `묻는 동안 알림을 답을 기다리는 상태로 바꾸고 답한 뒤 마지막 진행 상태로 되돌린다`() = runTest {
        val asker = ScriptedAsker(ConflictAnswer.Choose(ConflictChoice.SKIP, applyToRest = false))
        val progress = ProgressPhase.Counted(2, 5, "a")

        orchestrator(asker).launchAsking(FileOperationType.UNZIP, onRefresh = {}) { onProgress, resolve ->
            onProgress(progress)
            resolve.resolve(fileConflict)
            IoResult.Done
        }
        runCurrent()

        assertEquals(
            listOf("progress 40", "awaiting UNZIP CONFLICT", "progress 40"),
            notifier.events.take(3)
        )
    }

    @Test
    fun `비밀번호 질문에 입력한 비밀번호를 압축 해제 구현에 돌려준다`() = runTest {
        val asker = ScriptedAsker(passwordAnswers = listOf(PasswordAnswer.Enter("secret")))
        val passwords = mutableListOf<String>()

        orchestrator(asker).launchAsking(FileOperationType.UNZIP, onRefresh = {}) { _, prompts ->
            passwords += prompts.passwordFor("a.zip", ArchivePasswordPrompt.WRONG)
            IoResult.Done
        }
        runCurrent()

        assertEquals(listOf("secret"), passwords)
        assertEquals(listOf("a.zip" to ArchivePasswordPrompt.WRONG), asker.askedPasswords)
        assertEquals(listOf(NoticeMessage.Completed(OperationKind.Tracked(FileOperationType.UNZIP))), notices.completions)
    }

    @Test
    fun `비밀번호 질문에서 취소하면 작업을 취소로 끝낸다`() = runTest {
        val asker = ScriptedAsker(passwordAnswers = listOf(PasswordAnswer.Cancel))

        orchestrator(asker).launchAsking(FileOperationType.UNZIP, onRefresh = {}) { _, prompts ->
            prompts.passwordFor("a.zip", ArchivePasswordPrompt.FIRST)
            IoResult.Done
        }
        runCurrent()

        assertEquals(listOf(NoticeMessage.Cancelled(OperationKind.Tracked(FileOperationType.UNZIP))), notices.completions)
        assertEquals(emptyList<NoticeMessage>(), notices.failures)
    }

    @Test
    fun `비밀번호 질문에 6시간 동안 답이 없으면 압축 파일 이름으로 실패를 알린다`() = runTest {
        orchestrator(NeverAnsweringAsker).launchAsking(FileOperationType.UNZIP, onRefresh = {}) { _, prompts ->
            prompts.passwordFor("a.zip", ArchivePasswordPrompt.FIRST)
            IoResult.Done
        }

        advanceTimeBy((6.hours + 1.seconds).inWholeMilliseconds)
        runCurrent()

        assertEquals(
            listOf(NoticeMessage.UnansweredQuestion(FileOperationType.UNZIP, QuestionKind.ARCHIVE_PASSWORD, "a.zip", 6)),
            notices.failures
        )
    }

    @Test
    fun `비밀번호를 묻는 동안 알림을 비밀번호 대기 상태로 바꾼다`() = runTest {
        val asker = ScriptedAsker(passwordAnswers = listOf(PasswordAnswer.Enter("secret")))

        orchestrator(asker).launchAsking(FileOperationType.UNZIP, onRefresh = {}) { onProgress, prompts ->
            onProgress(ProgressPhase.Counted(1, 10, "a"))
            prompts.passwordFor("a.zip", ArchivePasswordPrompt.FIRST)
            IoResult.Done
        }
        runCurrent()

        assertEquals(listOf("progress 10", "awaiting UNZIP ARCHIVE_PASSWORD", "progress 10"), notifier.events.take(3))
    }

    /** [TestScope]의 가상 시간으로 작업을 실행하는 오케스트레이터를 만든다. */
    private fun TestScope.orchestrator(asker: OperationQuestionAsker) =
        FileOperationOrchestrator(notifier, NoOpProgressReporter, notices, GrantedProtection, asker, backgroundScope)

    /**
     * 정해 둔 답을 차례로 돌려주고 받은 질문을 기록하는 질문 통로.
     * @param answers 차례로 돌려줄 충돌 답
     * @param passwordAnswers 차례로 돌려줄 비밀번호 답
     */
    private class ScriptedAsker(vararg answers: ConflictAnswer, passwordAnswers: List<PasswordAnswer> = emptyList()) : OperationQuestionAsker {
        /** 아직 돌려주지 않은 충돌 답. */
        private val remaining = ArrayDeque(answers.toList())
        /** 아직 돌려주지 않은 비밀번호 답. */
        private val remainingPasswords = ArrayDeque(passwordAnswers)
        /** 받은 질문의 겹침(받은 순서). */
        val asked = mutableListOf<WriteConflict>()
        /** 받은 비밀번호 질문의 (압축 파일 이름, 이유)(받은 순서). */
        val askedPasswords = mutableListOf<Pair<String, ArchivePasswordPrompt>>()

        // 요청의 하위 타입이 답 타입 A를 정한다
        @Suppress("UNCHECKED_CAST")
        override suspend fun <A : Any> ask(operationType: FileOperationType, request: QuestionRequest<A>): A = when (request) {
            is QuestionRequest.Conflict -> {
                asked += request.conflict
                remaining.removeFirst() as A
            }
            is QuestionRequest.ArchivePassword -> {
                askedPasswords += request.archiveName to request.prompt
                remainingPasswords.removeFirst() as A
            }
        }
    }

    /** 답하지 않는 질문 통로. 기다림이 끝날 때(시간 초과·취소)까지 돌아오지 않는다. */
    private object NeverAnsweringAsker : OperationQuestionAsker {
        override suspend fun <A : Any> ask(operationType: FileOperationType, request: QuestionRequest<A>): A = awaitCancellation()
    }

    /** 진행·대기 알림을 차례로 기록하는 알림기. */
    private class RecordingNotifier : FileOperationNotifier {
        /** 받은 알림을 "progress 진행률" / "awaiting 작업 종류" 형태로 기록한 것. */
        val events = mutableListOf<String>()
        override fun progressNotification(state: ProgressState, instanceId: Int) { events += "progress ${(state.phase as? ProgressPhase.Counted)?.percent}" }
        override fun awaitingAnswerNotification(operationType: FileOperationType, kind: QuestionKind, instanceId: Int) { events += "awaiting $operationType $kind" }
        override fun completedNotification(kind: OperationKind, instanceId: Int) {}
        override fun cancelNotification(instanceId: Int) {}
    }

    /** 진행 상태를 기록하지 않는 진행 저장소. */
    private object NoOpProgressReporter : ProgressReporter {
        override fun setProgressState(instanceId: Int, state: ProgressState) {}
        override fun removeProgress(instanceId: Int) {}
    }
}
