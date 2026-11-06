package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.composepracticenew.model.NavigateResult
import com.todokanai.composepracticenew.model.PathSegment
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [FileNavigatorUseCase.dirTree]가 현재 경로에서 저장소의 부모 경로를 따라 루트까지 올라간 경로 단계를
 * 루트부터 순서대로 만들고, 경로가 없으면 빈 목록을 내는지 검증하는 단위 테스트(#227).
 */
class DirTreeTest {

    /** 현재 경로를 테스트가 정하는 탐색 저장소. */
    private val nav = FakeNavigator()
    /** 검증 대상. */
    private val useCase = FileNavigatorUseCase(nav)

    @Test
    fun `현재 경로가 없으면 빈 목록이다`() {
        assertEquals(emptyList<PathSegment>(), currentDirTree())
    }

    @Test
    fun `루트부터 현재 디렉터리까지 마지막 구간을 이름으로 담는다`() {
        nav.currentPath.value = "/storage/emulated/0"

        assertEquals(
            listOf(
                PathSegment("storage", "/storage"),
                PathSegment("emulated", "/storage/emulated"),
                PathSegment("0", "/storage/emulated/0"),
            ),
            currentDirTree()
        )
    }

    @Test
    fun `부모 경로는 저장소가 정한다`() {
        nav.parents = mapOf("ftp://host/dir" to "ftp://host")
        nav.currentPath.value = "ftp://host/dir"

        assertEquals(listOf(PathSegment("host", "ftp://host"), PathSegment("dir", "ftp://host/dir")), currentDirTree())
    }

    @Test
    fun `현재 경로가 바뀔 때마다 다시 계산하고 경로가 없어지면 빈 목록을 낸다`() = runBlocking {
        val emitted = mutableListOf<List<PathSegment>>()
        // Unconfined 수집기는 경로를 바꾸는 즉시 그 값을 받는다 — 연속으로 바꾼 값이 합쳐지지 않게
        val collector = launch(Dispatchers.Unconfined) { useCase.dirTree.toList(emitted) }

        nav.currentPath.value = "/a"
        nav.currentPath.value = "/a/b"
        nav.currentPath.value = null
        collector.cancel()

        assertEquals(
            listOf(
                emptyList(),
                listOf(PathSegment("a", "/a")),
                listOf(PathSegment("a", "/a"), PathSegment("b", "/a/b")),
                emptyList(),
            ),
            emitted
        )
    }

    @Test
    fun `이름은 저장소가 정한다`() {
        nav.parents = mapOf("doc:2" to "doc:1")
        nav.names = mapOf("doc:1" to "사진", "doc:2" to "여행")
        nav.currentPath.value = "doc:2"

        assertEquals(listOf(PathSegment("사진", "doc:1"), PathSegment("여행", "doc:2")), currentDirTree())
    }

    /** 지금 [FileNavigatorUseCase.dirTree]가 내는 경로 단계. */
    private fun currentDirTree(): List<PathSegment> = runBlocking { useCase.dirTree.first() }

    /** 현재 경로와 부모 경로 규칙을 테스트가 정하는 테스트용 탐색 저장소. */
    private class FakeNavigator : FileNavigatorRepository {
        /** 경로별 부모. null이면 '/' 기준으로 마지막 구간을 떼어 부모를 정한다. */
        var parents: Map<String, String>? = null
        override val currentPath = MutableStateFlow<String?>(null)
        override val listingRequest = MutableStateFlow<ListingRequest?>(null)
        override val directoryListing = MutableStateFlow<DirectoryListing>(DirectoryListing.NoPath)
        override suspend fun navigate(path: String) = NavigateResult.Navigated
        override fun refresh() {}
        override fun getParentPath(path: String): String? =
            parents?.get(path) ?: path.substringBeforeLast('/').takeIf { parents == null && it.isNotEmpty() && it != path }
        /** 경로별 표시 이름. 표에 없으면 '/' 기준으로 마지막 구간을 이름으로 쓴다. */
        var names: Map<String, String> = emptyMap()
        override fun displayNameOf(path: String): String = names[path] ?: path.substringAfterLast('/').ifEmpty { path }
    }
}
