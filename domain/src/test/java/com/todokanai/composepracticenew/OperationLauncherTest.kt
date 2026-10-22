package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.operation.OperationCallback
import com.todokanai.composepracticenew.operation.ProgressHandle
import com.todokanai.composepracticenew.operation.launchOperation
import com.todokanai.composepracticenew.repository.ProgressRepository
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
 * [launchOperation]이 작업 결과를 완료·실패 처리로 나누고, 구현 오류 하나로 작업 스코프가 무너지지 않게 그 작업만 실패로 끝내는지 검증하는 단위 테스트.
 * 작업이 일시 중단하지 않으므로 Unconfined 스코프에서 호출 즉시 끝난다.
 */
class OperationLauncherTest {

    /** 작업을 실행할 스코프. */
    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
    /** 완료·오류 메시지와 진행 상태 제거를 기록하는 진행 저장소. */
    private val progressRepo = RecordingProgressRepository()

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `성공하면 완료를 알리고 진행 상태를 지운다`() {
        launch { IoResult.Done }

        assertEquals(listOf("done"), progressRepo.completions)
        assertTrue(progressRepo.errors.isEmpty())
        assertEquals(listOf(INSTANCE_ID), progressRepo.removed)
    }

    @Test
    fun `실패 결과는 그 메시지로 실패를 알리고 진행 상태를 지운다`() {
        launch { IoResult.ConnectionLost("끊김") }

        assertEquals(listOf("끊김"), progressRepo.errors)
        assertTrue(progressRepo.completions.isEmpty())
        assertEquals(listOf(INSTANCE_ID), progressRepo.removed)
    }

    @Test
    fun `메시지가 없는 실패는 알 수 없는 오류로 알린다`() {
        launch { IoResult.ItemFailed(null) }

        assertEquals(listOf("알 수 없는 오류"), progressRepo.errors)
    }

    @Test
    fun `구현 오류는 그 작업만 실패로 끝내고 다음 작업은 계속 실행한다`() {
        launch { throw IllegalStateException("구현 오류") }
        launch { IoResult.Done }

        assertEquals(listOf("구현 오류"), progressRepo.errors)
        assertEquals(listOf("done"), progressRepo.completions)
    }

    /** [work]를 작업 하나로 실행한다. */
    private fun launch(work: suspend () -> IoResult<Unit>) = scope.launchOperation(
        ProgressHandle(INSTANCE_ID, progressRepo),
        OperationCallback(NoOpNotifier, progressRepo, completionMessage = "done") {},
        work
    )

    /** 방출된 완료·오류 메시지와 지운 진행 상태를 기록하는 테스트용 진행 저장소. */
    private class RecordingProgressRepository : ProgressRepository {
        val errors = mutableListOf<String>()
        val completions = mutableListOf<String>()
        val removed = mutableListOf<Int>()
        override val progressMap: StateFlow<Map<Int, ProgressState>> = MutableStateFlow(emptyMap())
        override val operationErrors: SharedFlow<String> = MutableSharedFlow()
        override val operationCompletions: SharedFlow<String> = MutableSharedFlow()
        override fun setProgressState(instanceId: Int, state: ProgressState) {}
        override fun removeProgress(instanceId: Int) { removed += instanceId }
        override fun emitError(message: String) { errors += message }
        override fun emitCompletion(message: String) { completions += message }
    }

    /** 알림을 표시하지 않는 테스트용 알림기. */
    private object NoOpNotifier : FileOperationNotifier {
        override fun progressNoti(state: ProgressState, instanceId: Int) {}
        override fun completedNotification(title: String, message: String, operationType: FileOperationType?, notifId: Int) {}
        override fun cancelNotification(notifId: Int) {}
    }

    private companion object {
        /** 테스트 작업의 인스턴스 ID. */
        const val INSTANCE_ID = 7
    }
}
