package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.KnownSizeProgress
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.toModel
import com.todokanai.composepracticenew.tools.independent.readableFileSize_td
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [toModel]이 진행률을 모르면 0%로 바꾸지 않고, 크기를 아는 부분의 진행을 그 부분의 퍼센트로 바꾸는지 검증하는 단위 테스트. */
class ProgressStateModelTest {

    @Test
    fun `진행률을 모르면 0%가 아니라 없음이다`() {
        assertNull(ProgressState(writtenBytes = 10).toModel().progress)
    }

    @Test
    fun `크기를 아는 부분은 그 부분의 합에 대한 퍼센트와 크기 문자열로 바뀐다`() {
        val model = ProgressState(knownSize = KnownSizeProgress(200, 50, 1, 7)).toModel().knownSize!!

        assertEquals(25, model.progress)
        assertEquals(readableFileSize_td(50), model.writtenSize)
        assertEquals(readableFileSize_td(200), model.totalSize)
        assertEquals(1, model.unknownFileCount)
        assertEquals(readableFileSize_td(7), model.unknownWrittenSize)
    }

    @Test
    fun `크기를 아는 부분의 합이 0이면 나눌 기준이 없어 퍼센트가 없다`() {
        assertNull(ProgressState(knownSize = KnownSizeProgress(0, 0, 2, 7)).toModel().knownSize?.progress)
    }
}
