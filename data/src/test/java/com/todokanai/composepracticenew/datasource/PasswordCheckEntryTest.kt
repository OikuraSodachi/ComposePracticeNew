package com.todokanai.composepracticenew.datasource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 비밀번호 확인에 쓸 항목을 고르는 기준([passwordCheckEntryOf])을 검증하는 단위 테스트 — ZIP·RAR 추출기가 함께 쓰는 기준이다.
 */
class PasswordCheckEntryTest {

    @Test
    fun `내용이 있는 항목 중 가장 작은 것을 고르고 0바이트 항목은 고르지 않는다`() {
        val sizes = mapOf("empty" to 0L, "big" to 100L, "small" to 3L)

        assertEquals("small", passwordCheckEntryOf(sizes.keys.toList()) { sizes[it] })
    }

    @Test
    fun `크기를 모르는 항목은 크기를 아는 항목보다 뒤이고 0바이트 항목보다는 앞이다`() {
        val sizes = mapOf("empty" to 0L, "unknown" to null, "big" to 100L)

        assertEquals("big", passwordCheckEntryOf(sizes.keys.toList()) { sizes[it] })
        assertEquals("unknown", passwordCheckEntryOf(listOf("empty", "unknown")) { sizes[it] })
    }

    @Test
    fun `모두 0바이트면 0바이트 항목을 고르고 항목이 없으면 null이다`() {
        assertEquals("a", passwordCheckEntryOf(listOf("a", "b")) { 0L })
        assertNull(passwordCheckEntryOf(emptyList<String>()) { 0L })
    }
}
