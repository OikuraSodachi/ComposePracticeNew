package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FailedItem
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
        val result = forEachCollectingFailures(listOf("a", "b", "c"), "삭제 실패", nameOf = { it }) {
            processed += it
            IoResult.Done
        }
        assertEquals(listOf("a", "b", "c"), processed)
        assertEquals(IoResult.Done, result)
    }

    @Test
    fun `한 항목이 실패해도 나머지를 끝까지 처리하고 마지막에 원인을 담아 알린다`() = runBlocking {
        val processed = mutableListOf<String>()
        val result = forEachCollectingFailures(listOf("a", "b", "c"), "삭제 실패", nameOf = { it }) {
            processed += it
            if (it == "a") IoResult.ItemFailed("권한 없음") else IoResult.Done
        }
        assertEquals(listOf("a", "b", "c"), processed)
        assertEquals("삭제 실패: a (권한 없음)", (result as IoResult.PartiallyFailed).message)
    }

    @Test
    fun `여러 항목이 실패하면 개수와 이름 일부를 담고 나머지는 줄여 표시한다`() = runBlocking {
        val items = listOf("a", "b", "c", "d", "e", "f")
        val result = forEachCollectingFailures(items, "업로드 실패", nameOf = { it }) {
            if (it != "f") IoResult.ItemFailed(null) else IoResult.Done
        }
        assertEquals("업로드 실패: 5개 항목 (a, b, c 외 2개)", (result as IoResult.Failure).message)
    }

    @Test
    fun `연결이 끊기면 남은 항목을 시도하지 않고 그 끊김을 그대로 돌려준다`() = runBlocking {
        val processed = mutableListOf<String>()
        val result = forEachCollectingFailures(listOf("a", "b", "c"), "다운로드 실패", nameOf = { it }) {
            processed += it
            when (it) {
                "a" -> IoResult.ItemFailed("일반 실패")
                "b" -> IoResult.ConnectionLost("끊김")
                else -> IoResult.Done
            }
        }
        assertEquals(listOf("a", "b"), processed)
        assertEquals(IoResult.ConnectionLost("끊김"), result)
    }

    @Test
    fun `취소는 실패로 모으지 않고 즉시 전파한다`() {
        val processed = mutableListOf<String>()
        assertThrows(CancellationException::class.java) {
            runBlocking {
                forEachCollectingFailures(listOf("a", "b"), "삭제 실패", nameOf = { it }) {
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
                forEachCollectingFailures(listOf("a", "b"), "삭제 실패", nameOf = { it }) {
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
            "삭제 실패",
            nameOf = { it },
            isBlockedBy = { item, failed -> failed.startsWith("$item/") }
        ) {
            processed += it
            if (it == "a/b/x") IoResult.ItemFailed("권한 없음") else IoResult.Done
        }
        assertEquals(listOf("a/b/x", "c"), processed)
        assertEquals("삭제 실패: a/b/x (권한 없음)", (result as IoResult.Failure).message)
    }

    @Test
    fun `항목 작업의 부분 실패는 감싸지 않고 항목 이름 아래 경로로 평탄화하며 머리말은 바깥 것 하나만 쓴다`() = runBlocking {
        val result = forEachCollectingFailures(listOf("a.txt", "dir", "ok"), "삭제 실패", nameOf = { it }) { item ->
            when (item) {
                // 대상 자신의 실패는 안쪽에서 빈 경로로 온다
                "a.txt" -> IoResult.PartiallyFailed("삭제하지 못함", listOf(FailedItem("", null)))
                "dir" -> IoResult.PartiallyFailed("하위 항목 삭제 실패", listOf(FailedItem("sub/x.txt", "DELE 실패: 550")))
                else -> IoResult.Done
            }
        }
        result as IoResult.PartiallyFailed
        assertEquals(listOf("a.txt", "dir/sub/x.txt"), result.failures.map { it.path })
        assertEquals("삭제 실패: 2개 항목 (a.txt, dir/sub/x.txt)", result.message)
    }

    @Test
    fun `평탄화된 실패가 하나면 그 경로와 안쪽 원인을 바로 보여준다`() = runBlocking {
        val result = forEachCollectingFailures(listOf("dir"), "삭제 실패", nameOf = { it }) {
            IoResult.PartiallyFailed("하위 항목 삭제 실패", listOf(FailedItem("sub/x.txt", "DELE 실패: 550")))
        }
        assertEquals("삭제 실패: dir/sub/x.txt (DELE 실패: 550)", (result as IoResult.Failure).message)
    }
}
