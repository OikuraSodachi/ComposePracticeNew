package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.service.FileOperationServicePolicy
import com.todokanai.composepracticenew.usecase.ProgressUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 파일 작업 보호 서비스가 진행 상태를 따르는지 — 작업이 생길 때 시작을 요청하고, 작업이 모두 끝나면 멈추는지 — 검증하는 단위 테스트.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FileOperationServicePolicyTest {

    /** 진행 상태를 테스트가 직접 넣고 빼는 진행 저장소. 빈 상태로 시작한다. */
    private val progressRepo = MutableProgressRepository()

    /** 검증 대상 규칙. */
    private val policy = FileOperationServicePolicy(ProgressUseCase(progressRepo))

    @Test
    fun `작업이 없을 때는 시작을 요청하지 않는다`() = runTest(UnconfinedTestDispatcher()) {
        val requests = collectStartRequests()

        assertEquals(0, requests.size)
    }

    @Test
    fun `작업이 진행 중인 동안 작업이 더 생겨도 다시 요청하지 않는다`() = runTest(UnconfinedTestDispatcher()) {
        val requests = collectStartRequests()

        progressRepo.setProgressState(1, copying())
        progressRepo.setProgressState(2, copying())

        assertEquals(1, requests.size)
    }

    @Test
    fun `작업이 모두 끝난 뒤 새 작업이 생기면 다시 시작을 요청한다`() = runTest(UnconfinedTestDispatcher()) {
        val requests = collectStartRequests()

        progressRepo.setProgressState(1, copying())
        progressRepo.removeProgress(1)
        progressRepo.setProgressState(2, copying())

        assertEquals(2, requests.size)
    }

    @Test
    fun `작업이 남아 있는 동안은 멈추지 않고 모두 끝나면 멈춘다`() = runTest(UnconfinedTestDispatcher()) {
        progressRepo.setProgressState(1, copying())
        progressRepo.setProgressState(2, copying())
        val stop = launch { policy.awaitStop() }

        progressRepo.removeProgress(1)
        assertFalse(stop.isCompleted)

        progressRepo.removeProgress(2)
        assertTrue(stop.isCompleted)
    }

    @Test
    fun `서비스가 시작되기 전에 작업이 끝났으면 바로 멈춘다`() = runTest(UnconfinedTestDispatcher()) {
        val stop = launch { policy.awaitStop() }

        assertTrue(stop.isCompleted)
    }

    /** 시작 요청을 테스트가 끝날 때까지 모아 담는 목록을 돌려준다. */
    private fun TestScope.collectStartRequests(): List<Unit> {
        val requests = mutableListOf<Unit>()
        backgroundScope.launch { policy.startRequests.toList(requests) }
        return requests
    }

    private fun copying() = ProgressState(progress = 0, operationType = FileOperationType.COPY)
}
