package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.repository.ProgressRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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
    private val progressRepo = RecordingProgressRepository()
    /** 검증 대상 오케스트레이터. */
    private val orchestrator = FileOperationOrchestrator(NoOpNotifier, progressRepo, scope)
    /** 열릴 때까지 작업을 멈춰 두는 신호. */
    private val gate = CompletableDeferred<Unit>()

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `진행 표시가 있는 작업은 진행률을 보고하기 전부터 등록된다`() {
        orchestrator.launch("done", FileOperationType.COPY, onRefresh = {}) { _ ->
            gate.await()
            IoResult.Done
        }

        assertEquals(mapOf(1 to ProgressState(operationType = FileOperationType.COPY)), progressRepo.progressMap.value)
    }

    @Test
    fun `나중에 시작한 작업이 먼저 진행률을 보고해도 시작한 순서대로 등록된다`() {
        orchestrator.launch("done", FileOperationType.COPY, onRefresh = {}) { onProgress ->
            gate.await()
            onProgress(ProgressState(progress = 0))
            IoResult.Done
        }
        orchestrator.launch("done", FileOperationType.UPLOAD, onRefresh = {}) { onProgress ->
            onProgress(ProgressState(progress = 0))
            gate.await()
            IoResult.Done
        }

        gate.complete(Unit)

        assertEquals(listOf(1, 2), progressRepo.registrationOrder)
    }

    @Test
    fun `순간 작업은 등록하지 않는다`() {
        orchestrator.launch("done", onRefresh = {}) { _ ->
            gate.await()
            IoResult.Done
        }

        assertTrue(progressRepo.progressMap.value.isEmpty())
    }

    /** 진행 상태를 보관하고, 각 작업이 처음 등록된 순서를 기록하는 테스트용 진행 저장소. */
    private class RecordingProgressRepository : ProgressRepository {
        val registrationOrder = mutableListOf<Int>()
        override val progressMap = MutableStateFlow<Map<Int, ProgressState>>(emptyMap())
        override val operationErrors: SharedFlow<String> = MutableSharedFlow()
        override val operationCompletions: SharedFlow<String> = MutableSharedFlow()
        override fun setProgressState(instanceId: Int, state: ProgressState) {
            if (instanceId !in progressMap.value) registrationOrder += instanceId
            progressMap.value = progressMap.value + (instanceId to state)
        }
        override fun removeProgress(instanceId: Int) {
            progressMap.value = progressMap.value - instanceId
        }
        override fun emitError(message: String) {}
        override fun emitCompletion(message: String) {}
    }

    /** 알림을 표시하지 않는 테스트용 알림기. */
    private object NoOpNotifier : FileOperationNotifier {
        override fun progressNoti(state: ProgressState, instanceId: Int) {}
        override fun completedNotification(title: String, message: String, operationType: FileOperationType?, notifId: Int) {}
        override fun cancelNotification(notifId: Int) {}
    }
}
