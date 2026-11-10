package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.InMemorySortPreference
import com.todokanai.composepracticenew.model.SortMode
import com.todokanai.composepracticenew.usecase.SortModeUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** [SortModeHolder]가 저장된 정렬 기준을 내보내고, 선택한 정렬 기준을 저장하는지 검증하는 단위 테스트. */
@OptIn(ExperimentalCoroutinesApi::class)
class SortModeHolderTest {

    /** 정렬 기준을 메모리에 저장하는 설정 저장소. */
    private val preference = InMemorySortPreference()
    /** 기본값과 구별되는 정렬 기준. */
    private val otherMode = SortMode.entries.first { it != SortMode.BY_DEFAULT }

    @Test
    fun `저장된 정렬 기준이 바뀌면 그 값을 내보낸다`() = runTest(UnconfinedTestDispatcher()) {
        val holder = SortModeHolder(SortModeUseCase(preference), backgroundScope)
        backgroundScope.launch { holder.sortMode.collect {} }

        preference.sortBy.value = otherMode

        assertEquals(otherMode, holder.sortMode.value)
    }

    @Test
    fun `선택한 정렬 기준을 저장한다`() = runTest(UnconfinedTestDispatcher()) {
        val holder = SortModeHolder(SortModeUseCase(preference), backgroundScope)

        holder.onSortModeSelected(otherMode)

        assertEquals(otherMode, preference.sortBy.value)
    }
}
