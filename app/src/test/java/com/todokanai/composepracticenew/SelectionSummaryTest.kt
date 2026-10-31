package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ItemAttributes
import com.todokanai.composepracticenew.model.SizeMeasurement
import com.todokanai.composepracticenew.tools.independent.readableFileSize_td
import com.todokanai.composepracticenew.ui.model.AttributesInfo
import com.todokanai.composepracticenew.ui.model.ChildCountInfo
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.ItemKind
import com.todokanai.composepracticenew.ui.model.SelectionSummary
import com.todokanai.composepracticenew.ui.model.SizeInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/** [SelectionSummary.loading]이 선택 수에 따라 단일 상세·여러 항목 요약을 고르고, 읽은 속성·측정 결과가 표시 상태로 바뀌는지 검증하는 단위 테스트. */
class SelectionSummaryTest {

    @Test
    fun `항목 하나면 이름과 넘겨받은 규칙의 위치를 담고 속성·크기는 읽는 중인 단일 상세다`() {
        val file = item(path = "/storage/DCIM/photo.jpg", lastModified = "어제")

        val summary = SelectionSummary.loading(listOf(file), locationOf = { "/storage/DCIM" })

        assertEquals(
            SelectionSummary.Single(
                name = "photo.jpg",
                location = "/storage/DCIM",
                attributes = AttributesInfo.Loading,
                size = SizeInfo.Loading,
                childCount = null
            ),
            summary
        )
    }

    @Test
    fun `읽은 속성이 폴더면 폴더, 읽지 못하면 실패다`() {
        val folder = AttributesInfo.from(IoResult.Success(ItemAttributes(isDirectory = true, lastModifiedMillis = 0)), "Music")

        assertEquals(ItemKind.Folder, (folder as AttributesInfo.Known).kind)
        assertEquals(AttributesInfo.Failed("원인"), AttributesInfo.from(IoResult.ItemFailed("원인"), "Music"))
    }

    @Test
    fun `상위가 없는 경로의 위치는 항목 자신이 아니라 없음이다`() {
        val summary = SelectionSummary.loading(listOf(item("a.txt")), locationOf = { null }) as SelectionSummary.Single

        assertEquals(null, summary.location)
    }

    @Test
    fun `확장자가 없거나 숨김 파일의 첫 점이면 확장자 없음이다`() {
        assertEquals(ItemKind.File(null), kindOf("README"))
        assertEquals(ItemKind.File(null), kindOf(".bashrc"))
        assertEquals(ItemKind.File(null), kindOf("trailing."))
        assertEquals(ItemKind.File("gz"), kindOf("archive.tar.gz"))
    }

    @Test
    fun `항목이 없거나 둘 이상이면 개수 요약이다`() {
        assertEquals(SelectionSummary.Multiple(0, SizeInfo.Loading), SelectionSummary.loading(emptyList(), locationOf = { null }))
        assertEquals(
            SelectionSummary.Multiple(2, SizeInfo.Loading),
            SelectionSummary.loading(listOf(item("/a"), item("/b")), locationOf = { null })
        )
    }

    @Test
    fun `정확한 측정은 크기 문자열로, 하한은 읽지 못한 수와 함께, 실패는 메시지로 바뀐다`() {
        assertEquals(
            SizeInfo.Exact(readableFileSize_td(3_048)),
            SizeInfo.from(IoResult.Success(SizeMeasurement.Complete(3_048)))
        )
        assertEquals(
            SizeInfo.AtLeast(readableFileSize_td(10), 2),
            SizeInfo.from(IoResult.Success(SizeMeasurement.LowerBound(10, listOf(FailedItem("a", null), FailedItem("b", null)))))
        )
        assertEquals(SizeInfo.Failed("원인"), SizeInfo.from(IoResult.ItemFailed("원인")))
    }

    @Test
    fun `하위 항목 수 실패는 0개가 아니라 실패로 바뀐다`() {
        assertEquals(ChildCountInfo.Known(0), ChildCountInfo.from(IoResult.Success(0)))
        assertEquals(ChildCountInfo.Failed("원인"), ChildCountInfo.from(IoResult.ItemFailed("원인")))
    }

    private fun kindOf(name: String): ItemKind =
        (AttributesInfo.from(IoResult.Success(ItemAttributes(isDirectory = false, lastModifiedMillis = 0)), name) as AttributesInfo.Known).kind

    private fun item(path: String, isDirectory: Boolean = false, lastModified: String = "") = FileHolderItem(
        path = path, isDirectory = isDirectory, name = path.substringAfterLast('/'), size = "", lastModified = lastModified,
        sizeBytes = null, lastModifiedMillis = 0L
    )
}
