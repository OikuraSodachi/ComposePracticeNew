package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.ui.model.ConflictResolution
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.NameCollisionGroup
import com.todokanai.composepracticenew.ui.model.PendingConflict
import com.todokanai.composepracticenew.viewmodel.ConflictGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ConflictGate]가 충돌이 없을 때만 바로 실행하고, 충돌이 있으면 들어온 순서대로 하나씩 사용자의 선택대로 처리하는지 검증하는 단위 테스트. */
class ConflictGateTest {

    /** 검증 대상 게이트. */
    private val gate = ConflictGate()
    /** 실행 함수가 받은 건너뛸 항목 목록 기록. */
    private val executions = mutableListOf<List<FileHolderItem>>()
    /** 취소 함수가 불린 횟수. */
    private var cancels = 0
    /** 작업 대상 항목. */
    private val targets = listOf(item("a.txt"), item("b.txt"))

    @Test
    fun `충돌이 없으면 바로 실행한다`() {
        val executed = request(targets, emptyList())

        assertTrue(executed)
        assertEquals(listOf(emptyList<FileHolderItem>()), executions)
        assertNull(gate.pending.value)
    }

    @Test
    fun `충돌이 있으면 충돌 항목과 함께 확인을 기다린다`() {
        val executed = request(targets, listOf(targets[0]))

        assertFalse(executed)
        assertEquals(PendingConflict(targets, listOf(targets[0])), gate.pending.value)
        assertTrue(executions.isEmpty())
    }

    @Test
    fun `충돌이 없어도 함께 보내는 항목끼리 겹칠 수 있으면 확인을 기다린다`() {
        val collisions = listOf(NameCollisionGroup(null, listOf("a.txt", "A.txt")))

        val executed = request(targets, emptyList(), collisions)

        assertFalse(executed)
        assertEquals(PendingConflict(targets, emptyList(), collisions), gate.pending.value)
        assertTrue(executions.isEmpty())
    }

    @Test
    fun `덮어쓰기를 고르면 건너뛸 항목 없이 실행한다`() {
        request(targets, listOf(targets[0]))

        assertTrue(gate.resolve(ConflictResolution.OVERWRITE))
        assertEquals(listOf(emptyList<FileHolderItem>()), executions)
        assertNull(gate.pending.value)
    }

    @Test
    fun `건너뛰기를 고르면 충돌 항목을 건너뛰고 실행한다`() {
        request(targets, listOf(targets[0]))

        assertTrue(gate.resolve(ConflictResolution.SKIP))
        assertEquals(listOf(listOf(targets[0])), executions)
        assertNull(gate.pending.value)
    }

    @Test
    fun `취소하면 실행하지 않고 취소 함수를 부른 뒤 확인 대기를 끝낸다`() {
        request(targets, listOf(targets[0]))

        assertFalse(gate.resolve(ConflictResolution.CANCEL))
        assertTrue(executions.isEmpty())
        assertEquals(1, cancels)
        assertNull(gate.pending.value)
    }

    @Test
    fun `확인을 기다리는 동안 들어온 요청은 버리지 않고 앞의 확인이 끝나면 묻는다`() {
        val later = listOf(item("c.txt"))
        request(targets, listOf(targets[0]))

        assertFalse(request(later, later))
        assertEquals(PendingConflict(targets, listOf(targets[0])), gate.pending.value)

        gate.resolve(ConflictResolution.OVERWRITE)
        assertEquals(PendingConflict(later, later), gate.pending.value)

        gate.resolve(ConflictResolution.SKIP)
        assertEquals(listOf(emptyList(), later), executions)
        assertNull(gate.pending.value)
    }

    @Test
    fun `모두 버리면 실행하지도 취소 함수를 부르지도 않는다`() {
        request(targets, listOf(targets[0]))
        request(listOf(item("c.txt")), listOf(item("c.txt")))

        gate.discardAll()

        assertNull(gate.pending.value)
        assertTrue(executions.isEmpty())
        assertEquals(0, cancels)
    }

    /** 실행·취소를 기록하는 함수로 게이트에 요청한다. */
    private fun request(targets: List<FileHolderItem>, conflicts: List<FileHolderItem>, collisions: List<NameCollisionGroup> = emptyList()) =
        gate.request(targets, conflicts, collisions, onCancel = { cancels++ }) { executions += it }

    /** [name] 파일 항목을 만든다. */
    private fun item(name: String) = FileHolderItem(path = "/src/$name", isDirectory = false, name = name, size = "", lastModified = "", sizeBytes = null, lastModifiedMillis = 0L)
}
