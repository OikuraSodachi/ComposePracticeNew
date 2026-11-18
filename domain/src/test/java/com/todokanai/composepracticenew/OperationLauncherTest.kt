package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.OperationKind
import com.todokanai.composepracticenew.model.InstantOperation
import com.todokanai.composepracticenew.model.QuestionKind
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.NoticeMessage
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.repository.ProgressReporter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FileOperationOrchestrator.launch]가 작업 결과를 완료·실패 처리로 나누고, 작업의 구현 오류 하나로 작업 스코프가 무너지지 않게 그 작업만 실패로 끝내며,
 * 완료·실패·취소 보고 중 예외는 작업 실패로 다시 알리지 않고 스코프에 전파하며,
 * 진행·완료·취소 알림을 모두 그 작업에 발급한 인스턴스 ID로 보내며, 취소 요청([FileOperationOrchestrator.cancel])으로 작업을 멈추고 취소를 알리는지 검증하는 단위 테스트.
 * 작업이 일시 중단하지 않으므로 Unconfined 스코프에서 호출 즉시 끝난다. 첫 작업의 ID는 1이고 시작 순서대로 1씩 커진다.
 */
class OperationLauncherTest {

    /** 작업을 실행할 스코프. */
    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
    /** 완료·오류 메시지와 진행 상태 제거를 기록하는 진행 저장소. */
    private val progressReporter = RecordingProgressReporter()

    /** 넣은 실패·완료 메시지를 기록하는 메시지 저장소. */
    private val notices = RecordingOperationNotices()
    /** 받은 알림 요청을 기록하는 알림기. */
    private val notifier = RecordingNotifier()
    /** 검증 대상. */
    private val orchestrator = FileOperationOrchestrator(notifier, progressReporter, notices, GrantedProtection, UnexpectedQuestions, scope)

