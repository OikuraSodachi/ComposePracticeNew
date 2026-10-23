package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.DestinationConflicts
import com.todokanai.composepracticenew.model.ListingCompleteness
import com.todokanai.composepracticenew.ui.model.ConflictResolution
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.PendingConflict
import com.todokanai.composepracticenew.viewmodel.ConflictGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ConflictGate]가 충돌이 없다고 확인된 경우에만 바로 실행하고, 목적지 목록을 확인하지 못했으면
 * 충돌이 없어 보여도 덮어쓸 수 있음을 확인받은 뒤에만 실행하는지 검증하는 단위 테스트.
 */
class ConflictGateTest {

    /** 검증 대상 게이트. */
    private val gate = ConflictGate()
    /** 실행 함수가 받은 건너뛸 항목 목록 기록. */
    private val executions = mutableListOf<List<FileHolderItem>>()
    /** 작업 대상 항목. */
    private val targets = listOf(item("a.txt"), item("b.txt"))

    @Test
    fun `확인된 목적지에 충돌이 없으면 바로 실행한다`() {
        val executed = gate.request(targets, checked(emptyList(), ListingCompleteness.COMPLETE)) { executions += it }

        assertTrue(executed)
        assertEquals(listOf(emptyList<FileHolderItem>()), executions)
        assertNull(gate.pending.value)
    }

    @Test
    fun `목적지 목록을 확인하지 못했으면 충돌이 없어 보여도 실행하지 않고 확인을 기다린다`() {
        val executed = gate.request(targets, checked(emptyList(), ListingCompleteness.UNCONFIRMED)) { executions += it }

        assertFalse(executed)
        assertTrue(executions.isEmpty())
        assertEquals(PendingConflict(targets, emptyList(), destinationUnconfirmed = true), gate.pending.value)
    }

    @Test
    fun `확인되지 않은 목적지에 계속을 고르면 전부 실행하고 취소하면 실행하지 않는다`() {
        gate.request(targets, checked(emptyList(), ListingCompleteness.UNCONFIRMED)) { executions += it }
        assertTrue(gate.resolve(ConflictResolution.OVERWRITE))
        assertEquals(listOf(emptyList<FileHolderItem>()), executions)

        gate.request(targets, checked(emptyList(), ListingCompleteness.UNCONFIRMED)) { executions += it }
        assertFalse(gate.resolve(ConflictResolution.CANCEL))
        assertEquals(1, executions.size)
    }

    @Test
    fun `확인된 목적지에 충돌이 있으면 충돌 항목과 함께 확인을 기다린다`() {
        gate.request(targets, checked(listOf(targets[0]), ListingCompleteness.COMPLETE)) { executions += it }

        assertEquals(PendingConflict(targets, listOf(targets[0]), destinationUnconfirmed = false), gate.pending.value)
        assertTrue(executions.isEmpty())
    }

    /** "/dst"에 대한 충돌 판정 결과를 만든다. */
    private fun checked(conflicts: List<FileHolderItem>, completeness: ListingCompleteness) =
        DestinationConflicts("/dst", conflicts, completeness)

    /** [name] 파일 항목을 만든다. */
    private fun item(name: String) = FileHolderItem(path = "/src/$name", isDirectory = false, name = name, size = "", lastModified = "")
}
