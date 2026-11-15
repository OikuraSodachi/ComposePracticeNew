package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.ui.model.UiText
import com.todokanai.composepracticenew.ui.model.failureTextOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [failureTextOf]가 실패 종류·원인·작업 종류로 표시 문구를 조합하는지 검증하는 단위 테스트. */
class FailureTextTest {

    /** 권한 없음 원인. */
    private val denied = FailureReason.Raw("권한 없음")

    /** 삭제 작업 부분 실패의 머리말. */
    private val deleteHeading = UiText.Resource(R.string.failure_partial_heading_delete)

    @Test
    fun `원인을 모르는 항목 실패는 문구가 없다`() {
        assertNull(failureTextOf(IoResult.ItemFailed(null), FileOperationType.COPY))
    }

    @Test
    fun `항목 실패는 원인 문구만 보인다`() {
        assertEquals(UiText.Raw("권한 없음"), failureTextOf(IoResult.ItemFailed(denied), FileOperationType.COPY))
    }

    @Test
    fun `이름 변경 실패는 새 이름과 저장소의 원인을 함께 보인다`() {
        val expected = UiText.Resource(
            R.string.failure_with_cause,
            listOf(UiText.Resource(R.string.failure_at, listOf(UiText.Resource(R.string.notice_failure_prefix_rename), "b.txt")), UiText.Raw("550"))
        )

        assertEquals(expected, failureTextOf(IoResult.ConnectionLost(FailureReason.RenameFailed("b.txt", FailureReason.Raw("550"))), null))
    }

    @Test
    fun `원인을 모르는 폴더 생성 실패는 이름만 보인다`() {
        val expected = UiText.Resource(R.string.failure_at, listOf(UiText.Resource(R.string.notice_failure_prefix_make_folder), "new"))

        assertEquals(expected, failureTextOf(IoResult.ItemFailed(FailureReason.MakeFolderFailed("new", null)), null))
    }

    @Test
    fun `보호 거부는 거부 문구로 보인다`() {
        assertEquals(UiText.Resource(R.string.error_protection_refused), failureTextOf(IoResult.ItemFailed(FailureReason.ProtectionRefused), null))
    }

    @Test
    fun `실패 하나면 작업 종류의 머리말과 경로와 원인을 보인다`() {
        val expected = UiText.Resource(R.string.failure_with_cause, listOf(UiText.Resource(R.string.failure_at, listOf(deleteHeading, "a/b/x")), denied.asText()))

        assertEquals(expected, failureTextOf(IoResult.PartiallyFailed(listOf(FailedItem("a/b/x", denied))), FileOperationType.DELETE))
    }

    @Test
    fun `작업 대상 자신이 원인 없이 실패하면 머리말만 보인다`() {
        assertEquals(deleteHeading, failureTextOf(IoResult.PartiallyFailed(listOf(FailedItem("", null))), FileOperationType.DELETE))
    }

    @Test
    fun `순간 작업의 부분 실패는 일반 머리말을 쓴다`() {
        val expected = UiText.Resource(R.string.failure_at, listOf(UiText.Resource(R.string.failure_partial_heading_instant), "a"))

        assertEquals(expected, failureTextOf(IoResult.PartiallyFailed(listOf(FailedItem("a", null))), null))
    }

    @Test
    fun `여러 항목이 원인 모르게 실패하면 개수와 경로 셋을 보이고 나머지는 줄인다`() {
        val failures = listOf("a", "b", "c", "d", "e").map { FailedItem(it, null) }
        val expected = UiText.Resource(
            R.string.failure_partial_multiple,
            listOf(
                UiText.Resource(R.string.failure_partial_heading_upload),
                5,
                UiText.Resource(R.string.failure_reason_unknown),
                UiText.Resource(R.string.failure_listed_paths_omitted, listOf("a, b, c", 2))
            )
        )

        assertEquals(expected, failureTextOf(IoResult.PartiallyFailed(failures), FileOperationType.UPLOAD))
    }

    @Test
    fun `여러 항목이 같은 원인으로 실패하면 그 원인과 경로를 모두 보인다`() {
        val failures = listOf(FailedItem("a", denied), FailedItem("b", denied))
        val expected = UiText.Resource(
            R.string.failure_partial_multiple,
            listOf(UiText.Resource(R.string.failure_partial_heading_upload), 2, denied.asText(), UiText.Raw("a, b"))
        )

        assertEquals(expected, failureTextOf(IoResult.PartiallyFailed(failures), FileOperationType.UPLOAD))
    }

    @Test
    fun `원인이 여러 종류면 가장 많은 원인과 나머지 원인의 종류와 항목 수를 보인다`() {
        val failures = listOf(
            FailedItem("a", denied),
            FailedItem("b", FailureReason.Raw("공간 부족")),
            FailedItem("c", denied),
            FailedItem("d", null)
        )
        val summary = UiText.Resource(R.string.failure_reason_summary_mixed, listOf(denied.asText(), 2, 2, 2))
        val expected = UiText.Resource(
            R.string.failure_partial_multiple,
            listOf(UiText.Resource(R.string.failure_partial_heading_copy), 4, summary, UiText.Resource(R.string.failure_listed_paths_omitted, listOf("a, b, c", 1)))
        )

        assertEquals(expected, failureTextOf(IoResult.PartiallyFailed(failures), FileOperationType.COPY))
    }

    @Test
    fun `가장 많은 원인이 여럿이면 먼저 나온 원인을 쓰고 원인 모름도 한 종류로 센다`() {
        val failures = listOf(FailedItem("a.txt", null), FailedItem("dir/sub/x.txt", denied))
        val summary = UiText.Resource(R.string.failure_reason_summary_mixed, listOf(UiText.Resource(R.string.failure_reason_unknown), 1, 1, 1))
        val expected = UiText.Resource(R.string.failure_partial_multiple, listOf(deleteHeading, 2, summary, UiText.Raw("a.txt, dir/sub/x.txt")))

        assertEquals(expected, failureTextOf(IoResult.PartiallyFailed(failures), FileOperationType.DELETE))
    }

    /** 원문 원인의 표시 문구. */
    private fun FailureReason.Raw.asText(): UiText = UiText.Raw(text)
}
