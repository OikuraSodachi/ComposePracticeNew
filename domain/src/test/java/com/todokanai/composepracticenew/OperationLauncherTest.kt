package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.repository.ProgressRepository
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
 * [FileOperationOrchestrator.launch]가 작업 결과를 완료·실패 처리로 나누고, 구현 오류 하나로 작업 스코프가 무너지지 않게 그 작업만 실패로 끝내며,
 * 진행·완료·취소 알림을 모두 그 작업에 발급한 인스턴스 ID로 보내는지 검증하는 단위 테스트.
 * 작업이 일시 중단하지 않으므로 Unconfined 스코프에서 호출 즉시 끝난다. 첫 작업의 ID는 1이고 시작 순서대로 1씩 커진다.
 */
class OperationLauncherTest {

    /** 작업을 실행할 스코프. */
    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
    /** 완료·오류 메시지와 진행 상태 제거를 기록하는 진행 저장소. */
    private val progressRepo = RecordingProgressRepository()

    /** 넣은 실패·완료 메시지를 기록하는 메시지 저장소. */
    private val notices = RecordingOperationNotices()
    /** 받은 알림 요청을 기록하는 알림기. */
    private val notifier = RecordingNotifier()
    /** 검증 대상. */
    private val orchestrator = FileOperationOrchestrator(notifier, progressRepo, notices, scope)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `성공하면 완료를 알리고 진행 상태를 지운다`() {
        launch { IoResult.Done }

        assertEquals(listOf("done"), notices.completions)
        assertTrue(notices.failures.isEmpty())
        assertEquals(listOf(1), progressRepo.removed)
    }

    @Test
    fun `실패 결과는 그 메시지로 실패를 알리고 진행 상태를 지운다`() {
        launch { IoResult.ConnectionLost("끊김") }

        assertEquals(listOf("끊김"), notices.failures)
        assertTrue(notices.completions.isEmpty())
        assertEquals(listOf(1), progressRepo.removed)
    }

    @Test
    fun `메시지가 없는 실패는 알 수 없는 오류로 알린다`() {
        launch { IoResult.ItemFailed(null) }

        assertEquals(listOf("알 수 없는 오류"), notices.failures)
    }

    @Test
    fun `구현 오류는 그 작업만 실패로 끝내고 다음 작업은 계속 실행한다`() {
        launch { throw IllegalStateException("구현 오류") }
        launch { IoResult.Done }

        assertEquals(listOf("구현 오류"), notices.failures)
        assertEquals(listOf("done"), notices.completions)
    }

    @Test
    fun `진행 알림과 완료 알림은 그 작업에 발급한 ID를 가리킨다`() {
        launch(FileOperationType.COPY) { onProgress -> onProgress(ProgressState(progress = 50)); IoResult.Done }
        launch(FileOperationType.MOVE) { onProgress -> onProgress(ProgressState(progress = 50)); IoResult.Done }

        // 완료 알림이 같은 ID여야 진행 알림 자리를 대신한다 — 다르면 끝난 작업의 진행 알림이 남는다
        assertEquals(listOf("progress 1", "completed 1", "progress 2", "completed 2"), notifier.calls)
    }

    @Test
    fun `실패하면 그 작업에 발급한 ID의 알림을 지운다`() {
        launch { IoResult.Done }
        launch(FileOperationType.COPY) { onProgress -> onProgress(ProgressState(progress = 50)); IoResult.ItemFailed("실패") }

        assertEquals(listOf("completed 1", "progress 2", "cancel 2"), notifier.calls)
    }

    /**
     * [block]을 작업 하나로 실행한다.
     * @param operationType 작업 종류. null이면 순간 작업
     * @param block 진행 보고 콜백을 받아 결과를 돌려주는 작업
     */
    private fun launch(
        operationType: FileOperationType? = null,
        block: suspend (onProgress: (ProgressState) -> Unit) -> IoResult<Unit>
    ) = orchestrator.launch(completionMessage = "done", operationType = operationType, onRefresh = {}, block = block)

    /** 지운 진행 상태를 기록하는 테스트용 진행 저장소. */
    private class RecordingProgressRepository : ProgressRepository {
        val removed = mutableListOf<Int>()
        override val progressMap: StateFlow<Map<Int, ProgressState>> = MutableStateFlow(emptyMap())
        override fun setProgressState(instanceId: Int, state: ProgressState) {}
        override fun removeProgress(instanceId: Int) { removed += instanceId }
    }

    /** 받은 진행·완료·취소 알림 요청을 "종류 인스턴스ID"로 받은 순서대로 기록하는 테스트용 알림기. */
    private class RecordingNotifier : FileOperationNotifier {
        val calls = mutableListOf<String>()
        override fun progressNoti(state: ProgressState, instanceId: Int) { calls += "progress $instanceId" }
        override fun completedNotification(message: String, instanceId: Int) { calls += "completed $instanceId" }
        override fun cancelNotification(instanceId: Int) { calls += "cancel $instanceId" }
    }
}
