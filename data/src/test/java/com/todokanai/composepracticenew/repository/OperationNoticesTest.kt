package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.OperationNoticeKind
import com.todokanai.composepracticenew.usecase.OperationNoticeUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [OperationNotices]가 받을 화면이 없을 때 넣은 메시지를 잃지 않고, 각 메시지를 한 곳에서 하나씩 다 보여 준 뒤에만 지우는지를 [OperationNoticeUseCase.deliver]와 함께 검증하는 단위 테스트. */
@OptIn(ExperimentalCoroutinesApi::class)
class OperationNoticesTest {

    /** 검증 대상인 메시지 알림함. */
    private val notices = OperationNotices()

    @Test
    fun `받는 화면이 없을 때 넣은 메시지는 나중에 받기 시작하면 넣은 순서대로 알린다`() = runTest {
        // replay 없는 SharedFlow로 흘려보내던 이전 구현에서는 수집자가 없는 동안의 메시지가 버려졌다 — 저장소 화면에 있을 때 끝난 작업의 실패가 그랬다
        notices.report(OperationNoticeKind.FAILURE, "복사 실패")
        notices.report(OperationNoticeKind.COMPLETION, "삭제 완료")

        val shown = mutableListOf<String>()
        backgroundScope.launch { OperationNoticeUseCase(notices).deliver { shown += it.message } }
        runCurrent()

        assertEquals(listOf("복사 실패", "삭제 완료"), shown)
        assertEquals(emptyList<Any>(), notices.pending.value)
    }

    @Test
    fun `여러 곳이 함께 받아도 각 메시지는 한 곳에서만 알린다`() = runTest {
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()
        backgroundScope.launch { OperationNoticeUseCase(notices).deliver { first += it.message } }
        backgroundScope.launch { OperationNoticeUseCase(notices).deliver { second += it.message } }
        runCurrent()

        notices.report(OperationNoticeKind.FAILURE, "이동 실패")
        notices.report(OperationNoticeKind.COMPLETION, "복사 완료")
        runCurrent()

        assertEquals(listOf("복사 완료", "이동 실패"), (first + second).sorted())
        assertEquals(emptyList<Any>(), notices.pending.value)
    }

    @Test
    fun `앞 메시지를 다 보여 주기 전에는 다음 메시지를 꺼내지 않는다`() = runTest {
        // 쌓인 메시지를 한꺼번에 꺼내 Toast로 띄우던 이전 구현에서는 시스템 Toast 한도(패키지당 5개)를 넘은 메시지가 꺼낸 채로 버려졌다
        repeat(6) { notices.report(OperationNoticeKind.FAILURE, "전송 실패 $it") }
        val shown = mutableListOf<String>()
        val dismissed = Channel<Unit>()
        backgroundScope.launch {
            OperationNoticeUseCase(notices).deliver {
                shown += it.message
                dismissed.receive()
            }
        }
        runCurrent()

        assertEquals(listOf("전송 실패 0"), shown)
        assertEquals((1..5).map { "전송 실패 $it" }, notices.pending.value.map { it.content.message })

        repeat(6) {
            dismissed.send(Unit)
            runCurrent()
        }

        assertEquals((0..5).map { "전송 실패 $it" }, shown)
        assertEquals(emptyList<Any>(), notices.pending.value)
    }

    @Test
    fun `보여 주는 도중 취소되면 그 메시지를 넣은 순서 자리로 돌려놓는다`() = runTest {
        // 화면이 보이지 않게 되면(STARTED를 벗어나면) 보여 주던 메시지를 끝까지 보여 주지 못한다 — 다시 보일 때 처음부터 알려야 한다
        notices.report(OperationNoticeKind.FAILURE, "압축 실패")
        notices.report(OperationNoticeKind.COMPLETION, "이동 완료")
        val receiving = backgroundScope.launch { OperationNoticeUseCase(notices).deliver { Channel<Unit>().receive() } }
        runCurrent()

        receiving.cancel()
        runCurrent()

        assertEquals(listOf("압축 실패", "이동 완료"), notices.pending.value.map { it.content.message })
    }

    @Test
    fun `꺼낸 메시지는 다시 꺼낼 수 없다`() {
        notices.report(OperationNoticeKind.FAILURE, "압축 실패")
        val id = notices.pending.value.single().id

        assertTrue(notices.claim(id))
        assertFalse(notices.claim(id))
    }
}
