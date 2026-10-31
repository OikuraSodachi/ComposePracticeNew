package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.SortMode
import org.junit.Assert.assertEquals
import org.junit.Test

/** [SortMode]의 저장 식별자 호환성, 복원 폴백, 모드별 정렬 규칙을 검증하는 단위 테스트. */
class SortModeTest {

    private val dirB = item(name = "b", isDirectory = true, size = null, modified = 30)
    private val fileA = item(name = "a.txt", isDirectory = false, size = 300, modified = 20)
    private val fileC = item(name = "c.zip", isDirectory = false, size = 100, modified = 10)
    private val all = listOf(fileC, dirB, fileA)

    @Test
    fun `저장 식별자는 기존 DataStore 값과 동일하다`() {
        val expected = listOf(
            "BY_DEFAULT",
            "BY_NAME_ASCENDING", "BY_NAME_DESCENDING",
            "BY_SIZE_ASCENDING", "BY_SIZE_DESCENDING",
            "BY_TYPE_ASCENDING", "BY_TYPE_DESCENDING",
            "BY_DATE_ASCENDING", "BY_DATE_DESCENDING"
        )
        assertEquals(expected, SortMode.entries.map { it.id })
    }

    @Test
    fun `fromId는 저장된 식별자를 복원하고 알 수 없는 값은 BY_DEFAULT로 폴백한다`() {
        SortMode.entries.forEach { assertEquals(it, SortMode.fromId(it.id)) }
        assertEquals(SortMode.BY_DEFAULT, SortMode.fromId("UNKNOWN"))
        assertEquals(SortMode.BY_DEFAULT, SortMode.fromId(null))
    }

    @Test
    fun `BY_DEFAULT는 디렉터리 우선 후 이름순이다`() =
        assertSorted(SortMode.BY_DEFAULT, dirB, fileA, fileC)

    @Test
    fun `이름 정렬`() {
        assertSorted(SortMode.BY_NAME_ASCENDING, fileA, dirB, fileC)
        assertSorted(SortMode.BY_NAME_DESCENDING, fileC, dirB, fileA)
    }

    @Test
    fun `크기 정렬`() {
        assertSorted(SortMode.BY_SIZE_ASCENDING, dirB, fileC, fileA)
        assertSorted(SortMode.BY_SIZE_DESCENDING, fileA, fileC, dirB)
    }

    @Test
    fun `확장자 정렬`() {
        assertSorted(SortMode.BY_TYPE_ASCENDING, dirB, fileA, fileC)
        assertSorted(SortMode.BY_TYPE_DESCENDING, fileC, fileA, dirB)
    }

    @Test
    fun `숨김 파일의 첫 점은 확장자가 아니라 확장자 없는 항목과 함께 정렬된다`() {
        val dotFile = item(name = ".zshrc", isDirectory = false, size = 1, modified = 0)

        assertEquals(listOf(dotFile, fileA, fileC), listOf(fileC, dotFile, fileA).sortedWith(SortMode.BY_TYPE_ASCENDING.comparator))
    }

    @Test
    fun `수정일 정렬`() {
        assertSorted(SortMode.BY_DATE_ASCENDING, fileC, fileA, dirB)
        assertSorted(SortMode.BY_DATE_DESCENDING, dirB, fileA, fileC)
    }

    private fun assertSorted(mode: SortMode, vararg expected: FileHolderItem) =
        assertEquals(expected.toList(), all.sortedWith(mode.comparator))

    private fun item(name: String, isDirectory: Boolean, size: Long?, modified: Long) =
        FileHolderItem(path = "/$name", isDirectory = isDirectory, name = name, sizeBytes = size, lastModifiedMillis = modified)
}
