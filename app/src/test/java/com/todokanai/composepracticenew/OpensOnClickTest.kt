package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.LocalSelectMode
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.ui.model.opensOnClick
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 목록 행과 검색 결과가 함께 쓰는 클릭 규칙 [opensOnClick]이 기본 모드에서는 모두 열고, 다중 선택 중에는 열지 않으며,
 * 목적지 확인 중에는 폴더만 여는지 검증하는 단위 테스트.
 */
class OpensOnClickTest {

    /** 누를 폴더. */
    private val folder = item("/storage/0/music", isDirectory = true)
    /** 누를 파일. */
    private val file = item("/storage/0/song.mp3", isDirectory = false)

    @Test
    fun `기본 모드에서는 폴더와 파일을 모두 연다`() {
        assertEquals(listOf(true, true), opens(SelectMode.Default))
    }

    @Test
    fun `다중 선택 중에는 아무것도 열지 않는다`() {
        assertEquals(listOf(false, false), opens(SelectMode.MultiSelect))
    }

    @Test
    fun `목적지 확인 중에는 폴더만 연다`() {
        val confirmModes = listOf(
            SelectMode.LocalOperation.Copy, SelectMode.LocalOperation.Move, SelectMode.LocalOperation.Unzip,
            SelectMode.LocalOperation.UnzipHere, SelectMode.Download
        )
        confirmModes.forEach { assertEquals("$it", listOf(true, false), opens(it)) }
    }

    /** [mode]에서 폴더와 파일을 각각 여는지. */
    private fun opens(mode: LocalSelectMode): List<Boolean> = listOf(mode.opensOnClick(folder), mode.opensOnClick(file))

    /** [path]의 로컬 항목. */
    private fun item(path: String, isDirectory: Boolean) = FileHolderItem(
        path = path, isDirectory = isDirectory, name = path.substringAfterLast('/'), size = "", lastModified = "",
        sizeBytes = null, lastModifiedMillis = 0L, isLocal = true
    )
}
