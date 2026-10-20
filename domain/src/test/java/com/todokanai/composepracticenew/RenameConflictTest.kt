package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.RenameConflict
import com.todokanai.composepracticenew.usecase.ConflictCheckUseCase
import org.junit.Assert.assertEquals
import org.junit.Test

/** [ConflictCheckUseCase.findRenameConflict]가 같은 폴더의 기존 항목과 겹치는 방식을 종류·대소문자 규칙에 따라 판정하는지 검증하는 단위 테스트. */
class RenameConflictTest {

    /** 테스트용 폴더 항목. */
    private data class Entry(val path: String, val name: String, val isDirectory: Boolean)

    private val useCase = ConflictCheckUseCase()
    private val fileA = Entry("/d/a.txt", "a.txt", false)
    private val fileB = Entry("/d/b.txt", "b.txt", false)
    private val folderX = Entry("/d/x", "x", true)
    private val folderY = Entry("/d/y", "y", true)
    private val siblings = listOf(fileA, fileB, folderX, folderY)

    private fun conflictOf(target: Entry, newName: String, ignoreCase: Boolean = false) =
        useCase.findRenameConflict(target, newName, siblings, ignoreCase, Entry::path, Entry::name, Entry::isDirectory)

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
    fun `자기 자신의 이름이나 대소문자만 바꾼 이름은 충돌이 아니다`() {
        assertEquals(RenameConflict.NONE, conflictOf(fileA, "a.txt"))
        assertEquals(RenameConflict.NONE, conflictOf(fileA, "A.TXT", ignoreCase = true))
    }

    @Test
    fun `대소문자 무시 여부에 따라 대소문자만 다른 다른 항목과의 충돌이 달라진다`() {
        assertEquals(RenameConflict.FILE, conflictOf(fileA, "B.TXT", ignoreCase = true))
        assertEquals(RenameConflict.NONE, conflictOf(fileA, "B.TXT", ignoreCase = false))
    }
}
