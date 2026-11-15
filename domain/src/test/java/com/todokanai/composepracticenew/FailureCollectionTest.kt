package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.operation.forEachCollectingFailures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** [forEachCollectingFailures]가 실패를 모아 끝까지 진행하고, 연결 끊김은 즉시 돌려주며, 취소·구현 오류는 전파하는지 검증하는 단위 테스트. */
class FailureCollectionTest {

    @Test
    fun `모든 항목이 성공하면 성공을 돌려주고 모든 항목을 처리한다`() = runBlocking {
        val processed = mutableListOf<String>()
        val result = forEachCollectingFailures(listOf("a", "b", "c"), nameOf = { it }) {
            processed += it
            IoResult.Done
        }
        assertEquals(listOf("a", "b", "c"), processed)
        assertEquals(IoResult.Done, result)
    }

    @Test
    fun `한 항목이 실패해도 나머지를 끝까지 처리하고 마지막에 원인을 담아 알린다`() = runBlocking {
        val processed = mutableListOf<String>()
        val result = forEachCollectingFailures(listOf("a", "b", "c"), nameOf = { it }) {
            processed += it
            if (it == "a") IoResult.ItemFailed(FailureReason.Raw("권한 없음")) else IoResult.Done
        }
        assertEquals(listOf("a", "b", "c"), processed)
        assertEquals(IoResult.PartiallyFailed(listOf(FailedItem("a", FailureReason.Raw("권한 없음")))), result)
    }

    @Test
    fun `여러 항목이 실패하면 개수와 이름 일부를 담고 나머지는 줄여 표시한다`() = runBlocking {
        val items = listOf("a", "b", "c", "d", "e", "f")
        val result = forEachCollectingFailures(items, nameOf = { it }) {
            if (it != "f") IoResult.ItemFailed(null) else IoResult.Done
        }
        assertEquals(listOf("a", "b", "c", "d", "e").map { FailedItem(it, null) }, (result as IoResult.PartiallyFailed).failures)
    }

    @Test
    fun `여러 항목이 같은 원인으로 실패하면 그 원인을 함께 표시한다`() = runBlocking {
        val result = forEachCollectingFailures(listOf("a", "b"), nameOf = { it }) {
            IoResult.ItemFailed(FailureReason.Raw("425 Can't open data connection"))
        }
        assertEquals(listOf("a", "b").map { FailedItem(it, FailureReason.Raw("425 Can't open data connection")) }, (result as IoResult.PartiallyFailed).failures)
    }

    @Test
    fun `원인이 여러 종류면 가장 많은 원인과 나머지 원인의 종류·항목 수를 표시한다`() = runBlocking {
        val reasons = mapOf("a" to "디스크 부족", "b" to "권한 없음", "c" to "권한 없음", "d" to null)
        val result = forEachCollectingFailures(reasons.keys, nameOf = { it }) { IoResult.ItemFailed(reasons.getValue(it)?.let(FailureReason::Raw)) }
        assertEquals(reasons.map { (path, reason) -> FailedItem(path, reason?.let(FailureReason::Raw)) }, (result as IoResult.PartiallyFailed).failures)
    }

    @Test
    fun `연결이 끊기면 남은 항목을 시도하지 않고 그 끊김을 그대로 돌려준다`() = runBlocking {
        val processed = mutableListOf<String>()
        val result = forEachCollectingFailures(listOf("a", "b", "c"), nameOf = { it }) {
            processed += it
            when (it) {
                "a" -> IoResult.ItemFailed(FailureReason.Raw("일반 실패"))
                "b" -> IoResult.ConnectionLost(FailureReason.Raw("끊김"))
                else -> IoResult.Done
            }
        }
        assertEquals(listOf("a", "b"), processed)
        assertEquals(IoResult.ConnectionLost(FailureReason.Raw("끊김")), result)
    }

    @Test
    fun `취소는 실패로 모으지 않고 즉시 전파한다`() {
        val processed = mutableListOf<String>()
        assertThrows(CancellationException::class.java) {
            runBlocking {
                forEachCollectingFailures(listOf("a", "b"), nameOf = { it }) {
                    processed += it
                    throw CancellationException("취소")
                }
            }
        }
        assertEquals(listOf("a"), processed)
    }

    @Test
    fun `구현 오류 예외는 항목 실패로 모으지 않고 즉시 전파한다`() {
        val processed = mutableListOf<String>()
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                forEachCollectingFailures(listOf("a", "b"), nameOf = { it }) {
                    processed += it
                    throw IllegalStateException("버그")
                }
            }
        }
        assertEquals(listOf("a"), processed)
    }

    @Test
    fun `앞선 실패에 막힌 항목은 시도하지도 실패로 세지도 않는다`() = runBlocking {
        // 리프부터 삭제하는 순서 — a/b/x 실패 시 그 상위인 a/b, a는 막히고 형제 c는 그대로 처리된다
        val items = listOf("a/b/x", "a/b", "c", "a")
        val processed = mutableListOf<String>()
        val result = forEachCollectingFailures(
            items,
            nameOf = { it },
            isBlockedBy = { item, failed -> failed.startsWith("$item/") }
        ) {
            processed += it
            if (it == "a/b/x") IoResult.ItemFailed(FailureReason.Raw("권한 없음")) else IoResult.Done
        }
        assertEquals(listOf("a/b/x", "c"), processed)
        assertEquals(IoResult.PartiallyFailed(listOf(FailedItem("a/b/x", FailureReason.Raw("권한 없음")))), result)
    }

    @Test
    fun `항목 작업의 부분 실패는 감싸지 않고 항목 이름 아래 경로로 평탄화한다`() = runBlocking {
        val result = forEachCollectingFailures(listOf("a.txt", "dir", "ok"), nameOf = { it }) { item ->
            when (item) {
                // 대상 자신의 실패는 안쪽에서 빈 경로로 온다
                "a.txt" -> IoResult.PartiallyFailed(listOf(FailedItem("", null)))
                "dir" -> IoResult.PartiallyFailed(listOf(FailedItem("sub/x.txt", FailureReason.Raw("DELE 실패: 550"))))
                else -> IoResult.Done
            }
        }
        result as IoResult.PartiallyFailed
        assertEquals(listOf("a.txt", "dir/sub/x.txt"), result.failures.map { it.path })
        assertEquals(listOf(null, FailureReason.Raw("DELE 실패: 550")), result.failures.map { it.reason })
    }

    @Test
    fun `평탄화된 실패가 하나면 그 경로와 안쪽 원인을 바로 보여준다`() = runBlocking {
        val result = forEachCollectingFailures(listOf("dir"), nameOf = { it }) {
            IoResult.PartiallyFailed(listOf(FailedItem("sub/x.txt", FailureReason.Raw("DELE 실패: 550"))))
        }
        assertEquals(IoResult.PartiallyFailed(listOf(FailedItem("dir/sub/x.txt", FailureReason.Raw("DELE 실패: 550")))), result)
    }
}
