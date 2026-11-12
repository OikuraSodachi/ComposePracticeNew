package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.operation.OperationProtection
import com.todokanai.composepracticenew.repository.ProgressRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FileOperationOrchestrator.launch]가 진행 표시가 있는 작업을 보호를 받은 뒤에만 시작하고,
 * 보호가 거부되면 작업을 실행하지 않고 실패로 끝내는지 검증하는 단위 테스트.
 */
class OperationProtectionGateTest {

    /** 작업을 실행할 스코프. Unconfined라 작업은 처음 멈추는 지점까지 launch 호출 안에서 바로 돈다. */
    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
    /** 진행 상태를 보관하는 진행 저장소. */
    private val progressRepo = MapProgressRepository()
    /** 작업의 완료·실패 메시지를 기록하는 알림 저장소. */
    private val notices = RecordingOperationNotices()
    /** 테스트가 결과를 정하는 작업 보호. */
    private val protection = GatedProtection()
    /** 검증 대상 오케스트레이터. */
    private val orchestrator = FileOperationOrchestrator(NoOpNotifier, progressRepo, notices, protection, UnexpectedConflictQuestions, scope)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `보호를 받기 전에는 작업을 실행하지 않고 받은 뒤에 실행한다`() {
        var ran = false
        orchestrator.launch("done", FileOperationType.COPY, onRefresh = {}) { _ ->
            ran = true
            IoResult.Done
        }
        assertFalse(ran)
        assertEquals(1, progressRepo.progressMap.value.size)

        protection.result.complete(IoResult.Done)

        assertTrue(ran)
        assertEquals(listOf("done"), notices.completions)
    }

    @Test
    fun `보호가 거부되면 작업을 실행하지 않고 실패로 끝낸다`() {
        var ran = false
        orchestrator.launch("done", FileOperationType.COPY, onRefresh = {}) { _ ->
            ran = true
            IoResult.Done
        }

        protection.result.complete(IoResult.ItemFailed("보호 거부"))

        assertFalse(ran)
        assertEquals(listOf("보호 거부"), notices.failures)
        assertTrue(notices.completions.isEmpty())
        assertTrue(progressRepo.progressMap.value.isEmpty())
    }

    @Test
    fun `순간 작업은 보호를 요청하지 않고 바로 실행한다`() {
        var ran = false
        orchestrator.launch("done", onRefresh = {}) { _ ->
            ran = true
            IoResult.Done
        }

        assertTrue(ran)
        assertEquals(0, protection.requests)
    }

    /** [result]가 정해질 때까지 기다렸다가 그 결과를 돌려주는 테스트용 작업 보호. 요청 수를 센다. */
    private class GatedProtection : OperationProtection {
        /** 테스트가 정하는 보호 결과. 정해질 때까지 acquire가 기다린다. */
        val result = CompletableDeferred<IoResult<Unit>>()
        /** acquire가 불린 횟수. */
        var requests = 0
        override suspend fun acquire(): IoResult<Unit> {
            requests++
            return result.await()
        }
    }

    /** 진행 상태를 맵으로 보관하는 테스트용 진행 저장소. */
    private class MapProgressRepository : ProgressRepository {
        override val progressMap = MutableStateFlow<Map<Int, ProgressState>>(emptyMap())
        override fun setProgressState(instanceId: Int, state: ProgressState) {
            progressMap.value = progressMap.value + (instanceId to state)
        }
        override fun removeProgress(instanceId: Int) {
            progressMap.value = progressMap.value - instanceId
        }
    }

    /** 알림을 표시하지 않는 테스트용 알림기. */
    private object NoOpNotifier : FileOperationNotifier {
        override fun progressNoti(state: ProgressState, instanceId: Int) {}
        override fun completedNotification(message: String, instanceId: Int) {}
        override fun cancelNotification(instanceId: Int) {}
        override fun awaitingAnswerNoti(operationType: FileOperationType, instanceId: Int) {}
    }
}