    /** 작업 코루틴에서 전파된 예외. 받은 순서대로 쌓는다. */
    private val propagated = mutableListOf<Throwable>()
    /** 작업에서 전파된 예외를 [propagated]에 모으는 스코프 — 앱 수명 스코프처럼 한 작업의 실패가 다른 작업을 취소하지 않는다. */
    private val propagatingScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, e -> propagated += e })
    /** [propagatingScope]에서 작업을 실행하는 검증 대상. 보고 중 예외의 전파를 볼 때 쓴다. */
    private val propagatingOrchestrator = FileOperationOrchestrator(notifier, progressReporter, notices, GrantedProtection, UnexpectedQuestions, propagatingScope)

    /** 작업 종류를 따로 보지 않는 시험에서 실행하는 순간 작업. */
    private val instant = OperationKind.Instant(InstantOperation.MAKE_FOLDER, "new")

    @After
    fun tearDown() {
        scope.cancel()
        propagatingScope.cancel()
    }

    @Test
    fun `성공하면 완료를 알리고 진행 상태를 지운다`() {
        launch { IoResult.Done }

        assertEquals(listOf(NoticeMessage.Completed(instant)), notices.completions)
        assertTrue(notices.failures.isEmpty())
        assertEquals(listOf(1), progressReporter.removed)
    }

    @Test
    fun `실패 결과는 그 메시지로 실패를 알리고 진행 상태를 지운다`() {
        launch { IoResult.ConnectionLost(FailureReason.IoFailed("끊김")) }

        assertEquals(listOf(NoticeMessage.OperationFailed(instant, IoResult.ConnectionLost(FailureReason.IoFailed("끊김")))), notices.failures)
        assertTrue(notices.completions.isEmpty())
        assertEquals(listOf(1), progressReporter.removed)
    }

    @Test
    fun `메시지가 없는 실패는 메시지 없는 실패로 알린다`() {
        launch { IoResult.ItemFailed(null) }

        assertEquals(listOf(NoticeMessage.OperationFailed(instant, IoResult.ItemFailed(null))), notices.failures)
    }

    @Test
    fun `구현 오류는 그 작업만 실패로 끝내고 다음 작업은 계속 실행한다`() {
        launch { throw IllegalStateException("구현 오류") }
        launch { IoResult.Done }

        assertEquals(listOf(NoticeMessage.OperationFailed(instant, IoResult.ItemFailed(FailureReason.Unexpected("구현 오류")))), notices.failures)
        assertEquals(listOf(NoticeMessage.Completed(instant)), notices.completions)
    }

    @Test
    fun `완료를 알린 뒤 보고 중 예외는 실패로 또 알리지 않고 스코프에 전파한다`() {
        launchFailingRefresh { IoResult.Done }

        assertEquals(listOf(NoticeMessage.Completed(instant)), notices.completions)
        assertTrue(notices.failures.isEmpty())
        assertEquals(listOf("completed 1"), notifier.calls)
        assertEquals(listOf("갱신 오류"), propagated.map { it.message })
        assertEquals(listOf(1), progressReporter.removed)
    }

    @Test
    fun `실패를 알린 뒤 보고 중 예외는 실패로 또 알리지 않고 스코프에 전파한다`() {
        launchFailingRefresh { IoResult.ItemFailed(FailureReason.IoFailed("실패")) }

        assertEquals(listOf(NoticeMessage.OperationFailed(instant, IoResult.ItemFailed(FailureReason.IoFailed("실패")))), notices.failures)
        assertTrue(notices.completions.isEmpty())
        assertEquals(listOf("cancel 1"), notifier.calls)
        assertEquals(listOf("갱신 오류"), propagated.map { it.message })
        assertEquals(listOf(1), progressReporter.removed)
    }

    @Test
    fun `취소를 알린 뒤 보고 중 예외는 실패로 알리지 않고 스코프에 전파한다`() {
        val gate = CompletableDeferred<Unit>()
        launchFailingRefresh(FileOperationType.COPY) { gate.await(); IoResult.Done }

        propagatingOrchestrator.cancel(1)

        assertEquals(listOf(NoticeMessage.Cancelled(OperationKind.Tracked(FileOperationType.COPY))), notices.completions)
        assertTrue(notices.failures.isEmpty())
        assertEquals(listOf("cancel 1"), notifier.calls)
        assertEquals(listOf("갱신 오류"), propagated.map { it.message })
        assertEquals(listOf(1), progressReporter.removed)
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
        launch(FileOperationType.COPY) { onProgress -> onProgress(ProgressState(progress = 50)); IoResult.ItemFailed(FailureReason.IoFailed("실패")) }

        assertEquals(listOf("completed 1", "progress 2", "cancel 2"), notifier.calls)
    }

    @Test
    fun `일시 중단한 작업을 취소하면 완료·실패 대신 취소를 알리고 진행 알림과 진행 상태를 지운다`() {
        val gate = CompletableDeferred<Unit>()
        launch(FileOperationType.COPY) { gate.await(); IoResult.Done }

        orchestrator.cancel(1)

        assertEquals(listOf(NoticeMessage.Cancelled(OperationKind.Tracked(FileOperationType.COPY))), notices.completions)
        assertTrue(notices.failures.isEmpty())
        assertEquals(listOf("cancel 1"), notifier.calls)
        assertEquals(listOf(1), progressReporter.removed)
    }

    @Test
    fun `취소된 뒤의 진행 보고는 취소를 던져 작업을 멈춘다`() {
        var reachedAfterProgress = false
        launch(FileOperationType.UPLOAD) { onProgress ->
            orchestrator.cancel(1)
            onProgress(ProgressState(progress = 10))
            reachedAfterProgress = true
            IoResult.Done
        }

        assertFalse(reachedAfterProgress)
        assertEquals(listOf(NoticeMessage.Cancelled(OperationKind.Tracked(FileOperationType.UPLOAD))), notices.completions)
        // 취소를 확인한 뒤의 진행은 보고하지 않는다
        assertEquals(listOf("cancel 1"), notifier.calls)
    }

    @Test
    fun `작업이 끝난 뒤에 온 취소 요청은 결과 알림과 목록 갱신을 바꾸지 않는다`() {
        val refreshGate = CompletableDeferred<Unit>()
        var refreshed = false
        orchestrator.launch(operationType = FileOperationType.COPY, onRefresh = { refreshGate.await(); refreshed = true }) { IoResult.Done }

        orchestrator.cancel(1)
        refreshGate.complete(Unit)

        assertEquals(listOf(NoticeMessage.Completed(OperationKind.Tracked(FileOperationType.COPY))), notices.completions)
        assertTrue(refreshed)
        assertEquals(listOf("completed 1"), notifier.calls)
    }

    @Test
    fun `끝난 작업이나 없는 작업의 취소 요청은 무시한다`() {
        launch { IoResult.Done }

        orchestrator.cancel(1)
        orchestrator.cancel(99)

        assertEquals(listOf(NoticeMessage.Completed(instant)), notices.completions)
    }

    /**
     * [block]을 순간 작업([instant]) 하나로 실행한다.
     * @param block 결과를 돌려주는 작업
     */
    private fun launch(block: suspend () -> IoResult<Unit>) = orchestrator.launchInstant(instant, onRefresh = {}, block = block)

    /**
     * [block]을 진행 표시가 있는 작업 하나로 실행한다.
     * @param operationType 작업 종류
     * @param block 진행 보고 콜백을 받아 결과를 돌려주는 작업
     */
    private fun launch(
        operationType: FileOperationType,
        block: suspend (onProgress: (ProgressState) -> Unit) -> IoResult<Unit>
    ) = orchestrator.launch(operationType = operationType, onRefresh = {}, block = block)

    /**
     * 목록 갱신이 언제나 던지는 순간 작업([instant]) 하나로 [block]을 [propagatingOrchestrator]에서 실행한다.
     * @param block 결과를 돌려주는 작업
     */
    private fun launchFailingRefresh(block: suspend () -> IoResult<Unit>) =
        propagatingOrchestrator.launchInstant(instant, onRefresh = { throw IllegalStateException("갱신 오류") }, block = block)

    /**
     * 목록 갱신이 언제나 던지는 진행 표시가 있는 작업 하나로 [block]을 [propagatingOrchestrator]에서 실행한다.
     * @param operationType 작업 종류
     * @param block 결과를 돌려주는 작업
     */
    private fun launchFailingRefresh(operationType: FileOperationType, block: suspend () -> IoResult<Unit>) =
        propagatingOrchestrator.launch(
            operationType = operationType,
            onRefresh = { throw IllegalStateException("갱신 오류") }
        ) { block() }

    /** 지운 진행 상태를 기록하는 테스트용 진행 저장소. */
    private class RecordingProgressReporter : ProgressReporter {
        val removed = mutableListOf<Int>()
        override fun setProgressState(instanceId: Int, state: ProgressState) {}
        override fun removeProgress(instanceId: Int) { removed += instanceId }
    }

    /** 받은 진행·완료·취소 알림 요청을 "종류 인스턴스ID"로 받은 순서대로 기록하는 테스트용 알림기. */
    private class RecordingNotifier : FileOperationNotifier {
        val calls = mutableListOf<String>()
        override fun progressNoti(state: ProgressState, instanceId: Int) { calls += "progress $instanceId" }
        override fun completedNotification(kind: OperationKind, instanceId: Int) { calls += "completed $instanceId" }
        override fun cancelNotification(instanceId: Int) { calls += "cancel $instanceId" }
        override fun awaitingAnswerNoti(operationType: FileOperationType, kind: QuestionKind, instanceId: Int) {}
    }
}
