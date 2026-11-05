package com.todokanai.composepracticenew.data.datastore

import com.todokanai.composepracticenew.model.PreferenceFailure
import com.todokanai.composepracticenew.usecase.PreferenceFailureUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [PreferenceFailureNotices]가 알림을 한 번만 꺼내게 하고, 회복한 읽기 실패를 빼며 다시 알리지 않는지를 [PreferenceFailureUseCase.deliver]와 함께 검증하는 단위 테스트. */
@OptIn(ExperimentalCoroutinesApi::class)
class PreferenceFailureNoticesTest {

    /** 검증 대상인 알림 목록. */
    private val notices = PreferenceFailureNotices()

    @Test
    fun `여러 곳이 함께 받아도 각 실패는 한 곳에서만 알린다`() = runTest {
        // receiveAsFlow로 나눠 주던 이전 구현에서는 알림이 어느 수집자로 갈지 정해지지 않았다 — 여기서는 합쳐 한 번씩만 알리는지를 본다
        val first = mutableListOf<PreferenceFailure>()
        val second = mutableListOf<PreferenceFailure>()
        backgroundScope.launch { PreferenceFailureUseCase(notices).deliver { first += it } }
        backgroundScope.launch { PreferenceFailureUseCase(notices).deliver { second += it } }
        runCurrent()

        notices.report(PreferenceFailure.SAVE)
        notices.report(PreferenceFailure.RESET)
        runCurrent()

        assertEquals(listOf(PreferenceFailure.SAVE, PreferenceFailure.RESET), (first + second).sorted())
        assertEquals(emptyList<Any>(), notices.pending.value)
    }

    @Test
    fun `꺼낸 알림은 다시 꺼낼 수 없다`() {
        notices.report(PreferenceFailure.SAVE)
        val id = notices.pending.value.single().id

        assertTrue(notices.claim(id))
        assertFalse(notices.claim(id))
    }

    @Test
    fun `읽기가 회복하면 알리지 않은 읽기 실패만 빠진다`() {
        notices.report(PreferenceFailure.READ)
        notices.report(PreferenceFailure.SAVE)

        notices.readRecovered()

        assertEquals(listOf(PreferenceFailure.SAVE), notices.pending.value.map { it.content })
    }

    @Test
    fun `보여 주는 도중 읽기가 회복한 읽기 실패는 취소돼도 돌려놓지 않는다`() = runTest {
        // 보여 주다 만 알림은 돌려놓지만, 그 사이 사실이 아니게 된 알림까지 다시 알리면 안 된다
        notices.report(PreferenceFailure.READ)
        notices.report(PreferenceFailure.SAVE)
        val receiving = backgroundScope.launch { PreferenceFailureUseCase(notices).deliver { Channel<Unit>().receive() } }
        runCurrent()

        notices.readRecovered()
        receiving.cancel()
        runCurrent()

        assertEquals(listOf(PreferenceFailure.SAVE), notices.pending.value.map { it.content })
    }
}
