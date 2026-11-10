package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.RenameConflict
import com.todokanai.composepracticenew.usecase.ConflictRules
import org.junit.Assert.assertEquals
import org.junit.Test

/** [ConflictRules.renameConflict]가 같은 폴더의 기존 항목과 겹치는 방식을 종류·이름 비교 규칙에 따라 판정하는지 검증하는 단위 테스트. */
class RenameConflictTest {

    /** 테스트용 폴더 항목. */
    private data class Entry(val path: String, val name: String, val isDirectory: Boolean)

    private val fileA = Entry("/d/a.txt", "a.txt", false)
    private val fileB = Entry("/d/b.txt", "b.txt", false)
    private val folderX = Entry("/d/x", "x", true)
    private val folderY = Entry("/d/y", "y", true)
    private val siblings = listOf(fileA, fileB, folderX, folderY)

    private fun conflictOf(target: Entry, newName: String, matchKeyOf: (String) -> String = { it }, siblings: List<Entry> = this.siblings) =
        ConflictRules.renameConflict(
            target.path, target.isDirectory, newName, siblings, matchKeyOf, Entry::path, Entry::name, Entry::isDirectory
        )

    @Test
    fun `겹치는 이름이 없으면 NONE`() {
        assertEquals(RenameConflict.NONE, conflictOf(fileA, "c.txt"))
    }

    @Test
    fun `파일끼리 겹치면 FILE, 폴더끼리 겹치면 FOLDER`() {
        assertEquals(RenameConflict.FILE, conflictOf(fileA, "b.txt"))
        assertEquals(RenameConflict.FOLDER, conflictOf(folderX, "y"))
    }

    @Test
    fun `파일과 폴더가 겹치면 TYPE_MISMATCH`() {
        assertEquals(RenameConflict.TYPE_MISMATCH, conflictOf(fileA, "x"))
        assertEquals(RenameConflict.TYPE_MISMATCH, conflictOf(folderX, "b.txt"))
    }

    @Test
    fun `자기 자신의 이름이나 비교 값만 같은 이름은 충돌이 아니다`() {
        assertEquals(RenameConflict.NONE, conflictOf(fileA, "a.txt"))
        assertEquals(RenameConflict.NONE, conflictOf(fileA, "A.TXT", String::lowercase))
    }

    @Test
    fun `비교 값이 같은 다른 항목과는 충돌한다`() {
        assertEquals(RenameConflict.FILE, conflictOf(fileA, "B.TXT", String::lowercase))
        assertEquals(RenameConflict.NONE, conflictOf(fileA, "B.TXT"))
    }

    @Test
    fun `비교 값만 같은 다른 종류 항목뿐이면 막지 않는다 — 덮어쓰지 않는 실행이 저장소에 따라 실패하거나 성공한다`() {
        assertEquals(RenameConflict.NONE, conflictOf(fileA, "X", String::lowercase))
        assertEquals(RenameConflict.NONE, conflictOf(folderX, "B.TXT", String::lowercase))
    }

    @Test
    fun `이름이 정확히 같은 항목이 있으면 목록 순서와 관계없이 그 항목의 종류로 판정한다`() {
        val upperFile = Entry("/d/B", "B", false)
        val lowerFolder = Entry("/d/b", "b", true)

        listOf(listOf(upperFile, lowerFolder), listOf(lowerFolder, upperFile)).forEach { order ->
            val siblings = listOf(fileA) + order
            assertEquals(RenameConflict.TYPE_MISMATCH, conflictOf(fileA, "b", String::lowercase, siblings))
            assertEquals(RenameConflict.FILE, conflictOf(fileA, "B", String::lowercase, siblings))
        }
    }

    @Test
    fun `비교 값만 같은 항목 중 같은 종류가 있으면 다른 종류가 함께 있어도 확인 대상이다`() {
        val siblings = listOf(fileA, Entry("/d/B", "B", true), Entry("/d/b́", "b́", false))

        assertEquals(RenameConflict.FILE, conflictOf(fileA, "b", { it.lowercase().filter { c -> c != '́' } }, siblings))
    }
}
