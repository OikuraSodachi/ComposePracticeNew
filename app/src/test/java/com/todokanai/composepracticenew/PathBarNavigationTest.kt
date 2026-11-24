package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.ui.model.allowsPathBarNavigation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 경로 표시줄 이동 규칙 [allowsPathBarNavigation]이 다중 선택 중에만 이동을 막는지 검증하는 단위 테스트(#519). */
class PathBarNavigationTest {

    @Test
    fun `다중 선택 중에는 경로 표시줄로 이동하지 않는다`() {
        assertFalse(SelectMode.MultiSelect.allowsPathBarNavigation)
    }

    @Test
    fun `기본 모드와 목적지를 찾는 모드에서는 경로 표시줄로 이동한다`() {
        val modes = listOf(
            SelectMode.Default,
            SelectMode.LocalOperation.Copy, SelectMode.LocalOperation.Move,
            SelectMode.LocalOperation.Unzip, SelectMode.LocalOperation.UnzipHere,
            SelectMode.Download(emptyList()), SelectMode.ArchiveCopy(archiveSelection), SelectMode.Upload(emptyList())
        )

        modes.forEach { assertTrue("$it", it.allowsPathBarNavigation) }
    }
}
