package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.DestinationConflicts
import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ListingFailureReason
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.composepracticenew.model.NavigateResult
import com.todokanai.composepracticenew.model.RenameConflict
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.StoragePathRules
import com.todokanai.composepracticenew.usecase.ConflictCheckUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ConflictCheckUseCase]가 현재 조회 요청의 확인된 목록으로만 이름 충돌을 판정하고,
 * 조회 중(직전 목록 표시 포함)·실패·이전 요청의 목록이면 판정하지 않고 거부를 알리는지 검증하는 단위 테스트(#215).
 */
class ConflictCheckVerificationTest {

    /** 넣은 실패·완료 메시지를 기록하는 메시지 저장소. */
    private val notices = RecordingOperationNotices()
    private val nav = FakeNavigator()
    private val navigator = FileNavigatorUseCase(nav, nav)
    /** 대소문자만 다른 이름을 같은 항목으로 보는 목적지 저장소의 이름 비교 규칙. */
    private val nameMatching = RecordingCaseInsensitiveNameMatching()
    private val useCase = ConflictCheckUseCase(notices, nameMatching) { UNVERIFIED }

    private val current = ListingRequest("/a", generation = 2L)
    private val previous = ListingRequest("/a", generation = 1L)
    private val incoming = listOf(item("/src/x.txt"))

    @Test
    fun `현재 요청의 조회를 마친 목록이면 같은 이름의 항목을 충돌로 판정하고 그 목록의 경로를 목적지로 돌려준다`() {
        show(DirectoryListing.Loaded(current, listOf(item("/a/x.txt"), item("/a/y.txt")), isComplete = true))

        assertEquals(DestinationConflicts("/a", incoming), useCase.findCopyMoveConflicts(incoming, navigator) { it.name })
        assertTrue(notices.failures.isEmpty())
    }

    @Test
    fun `복사·이동은 목적지 저장소가 같은 항목으로 다루는 이름도 충돌로 판정하고 판정한 목록의 경로로 규칙을 고른다`() {
        show(DirectoryListing.Loaded(current, listOf(item("/a/readme.txt")), isComplete = true))
        val upperCase = listOf(item("/src/README.TXT"))

        assertEquals(DestinationConflicts("/a", upperCase), useCase.findCopyMoveConflicts(upperCase, navigator) { it.name })
        assertEquals(setOf("/a"), nameMatching.locations.toSet())
    }

    @Test
    fun `이름 변경은 그 폴더 저장소가 같은 항목으로 다루는 다른 항목과 겹치면 충돌로 판정한다`() {
        show(DirectoryListing.Loaded(current, listOf(item("/a/x.txt"), item("/a/y.txt")), isComplete = true))

        assertEquals(RenameConflict.FILE, useCase.findRenameConflict("/a/x.txt", false, "Y.TXT", navigator))
        assertEquals(RenameConflict.NONE, useCase.findRenameConflict("/a/x.txt", false, "X.TXT", navigator))
        assertEquals(setOf("/a"), nameMatching.locations.toSet())
    }

    @Test
    fun `이름 변경은 이름이 정확히 같은 다른 종류 항목만 막고 비교 값만 같은 다른 종류 항목은 막지 않는다`() {
        show(DirectoryListing.Loaded(current, listOf(item("/a/x.txt"), folder("/a/y")), isComplete = true))

        assertEquals(RenameConflict.TYPE_MISMATCH, useCase.findRenameConflict("/a/x.txt", false, "y", navigator))
        assertEquals(RenameConflict.NONE, useCase.findRenameConflict("/a/x.txt", false, "Y", navigator))
    }

    @Test
    fun `입력 중 미리 보기도 그 항목의 폴더 저장소 규칙으로 판정한다`() {
        show(DirectoryListing.Loaded(current, listOf(item("/a/x.txt"), item("/a/y.txt")), isComplete = true))

        assertEquals(RenameConflict.FILE, useCase.previewRenameConflict("/a/x.txt", false, "Y.TXT", navigator))
        assertEquals(setOf("/a"), nameMatching.locations.toSet())
    }

    @Test
    fun `같은 목록으로 반복 판정하면 목록 항목의 비교 값을 다시 구하지 않는다`() {
        val listing = DirectoryListing.Loaded(current, listOf(item("/a/x.txt"), item("/a/y.txt")), isComplete = true)
        show(listing)

        useCase.previewRenameConflict("/a/x.txt", false, "Y", navigator)
        useCase.previewRenameConflict("/a/x.txt", false, "Y.", navigator)
        // 항목 2개는 한 번만, 입력한 이름은 판정마다 구한다
        assertEquals(4, nameMatching.locations.size)

        show(DirectoryListing.Loaded(current, listing.items + item("/a/z.txt"), isComplete = true))
        useCase.previewRenameConflict("/a/x.txt", false, "Y.T", navigator)
        // 새 목록이면 항목 3개의 비교 값을 다시 구한다
        assertEquals(8, nameMatching.locations.size)
    }

    @Test
    fun `옆 항목 기준 이름 제안도 그 폴더 저장소 규칙으로 겹침을 본다`() {
        show(DirectoryListing.Loaded(current, listOf(item("/a/x.txt"), item("/a/X.ZIP")), isComplete = true))

        assertEquals("x (1)", useCase.suggestNameBeside("/a/x.txt", "x", ".zip", navigator))
        assertEquals(setOf("/a"), nameMatching.locations.toSet())
    }

    @Test
    fun `조회를 마친 빈 폴더면 충돌이 없다`() {
        show(DirectoryListing.Loaded(current, emptyList(), isComplete = true))

        assertEquals(DestinationConflicts("/a", emptyList<FileHolderItem>()), useCase.findCopyMoveConflicts(incoming, navigator) { it.name })
    }

    @Test
    fun `조회에 실패했으면 판정하지 않고 거부를 알린다`() {
        show(DirectoryListing.Failed(current, ListingFailureReason.UNREADABLE, "550"))

        assertUnverified(useCase.findCopyMoveConflicts(incoming, navigator) { it.name })
    }

    @Test
    fun `읽지 못한 항목이 있는 목록이면 판정하지 않고 거부를 알린다`() {
        // 빠진 항목과 같은 이름을 묻지 않고 덮어쓰지 않도록(#255)
        show(DirectoryListing.Loaded(current, listOf(item("/a/y.txt")), isComplete = false))

        assertUnverified(useCase.findCopyMoveConflicts(incoming, navigator) { it.name })
    }

    @Test
    fun `같은 경로를 다시 조회하는 중이면 직전 목록이 있어도 판정하지 않는다`() {
        show(DirectoryListing.Loading(current, stale = DirectoryListing.Loaded(previous, listOf(item("/a/old.txt")), isComplete = true)))

        assertUnverified(useCase.findCopyMoveConflicts(incoming, navigator) { it.name })
    }

    @Test
    fun `이름 변경도 확인된 목록이 없으면 판정하지 않고 거부를 알린다`() {
        show(DirectoryListing.Loaded(previous, listOf(item("/a/y.txt")), isComplete = true))

        assertUnverified(useCase.findRenameConflict("/a/x.txt", false, "y.txt", navigator))
    }

    @Test
    fun `이름 변경은 현재 요청의 목록으로 겹치는 방식을 판정한다`() {
        show(DirectoryListing.Loaded(current, listOf(item("/a/x.txt"), item("/a/y.txt")), isComplete = true))

        assertEquals(RenameConflict.FILE, useCase.findRenameConflict("/a/x.txt", false, "y.txt", navigator))
    }

    @Test
    fun `이름을 바꿀 항목이 확인된 목록에 없으면 다른 폴더의 목록이므로 판정하지 않는다`() {
        show(DirectoryListing.Loaded(current, listOf(item("/a/y.txt")), isComplete = true))

        assertUnverified(useCase.findRenameConflict("/other/x.txt", false, "y.txt", navigator))
    }

    @Test
    fun `입력 중 미리 보기는 확인된 목록이 없어도 거부를 알리지 않는다`() {
        show(DirectoryListing.Loading(current))

        assertNull(useCase.previewRenameConflict("/a/x.txt", false, "y.txt", navigator))
        assertTrue(notices.failures.isEmpty())
    }

    @Test
    fun `새 항목 이름 제안은 겹치는 이름이 있으면 압축 저장소와 같은 형식으로 번호를 붙인다`() {
        show(DirectoryListing.Loaded(current, listOf(item("/a/New folder"), item("/a/new folder (1)")), isComplete = true))

        assertEquals("New folder (2)", useCase.suggestNewEntryName("New folder", "", navigator))
        assertEquals(setOf("/a"), nameMatching.locations.toSet())
        assertTrue(notices.failures.isEmpty())
    }

    @Test
    fun `확장자를 붙여 겹침을 보고 제안은 확장자를 뺀 이름으로 돌려준다`() {
        show(DirectoryListing.Loaded(current, listOf(item("/a/x.txt"), item("/a/x.zip")), isComplete = true))

        assertEquals("x (1)", useCase.suggestNameBeside("/a/x.txt", "x", ".zip", navigator))
    }

    @Test
    fun `이름 제안은 확인된 목록이 없으면 번호 없이 제안하고 거부를 알리지 않는다`() {
        show(DirectoryListing.Loading(current, stale = DirectoryListing.Loaded(previous, listOf(item("/a/New folder")), isComplete = true)))

        assertEquals("New folder", useCase.suggestNewEntryName("New folder", "", navigator))
        assertTrue(notices.failures.isEmpty())
    }

    @Test
    fun `옆 항목 기준 이름 제안은 그 항목이 목록에 없으면 다른 폴더의 목록이므로 번호 없이 제안한다`() {
        show(DirectoryListing.Loaded(current, listOf(item("/a/x.zip")), isComplete = true))

        assertEquals("x", useCase.suggestNameBeside("/b/x.txt", "x", ".zip", navigator))
    }

    /** 현재 요청을 [current]로 두고 목록 상태를 [listing]으로 정한다. */
    private fun show(listing: DirectoryListing) {
        nav.listingRequest.value = current
        nav.directoryListing.value = listing
    }

    /** 판정하지 않고(null) 거부를 한 번 알렸는지 확인한다. */
    private fun assertUnverified(result: Any?) {
        assertNull(result)
        assertEquals(listOf(UNVERIFIED), notices.failures)
    }

    /** [path]의 파일 항목을 만든다. */
    private fun item(path: String) =
        FileHolderItem(path = path, isDirectory = false, name = path.substringAfterLast('/'), sizeBytes = 1L, lastModifiedMillis = 0L)

    /** [path]의 폴더 항목을 만든다. */
    private fun folder(path: String) = item(path).copy(isDirectory = true)

    private companion object {
        /** 테스트용 미확인 거부 메시지. */
        const val UNVERIFIED = "목록 미확인"
    }

    /** 현재 요청과 목록 상태를 테스트가 정하는 테스트용 탐색 저장소. */
    private class FakeNavigator : FileNavigatorRepository, StoragePathRules {
        override val currentPath = MutableStateFlow<String?>(null)
        override val listingRequest = MutableStateFlow<ListingRequest?>(null)
        override val directoryListing = MutableStateFlow<DirectoryListing>(DirectoryListing.NoPath)
        override suspend fun navigate(path: String) = NavigateResult.Navigated
        override fun refresh() {}
        override fun getParentPath(path: String): String? = null
        override fun displayNameOf(path: String): String = path.substringAfterLast('/').ifEmpty { path }
    }
}
