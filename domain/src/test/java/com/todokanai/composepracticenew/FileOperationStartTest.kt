package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.ProgressPhase
import com.todokanai.composepracticenew.model.OperationKind
import com.todokanai.composepracticenew.model.QuestionKind
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.InstantOperation
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.repository.ProgressReporter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FileOperationOrchestrator.launch]가 진행 표시가 있는 작업을 시작하는 즉시 progressMap에 등록해,
 * progressMap에 나타나는 순서가 작업을 시작한 순서와 같은지 검증하는 단위 테스트.
 * 작업은 [gate]가 열릴 때까지 멈춰 있어 진행 중인 상태를 그대로 볼 수 있다.
 */
class FileOperationStartTest {

    /** 작업을 실행할 스코프. Unconfined라 작업은 처음 멈추는 지점까지 launch 호출 안에서 바로 돈다. */
    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
    /** 진행 상태와 작업이 처음 등록된 순서를 기록하는 진행 저장소. */
    private val progressReporter = RecordingProgressReporter()
    /** 검증 대상 오케스트레이터. */
    private val orchestrator = FileOperationOrchestrator(NoOpNotifier, progressReporter, RecordingOperationNotices(), GrantedProtection, UnexpectedQuestions, scope)
    /** 열릴 때까지 작업을 멈춰 두는 신호. */
    private val gate = CompletableDeferred<Unit>()

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `진행 표시가 있는 작업은 진행률을 보고하기 전부터 등록된다`() {
        orchestrator.launch(FileOperationType.COPY, onRefresh = {}) { _ ->
            gate.await()
            IoResult.Done
        }

        assertEquals(mapOf(1 to ProgressState(FileOperationType.COPY, ProgressPhase.Starting)), progressReporter.progressMap.value)
    }

    @Test
    fun `나중에 시작한 작업이 먼저 진행률을 보고해도 시작한 순서대로 등록된다`() {
        orchestrator.launch(FileOperationType.COPY, onRefresh = {}) { onProgress ->
            gate.await()
            onProgress(ProgressPhase.Starting)
            IoResult.Done
        }
        orchestrator.launch(FileOperationType.UPLOAD, onRefresh = {}) { onProgress ->
            onProgress(ProgressPhase.Starting)
            gate.await()
            IoResult.Done
        }

        gate.complete(Unit)

        assertEquals(listOf(1, 2), progressReporter.registrationOrder)
    }

    @Test
    fun `순간 작업은 등록하지 않는다`() {
        orchestrator.launchInstant(OperationKind.Instant(InstantOperation.MAKE_FOLDER, "new"), onRefresh = {}) {
            gate.await()
            IoResult.Done
        }

        assertTrue(progressReporter.progressMap.value.isEmpty())
    }

    /** 진행 상태를 보관하고, 각 작업이 처음 등록된 순서를 기록하는 테스트용 진행 상태 기록기. */
    private class RecordingProgressReporter : ProgressReporter {
        val registrationOrder = mutableListOf<Int>()
        val progressMap = MutableStateFlow<Map<Int, ProgressState>>(emptyMap())
        override fun setProgressState(instanceId: Int, state: ProgressState) {
            if (instanceId !in progressMap.value) registrationOrder += instanceId
            progressMap.value = progressMap.value + (instanceId to state)
        }
        override fun removeProgress(instanceId: Int) {
            progressMap.value = progressMap.value - instanceId
        }
    }

    /** 알림을 표시하지 않는 테스트용 알림기. */
    private object NoOpNotifier : FileOperationNotifier {
        override fun progressNotification(state: ProgressState, instanceId: Int) {}
        override fun completedNotification(kind: OperationKind, instanceId: Int) {}
        override fun cancelNotification(instanceId: Int) {}
        override fun awaitingAnswerNotification(operationType: FileOperationType, kind: QuestionKind, instanceId: Int) {}
    }
}
