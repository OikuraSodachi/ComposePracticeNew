package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.ListingFailureReason
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [FileNavigatorUseCase.loadedEntryNames]가 현재 경로의 조회를 마친 목록에서만 이름을 돌려주고,
 * 조회 중·실패·다른 경로의 목록이면 충돌 판정에 쓸 수 없음을 null로 알리는지 검증하는 단위 테스트(#215).
 */
class FileNavigatorLoadedEntryNamesTest {

    private val nav = FakeNavigator()
    private val useCase = FileNavigatorUseCase(nav)

    @Test
    fun `현재 경로의 조회를 마친 목록이면 항목 이름을 돌려준다`() {
        nav.currentPath.value = "/a"
        nav.directoryListing.value = DirectoryListing.Loaded("/a", listOf(item("/a/x.txt"), item("/a/y.txt")))

        assertEquals(setOf("x.txt", "y.txt"), useCase.loadedEntryNames())
    }

    @Test
    fun `조회를 마친 빈 폴더면 빈 집합을 돌려준다`() {
        nav.currentPath.value = "/a"
        nav.directoryListing.value = DirectoryListing.Loaded("/a", emptyList())

        assertEquals(emptySet<String>(), useCase.loadedEntryNames())
    }

    @Test
    fun `조회에 실패했으면 null이다`() {
        nav.currentPath.value = "/a"
        nav.directoryListing.value = DirectoryListing.Failed("/a", ListingFailureReason.UNREADABLE, "550")

        assertNull(useCase.loadedEntryNames())
    }

    @Test
    fun `조회 중이면 null이다`() {
        nav.currentPath.value = "/a"
        nav.directoryListing.value = DirectoryListing.Loading("/a")

        assertNull(useCase.loadedEntryNames())
    }

    @Test
    fun `목록이 아직 이전 경로의 것이면 null이다`() {
        nav.currentPath.value = "/b"
        nav.directoryListing.value = DirectoryListing.Loaded("/a", listOf(item("/a/x.txt")))

        assertNull(useCase.loadedEntryNames())
    }

    /** [path]의 파일 항목을 만든다. */
    private fun item(path: String) =
        FileHolderItem(path = path, isDirectory = false, name = path.substringAfterLast('/'), sizeBytes = 1L, lastModifiedMillis = 0L)

    /** 현재 경로와 목록 상태를 테스트가 정하는 테스트용 탐색 저장소. */
    private class FakeNavigator : FileNavigatorRepository {
        override val currentPath = MutableStateFlow<String?>(null)
        override val dirTree: Flow<List<FileEntry>> = emptyFlow()
        override val directoryListing = MutableStateFlow<DirectoryListing>(DirectoryListing.NoPath)
        override suspend fun navigate(path: String) {}
        override fun refresh() {}
        override fun getParentPath(path: String): String? = null
    }
}
