package com.todokanai.composepracticenew.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingRequestTest {

    @Test
    fun `처리했다고 알릴 때까지 요청을 보관한다`() {
        val pending = PendingRequest<String>()

        pending.post("a")

        assertEquals("a", pending.request.value)
    }

    @Test
    fun `처리했다고 알리면 요청이 사라진다`() {
        val pending = PendingRequest<String>()
        pending.post("a")

        pending.consume("a")

        assertNull(pending.request.value)
    }

    @Test
    fun `처리하기 전에 새 요청이 오면 마지막 요청만 남는다`() {
        val pending = PendingRequest<String>()

        pending.post("a")
        pending.post("b")

        assertEquals("b", pending.request.value)
    }

    @Test
    fun `처리한 요청을 알릴 때 그 사이 들어온 다른 요청은 남긴다`() {
        val pending = PendingRequest<String>()
        pending.post("a")
        pending.post("b")

        pending.consume("a")

        assertEquals("b", pending.request.value)
    }
}
