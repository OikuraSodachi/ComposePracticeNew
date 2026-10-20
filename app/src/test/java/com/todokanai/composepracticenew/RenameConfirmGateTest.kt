package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.RenameConflict
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.viewmodel.RenameConfirmGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [RenameConfirmGate]가 겹침 종류에 따라 즉시 실행하거나 덮어쓰기 확인을 기다리는지 검증하는 단위 테스트. */
class RenameConfirmGateTest {

    private val item = FileHolderItem(path = "/d/a.txt", isDirectory = false, name = "a.txt", size = "", lastModified = "")
    private val gate = RenameConfirmGate()
    private val calls = mutableListOf<Boolean>()

    @Test
    fun `겹침이 없거나 종류가 다르면 덮어쓰지 않는 실행을 즉시 한다`() {
        gate.request(item, "c.txt", RenameConflict.NONE) { calls += it }
        gate.request(item, "x", RenameConflict.TYPE_MISMATCH) { calls += it }

        assertEquals(listOf(false, false), calls)
        assertNull(gate.pending.value)
    }

    @Test
    fun `파일·폴더 겹침은 확인을 기다렸다가 확인하면 덮어쓰기로 실행한다`() {
        gate.request(item, "b.txt", RenameConflict.FILE) { calls += it }
        assertEquals(emptyList<Boolean>(), calls)
        assertEquals("b.txt", gate.pending.value?.newName)

        gate.confirm()

        assertEquals(listOf(true), calls)
        assertNull(gate.pending.value)
    }

    @Test
    fun `취소하면 실행하지 않고, 대기 중에 들어온 새 요청은 무시한다`() {
        gate.request(item, "b.txt", RenameConflict.FOLDER) { calls += it }
        gate.request(item, "c.txt", RenameConflict.NONE) { calls += it }
        assertEquals("b.txt", gate.pending.value?.newName)

        gate.cancel()
        gate.confirm()

        assertEquals(emptyList<Boolean>(), calls)
        assertNull(gate.pending.value)
    }
}
