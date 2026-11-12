package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.ConflictAnswer
import com.todokanai.composepracticenew.model.ConflictChoice
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.WriteConflict
import com.todokanai.composepracticenew.model.WriteConflictKind
import com.todokanai.composepracticenew.operation.ConflictMemory
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.repository.ConflictQuestionAsker
import com.todokanai.composepracticenew.repository.ProgressRepository
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * 쓰기 충돌을 작업 도중 묻는 흐름([FileOperationOrchestrator.launchResolvingConflicts])을 검증하는 단위 테스트(#316).
 * 답대로 처리하는지, "나머지에도 적용"을 겹침 종류별·기억 범위별로 쓰는지, 취소·무응답을 각각 취소·실패로 끝내는지, 묻는 동안 알림을 바꾸는지 확인한다.
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

        orchestrator(asker).launchResolvingConflicts("done", FileOperationType.COPY, onRefresh = {}) { _, resolve ->
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

        orchestrator(asker).launchResolvingConflicts("done", FileOperationType.COPY, onRefresh = {}) { _, resolve ->
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
    fun `기억 범위를 함께 쓰는 작업은 앞 작업에서 나머지에도 적용한 답을 쓴다`() = runTest {
        val asker = ScriptedAsker(ConflictAnswer.Choose(ConflictChoice.SKIP, applyToRest = true))
        val memory = ConflictMemory()
        val choices = mutableListOf<ConflictChoice>()
        val orchestrator = orchestrator(asker)

        repeat(2) {
            orchestrator.launchResolvingConflicts("done", FileOperationType.DOWNLOAD, onRefresh = {}, memory = memory) { _, resolve ->
                choices += resolve.resolve(fileConflict)
                IoResult.Done
            }
        }
        runCurrent()

        assertEquals(listOf(ConflictChoice.SKIP, ConflictChoice.SKIP), choices)
        assertEquals(1, asker.asked.size)
    }

    @Test
    fun `기억 범위가 다른 작업은 다시 묻는다`() = runTest {
        val asker = ScriptedAsker(
            ConflictAnswer.Choose(ConflictChoice.SKIP, applyToRest = true),
            ConflictAnswer.Choose(ConflictChoice.SKIP, applyToRest = true),
        )
        val orchestrator = orchestrator(asker)

        repeat(2) {
            orchestrator.launchResolvingConflicts("done", FileOperationType.COPY, onRefresh = {}) { _, resolve ->
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

        orchestrator(asker).launchResolvingConflicts("done", FileOperationType.COPY, onRefresh = {}) { _, resolve ->
            resolve.resolve(fileConflict)
            IoResult.Done
        }
        runCurrent()

        assertEquals(listOf("복사 취소 — 이미 복사한 항목은 그대로 둡니다"), notices.completions)
        assertEquals(emptyList<String>(), notices.failures)
    }

    @Test
    fun `질문에 6시간 동안 답이 없으면 작업을 실패로 끝낸다`() = runTest {
        orchestrator(NeverAnsweringAsker).launchResolvingConflicts("done", FileOperationType.MOVE, onRefresh = {}) { _, resolve ->
            resolve.resolve(fileConflict)
            IoResult.Done
        }

        advanceTimeBy((6.hours - 1.seconds).inWholeMilliseconds)
        runCurrent()
        assertEquals(emptyList<String>(), notices.failures)

        advanceTimeBy(2.seconds.inWholeMilliseconds)
        runCurrent()
        assertEquals(
            listOf("이동 중단: 'a.txt' 충돌 질문에 6시간 동안 답이 없었습니다 — 이미 처리한 항목은 그대로 둡니다"),
            notices.failures
        )
        assertEquals(emptyList<String>(), notices.completions)
    }

    @Test
    fun `종류가 엇갈린 겹침에 진행으로 답하면 구현 오류로 실패한다`() = runTest {
        val asker = ScriptedAsker(ConflictAnswer.Choose(ConflictChoice.PROCEED, applyToRest = false))

        orchestrator(asker).launchResolvingConflicts("done", FileOperationType.COPY, onRefresh = {}) { _, resolve ->
            resolve.resolve(mismatch)
            IoResult.Done
        }
        runCurrent()

        assertEquals(listOf("TYPE_MISMATCH 겹침은 건너뛰기만 할 수 있습니다"), notices.failures)
    }

    @Test
    fun `묻는 동안 알림을 답을 기다리는 상태로 바꾸고 답한 뒤 마지막 진행 상태로 되돌린다`() = runTest {
        val asker = ScriptedAsker(ConflictAnswer.Choose(ConflictChoice.SKIP, applyToRest = false))
        val progress = ProgressState(progress = 40)

        orchestrator(asker).launchResolvingConflicts("done", FileOperationType.UNZIP, onRefresh = {}) { onProgress, resolve ->
            onProgress(progress)
            resolve.resolve(fileConflict)
            IoResult.Done
        }
        runCurrent()

        assertEquals(
            listOf("progress 40", "awaiting UNZIP", "progress 40"),
            notifier.events.take(3)
        )
    }

    /** [TestScope]의 가상 시간으로 작업을 실행하는 오케스트레이터를 만든다. */
    private fun TestScope.orchestrator(asker: ConflictQuestionAsker) =
        FileOperationOrchestrator(notifier, NoOpProgressRepository, notices, GrantedProtection, asker, backgroundScope)

    /**
     * 정해 둔 답을 차례로 돌려주고 받은 질문을 기록하는 질문 통로.
     * @param answers 차례로 돌려줄 답
     */
    private class ScriptedAsker(vararg answers: ConflictAnswer) : ConflictQuestionAsker {
        /** 아직 돌려주지 않은 답. */
        private val remaining = ArrayDeque(answers.toList())
        /** 받은 질문의 겹침(받은 순서). */
        val asked = mutableListOf<WriteConflict>()

        override suspend fun ask(operationType: FileOperationType, conflict: WriteConflict): ConflictAnswer {
            asked += conflict
            return remaining.removeFirst()
        }
    }

    /** 답하지 않는 질문 통로. 기다림이 끝날 때(시간 초과·취소)까지 돌아오지 않는다. */
    private object NeverAnsweringAsker : ConflictQuestionAsker {
        override suspend fun ask(operationType: FileOperationType, conflict: WriteConflict): ConflictAnswer = awaitCancellation()
    }

    /** 진행·대기 알림을 차례로 기록하는 알림기. */
    private class RecordingNotifier : FileOperationNotifier {
        /** 받은 알림을 "progress 진행률" / "awaiting 작업 종류" 형태로 기록한 것. */
        val events = mutableListOf<String>()
        override fun progressNoti(state: ProgressState, instanceId: Int) { events += "progress ${state.progress}" }
        override fun awaitingAnswerNoti(operationType: FileOperationType, instanceId: Int) { events += "awaiting $operationType" }
        override fun completedNotification(message: String, instanceId: Int) {}
        override fun cancelNotification(instanceId: Int) {}
    }

    /** 진행 상태를 기록하지 않는 진행 저장소. */
    private object NoOpProgressRepository : ProgressRepository {
        override val progressMap: StateFlow<Map<Int, ProgressState>> = MutableStateFlow(emptyMap())
        override fun setProgressState(instanceId: Int, state: ProgressState) {}
        override fun removeProgress(instanceId: Int) {}
    }
}
