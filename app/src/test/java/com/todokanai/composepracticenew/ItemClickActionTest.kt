package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.ItemClickAction
import com.todokanai.composepracticenew.ui.model.ItemClickAction.IGNORE
import com.todokanai.composepracticenew.ui.model.ItemClickAction.OPEN
import com.todokanai.composepracticenew.ui.model.ItemClickAction.OPEN_ARCHIVE
import com.todokanai.composepracticenew.ui.model.ItemClickAction.TOGGLE
import com.todokanai.composepracticenew.ui.model.LocalSelectMode
import com.todokanai.composepracticenew.ui.model.RemoteSelectMode
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.ui.model.clickActionOf
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 목록 행과 검색 결과가 함께 쓰는 클릭 규칙 [clickActionOf]가 모드마다 맞는 동작을 정하는지 검증하는 단위 테스트(#455).
 * 로컬은 기본 모드에서 모두 열고, 다중 선택 중에는 토글하며, 목적지 확인 중에는 폴더만 연다.
 * 원격은 다중 선택 중에는 토글하고, 그 밖에는 폴더만 연다.
 */
class ItemClickActionTest {

    /** 누를 폴더. */
    private val folder = item("/storage/0/music", isDirectory = true)
    /** 누를 파일. */
    private val file = item("/storage/0/song.mp3", isDirectory = false)

    @Test
    fun `로컬 기본 모드에서는 폴더와 파일을 모두 연다`() {
        assertEquals(listOf(OPEN, OPEN), actions(SelectMode.Default))
    }

    @Test
    fun `로컬 다중 선택 중에는 폴더와 파일을 모두 토글한다`() {
        assertEquals(listOf(TOGGLE, TOGGLE), actions(SelectMode.MultiSelect))
    }

    @Test
    fun `로컬 목적지 확인 중에는 폴더만 연다`() {
        val confirmModes = listOf(
            SelectMode.LocalOperation.Copy, SelectMode.LocalOperation.Move, SelectMode.LocalOperation.Unzip,
            SelectMode.LocalOperation.UnzipHere, SelectMode.Download(emptyList()), SelectMode.ArchiveCopy(archiveSelection)
        )
        confirmModes.forEach { assertEquals("$it", listOf(OPEN, IGNORE), actions(it)) }
    }

    @Test
    fun `압축 파일에 추가할 곳을 찾는 중에는 폴더를 열고 파일은 압축 파일일 때만 연다`() {
        assertEquals(listOf(OPEN, OPEN_ARCHIVE), actions(SelectMode.AddToArchive))
    }

    @Test
    fun `원격 다중 선택 중에는 폴더와 파일을 모두 토글한다`() {
        assertEquals(listOf(TOGGLE, TOGGLE), remoteActions(SelectMode.MultiSelect))
    }

    @Test
    fun `원격 기본 모드와 업로드 목적지 확인 중에는 폴더만 연다`() {
        listOf(SelectMode.Default, SelectMode.Upload(emptyList())).forEach {
            assertEquals("$it", listOf(OPEN, IGNORE), remoteActions(it))
        }
    }

    /** 로컬 [mode]에서 폴더와 파일을 각각 눌렀을 때 할 일. */
    private fun actions(mode: LocalSelectMode): List<ItemClickAction> = listOf(mode.clickActionOf(folder), mode.clickActionOf(file))

    /** 원격 [mode]에서 폴더와 파일을 각각 눌렀을 때 할 일. */
    private fun remoteActions(mode: RemoteSelectMode): List<ItemClickAction> = listOf(mode.clickActionOf(folder), mode.clickActionOf(file))

    /** [path]의 항목. */
    private fun item(path: String, isDirectory: Boolean) = FileHolderItem(
        path = path, isDirectory = isDirectory, name = path.substringAfterLast('/'), size = "", lastModified = "",
        sizeBytes = null, lastModifiedMillis = 0L, isLocal = true
    )
}
