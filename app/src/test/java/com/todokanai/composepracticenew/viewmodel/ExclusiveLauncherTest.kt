package com.todokanai.composepracticenew.viewmodel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ExclusiveLauncher]가 작업을 하나만 실행하고, 처리 중에 다시 들어온 입력을 버리며, 작업이 끝나면 다시 받는지 검증하는 단위 테스트. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExclusiveLauncherTest {

    @Test
    fun `작업이 기다리는 동안 들어온 실행은 버린다`() = runTest(UnconfinedTestDispatcher()) {
        val launcher = ExclusiveLauncher(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        var runs = 0

        repeat(2) { launcher.launch { runs++; gate.await() } }

        assertEquals(1, runs)
        assertTrue(launcher.isRunning.value)
    }

    @Test
    fun `작업이 끝나면 다음 실행을 받는다`() = runTest(UnconfinedTestDispatcher()) {
        val launcher = ExclusiveLauncher(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        var runs = 0

        launcher.launch { runs++; gate.await() }
        gate.complete(Unit)
        launcher.launch { runs++ }

        assertEquals(2, runs)
        assertFalse(launcher.isRunning.value)
    }

    @Test
    fun `스코프가 이미 취소돼 본문이 실행되지 않아도 실행 중 표시를 내린다`() = runTest(UnconfinedTestDispatcher()) {
        // 테스트 스케줄러의 디스패처를 쓴다 — 취소된 코루틴도 디스패치되어야 완료되므로, 기본 디스패처면 확인 시점에 아직 끝나지 않는다
        val cancelled = CoroutineScope(Job().apply { cancel() } + UnconfinedTestDispatcher(testScheduler))
        val launcher = ExclusiveLauncher(cancelled)
        var runs = 0

        launcher.launch { runs++ }

        assertEquals(0, runs)
        assertFalse(launcher.isRunning.value)
    }
}
