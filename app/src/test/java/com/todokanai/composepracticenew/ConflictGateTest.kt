package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.ui.model.ConflictResolution
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.PendingConflict
import com.todokanai.composepracticenew.viewmodel.ConflictGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ConflictGate]가 충돌이 없을 때만 바로 실행하고, 충돌이 있으면 사용자의 선택대로 실행하는지 검증하는 단위 테스트. */
class ConflictGateTest {

    /** 검증 대상 게이트. */
    private val gate = ConflictGate()
    /** 실행 함수가 받은 건너뛸 항목 목록 기록. */
    private val executions = mutableListOf<List<FileHolderItem>>()
    /** 작업 대상 항목. */
    private val targets = listOf(item("a.txt"), item("b.txt"))

    @Test
    fun `충돌이 없으면 바로 실행한다`() {
        val executed = gate.request(targets, emptyList()) { executions += it }

        assertTrue(executed)
        assertEquals(listOf(emptyList<FileHolderItem>()), executions)
        assertNull(gate.pending.value)
    }

    @Test
    fun `충돌이 있으면 충돌 항목과 함께 확인을 기다린다`() {
        val executed = gate.request(targets, listOf(targets[0])) { executions += it }

        assertFalse(executed)
        assertEquals(PendingConflict(targets, listOf(targets[0])), gate.pending.value)
        assertTrue(executions.isEmpty())
    }

    @Test
    fun `덮어쓰기를 고르면 건너뛸 항목 없이 실행한다`() {
        gate.request(targets, listOf(targets[0])) { executions += it }

        assertTrue(gate.resolve(ConflictResolution.OVERWRITE))
        assertEquals(listOf(emptyList<FileHolderItem>()), executions)
        assertNull(gate.pending.value)
    }

    @Test
    fun `건너뛰기를 고르면 충돌 항목을 건너뛰고 실행한다`() {
        gate.request(targets, listOf(targets[0])) { executions += it }

        assertTrue(gate.resolve(ConflictResolution.SKIP))
        assertEquals(listOf(listOf(targets[0])), executions)
        assertNull(gate.pending.value)
    }

    @Test
    fun `취소하면 실행하지 않고 확인 대기를 끝낸다`() {
        gate.request(targets, listOf(targets[0])) { executions += it }

        assertFalse(gate.resolve(ConflictResolution.CANCEL))
        assertTrue(executions.isEmpty())
        assertNull(gate.pending.value)
    }

    @Test
    fun `확인을 기다리는 동안 들어온 요청은 실행하지도 대기시키지도 않는다`() {
        gate.request(targets, listOf(targets[0])) { executions += it }
        val pending = gate.pending.value

        val executed = gate.request(listOf(item("c.txt")), emptyList()) { executions += it }

        assertFalse(executed)
        assertTrue(executions.isEmpty())
        assertEquals(pending, gate.pending.value)
    }

    /** [name] 파일 항목을 만든다. */
    private fun item(name: String) = FileHolderItem(path = "/src/$name", isDirectory = false, name = name, size = "", lastModified = "")
}
