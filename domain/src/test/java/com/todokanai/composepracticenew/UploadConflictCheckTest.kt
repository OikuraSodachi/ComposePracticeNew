package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.FolderEntryNames
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ItemAttributes
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.composepracticenew.model.NameCollision
import com.todokanai.composepracticenew.model.NavigateResult
import com.todokanai.composepracticenew.model.SizeMeasurement
import com.todokanai.composepracticenew.model.UploadConflicts
import com.todokanai.composepracticenew.repository.FileInfoRepository
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.StoragePathRules
import com.todokanai.composepracticenew.usecase.ConflictCheckUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.UploadConflictCheckUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [UploadConflictCheckUseCase]가 목적지에 이미 있는 이름과의 겹침에 더해, 함께 올리는 항목끼리(폴더 안쪽 포함)
 * 목적지 저장소의 규칙으로 같은 항목이 될 수 있는 이름을 찾는지 검증하는 단위 테스트(#323).
 */
class UploadConflictCheckTest {

    private val notices = RecordingOperationNotices()
    private val nav = FakeNavigator()
    private val navigator = FileNavigatorUseCase(nav, nav)
    /** 대소문자만 다른 이름을 같은 항목으로 보는 목적지 저장소의 이름 비교 규칙. */
    private val nameMatching = RecordingCaseInsensitiveNameMatching()
    private val sourceInfo = FakeSourceInfo()
    private val useCase = UploadConflictCheckUseCase(ConflictCheckUseCase(notices, nameMatching) { UNVERIFIED }, sourceInfo, nameMatching)

    private val destination = ListingRequest("/r", generation = 1L)

    @Test
    fun `처음 선택한 항목끼리 목적지에서 같은 이름이 될 수 있으면 묶어 돌려준다`() = runBlocking {
        show(emptyList())
        val items = listOf(item("/src/a.txt"), item("/src/A.txt"), item("/src/b.txt"))

        val result = findConflicts(items)

        assertEquals(UploadConflicts("/r", emptyList<FileHolderItem>(), listOf(NameCollision(null, listOf("a.txt", "A.txt")))), result)
    }

    @Test
    fun `폴더 안쪽 항목끼리의 겹침은 그 폴더의 표시용 경로와 함께 돌려주고 원본 경로로 읽는다`() = runBlocking {
        show(emptyList())
        sourceInfo.entries = listOf(
            FolderEntryNames("photos", listOf("x.jpg", "sub")),
            FolderEntryNames("photos/sub", listOf("Y.jpg", "y.jpg", "z.jpg")),
        )
        val items = listOf(item("/src/photos"))

        val result = findConflicts(items)

        assertEquals(listOf(NameCollision("photos/sub", listOf("Y.jpg", "y.jpg"))), result?.collisions)
        assertEquals(listOf(listOf("/src/photos")), sourceInfo.requests)
    }

    @Test
    fun `함께 올리는 항목끼리의 이름도 목적지 폴더의 규칙으로 비교한다`() = runBlocking {
        show(emptyList())
        sourceInfo.entries = listOf(FolderEntryNames("photos", listOf("x.jpg")))

        findConflicts(listOf(item("/src/photos"), item("/src/q.txt")))

        assertEquals(setOf("/r"), nameMatching.locations.toSet())
    }

    @Test
    fun `목적지에 이미 있는 이름과의 겹침은 따로 돌려준다`() = runBlocking {
        show(listOf(item("/r/a.txt")))
        val items = listOf(item("/src/a.txt"), item("/src/b.txt"))

        assertEquals(UploadConflicts("/r", listOf(items[0]), emptyList()), findConflicts(items))
    }

    @Test
    fun `목적지 목록을 확인하지 못했으면 폴더 안을 읽지 않고 거부를 알린다`() = runBlocking {
        nav.listingRequest.value = destination
        nav.directoryListing.value = DirectoryListing.Loading(destination)

        assertNull(findConflicts(listOf(item("/src/photos"))))
        assertEquals(listOf(UNVERIFIED), notices.failures)
        assertEquals(emptyList<List<String>>(), sourceInfo.requests)
    }

    /** [items]를 목적지에 올릴 때의 겹침을 판정한다. */
    private suspend fun findConflicts(items: List<FileHolderItem>) =
        useCase.findConflicts(items, navigator, nameOf = { it.name }, pathOf = { it.path })

    /** 목적지의 확인된 목록을 [items]로 정한다. */
    private fun show(items: List<FileHolderItem>) {
        nav.listingRequest.value = destination
        nav.directoryListing.value = DirectoryListing.Loaded(destination, items, isComplete = true)
    }

    /** [path]의 파일 항목을 만든다. */
    private fun item(path: String) =
        FileHolderItem(path = path, isDirectory = false, name = path.substringAfterLast('/'), sizeBytes = 1L, lastModifiedMillis = 0L)

    private companion object {
        /** 테스트용 미확인 거부 메시지. */
        const val UNVERIFIED = "목록 미확인"
    }

    /** 폴더 안 항목 이름을 테스트가 정하고, 읽기 요청을 기록하는 원본 저장소. */
    private class FakeSourceInfo : FileInfoRepository {
        /** [folderEntryNames]가 돌려줄 폴더별 항목 이름. */
        var entries = emptyList<FolderEntryNames>()

        /** 받은 읽기 요청의 경로 목록(받은 순서). */
        val requests = mutableListOf<List<String>>()

        override fun locationOf(path: String): String? = error("쓰지 않음")
        override suspend fun readAttributes(path: String): IoResult<ItemAttributes> = error("쓰지 않음")
        override suspend fun measureSize(paths: List<String>): IoResult<SizeMeasurement> = error("쓰지 않음")
        override suspend fun countChildren(path: String): IoResult<Int> = error("쓰지 않음")
        override suspend fun folderEntryNames(paths: List<String>): List<FolderEntryNames> {
            requests += paths
            return entries
        }
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
