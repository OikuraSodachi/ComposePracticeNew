package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.service.FileOperationProtection
import com.todokanai.composepracticenew.service.FileOperationServiceController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * [FileOperationProtection]이 작업마다 시작 요청을 보내고, 그 요청의 결과로만 보호를 주는지 검증하는 단위 테스트.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FileOperationProtectionTest {

    /**
     * 보낸 시작 요청의 id를 기록하고, [accepts]에 따라 요청을 받아들이거나 거부하는 테스트용 컨트롤러.
     * @param accepts 시작 요청을 받아들일지(false면 시스템 거부처럼 false를 돌려준다)
     */
    private class RecordingController(var accepts: Boolean = true) : FileOperationServiceController {
        /** 받은 시작 요청 id(보낸 순서). */
        val requests = mutableListOf<Int>()
        override fun start(requestId: Int): Boolean {
            requests += requestId
            return accepts
        }
    }

    /** 시작 요청을 기록하는 테스트용 컨트롤러. */
    private val controller = RecordingController()

    /** 검증 대상 작업 보호. */
    private val protection = FileOperationProtection(controller, FixedMessages)

    @Test
    fun `서비스가 포그라운드가 됐다고 알리면 보호를 준다`() = runTest(UnconfinedTestDispatcher()) {
        val result = async { protection.acquire() }
        assertFalse(result.isCompleted)

        protection.report(controller.requests.single(), granted = true)

        assertEquals(IoResult.Done, result.await())
    }

    @Test
    fun `서비스가 startForeground 거부를 알리면 시작하지 않았다는 실패를 돌려준다`() = runTest(UnconfinedTestDispatcher()) {
        val result = async { protection.acquire() }

        protection.report(controller.requests.single(), granted = false)

        assertEquals(IoResult.ItemFailed(FixedMessages.protectionRefused), result.await())
    }

    @Test
    fun `시작 요청이 거부되면 결과를 기다리지 않고 실패를 돌려준다`() = runTest(UnconfinedTestDispatcher()) {
        controller.accepts = false

        assertEquals(IoResult.ItemFailed(FixedMessages.protectionRefused), protection.acquire())
    }

    @Test
    fun `작업마다 따로 요청하고 결과는 자기 요청의 것만 받는다`() = runTest(UnconfinedTestDispatcher()) {
        val first = async { protection.acquire() }
        val second = async { protection.acquire() }
        val (firstId, secondId) = controller.requests

        protection.report(secondId, granted = false)
        assertFalse(first.isCompleted)
        protection.report(firstId, granted = true)

        assertEquals(IoResult.Done, first.await())
        assertEquals(IoResult.ItemFailed(FixedMessages.protectionRefused), second.await())
    }

    @Test
    fun `취소된 요청의 결과는 무시한다`() = runTest(UnconfinedTestDispatcher()) {
        val cancelled = async { protection.acquire() }
        cancelled.cancel()

        protection.report(controller.requests.single(), granted = true)

        val next = async { protection.acquire() }
        protection.report(controller.requests.last(), granted = true)
        assertEquals(IoResult.Done, next.await())
    }
}
