package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.OperationNoticeKind
import com.todokanai.composepracticenew.usecase.OperationNoticeUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [OperationNotices]가 받을 화면이 없을 때 넣은 메시지를 잃지 않고 각 메시지를 한 곳에서만 꺼내게 하는지를 [OperationNoticeUseCase.deliver]와 함께 검증하는 단위 테스트. */
@OptIn(ExperimentalCoroutinesApi::class)
class OperationNoticesTest {

    /** 검증 대상인 메시지 목록. */
    private val notices = OperationNotices()

    @Test
    fun `받는 화면이 없을 때 넣은 메시지는 나중에 받기 시작하면 넣은 순서대로 알린다`() = runTest {
        // replay 없는 SharedFlow로 흘려보내던 이전 구현에서는 수집자가 없는 동안의 메시지가 버려졌다 — 저장소 화면에 있을 때 끝난 작업의 실패가 그랬다
        notices.report(OperationNoticeKind.FAILURE, "복사 실패")
        notices.report(OperationNoticeKind.COMPLETION, "삭제 완료")

        val shown = mutableListOf<String>()
        backgroundScope.launch { OperationNoticeUseCase(notices).deliver { shown += it } }
        runCurrent()

        assertEquals(listOf("복사 실패", "삭제 완료"), shown)
        assertEquals(emptyList<Any>(), notices.pending.value)
    }

    @Test
    fun `여러 곳이 함께 받아도 각 메시지는 한 곳에서만 알린다`() = runTest {
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()
        backgroundScope.launch { OperationNoticeUseCase(notices).deliver { first += it } }
        backgroundScope.launch { OperationNoticeUseCase(notices).deliver { second += it } }
        runCurrent()

        notices.report(OperationNoticeKind.FAILURE, "이동 실패")
        notices.report(OperationNoticeKind.COMPLETION, "복사 완료")
        runCurrent()

        assertEquals(listOf("복사 완료", "이동 실패"), (first + second).sorted())
        assertEquals(emptyList<Any>(), notices.pending.value)
    }

    @Test
    fun `꺼낸 메시지는 다시 꺼낼 수 없다`() {
        notices.report(OperationNoticeKind.FAILURE, "압축 실패")
        val id = notices.pending.value.single().id

        assertTrue(notices.claim(id))
        assertFalse(notices.claim(id))
    }
}
