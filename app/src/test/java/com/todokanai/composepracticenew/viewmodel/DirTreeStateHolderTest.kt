package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.composepracticenew.model.NavigateResult
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.StoragePathRules
import com.todokanai.composepracticenew.ui.model.DirectoryItem
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** [DirTreeStateHolder]가 현재 경로의 단계를 표시 항목으로 바꾸고, 누른 항목의 경로로 이동을 요청하는지 검증하는 단위 테스트. */
@OptIn(ExperimentalCoroutinesApi::class)
class DirTreeStateHolderTest {

    /** 이동 요청을 기록하는 탐색 저장소. */
    private val nav = RecordingNavigator()

    @Test
    fun `현재 경로의 루트부터 현재 디렉터리까지를 표시 항목으로 내보낸다`() = runTest(UnconfinedTestDispatcher()) {
        val holder = DirTreeStateHolder(FileNavigatorUseCase(nav, nav), backgroundScope)
        backgroundScope.launch { holder.dirTree.collect {} }

        nav.currentPath.value = "/a/b"

        assertEquals(listOf(DirectoryItem("a", "/a"), DirectoryItem("b", "/a/b")), holder.dirTree.value)
    }

    @Test
    fun `항목을 누르면 그 항목의 경로로 이동을 요청한다`() = runTest(UnconfinedTestDispatcher()) {
        val holder = DirTreeStateHolder(FileNavigatorUseCase(nav, nav), backgroundScope)

        holder.onDirClick(DirectoryItem("a", "/a"))

        assertEquals(listOf("/a"), nav.navigated)
    }

    @Test
    fun `구독 전에는 현재 경로와 무관하게 빈 목록을 내보낸다`() = runTest(UnconfinedTestDispatcher()) {
        nav.currentPath.value = "/a/b"

        val holder = DirTreeStateHolder(FileNavigatorUseCase(nav, nav), backgroundScope)

        assertEquals(emptyList<DirectoryItem>(), holder.dirTree.value)
    }

    /** 이동 요청을 기록하고 '/'로 경로를 나누는 테스트용 탐색 저장소. */
    private class RecordingNavigator : FileNavigatorRepository, StoragePathRules {
        /** 현재 경로. 테스트가 값을 직접 바꾼다. */
        override val currentPath = MutableStateFlow<String?>(null)
        /** 이 테스트가 검증하지 않는 목록 요청. */
        override val listingRequest = MutableStateFlow<ListingRequest?>(null)
        /** 이 테스트가 검증하지 않는 디렉터리 목록 상태. */
        override val directoryListing = MutableStateFlow<DirectoryListing>(DirectoryListing.NoPath)
        /** 이동을 요청받은 경로. */
        val navigated = mutableListOf<String>()

        override suspend fun navigate(path: String): NavigateResult {
            navigated += path
            return NavigateResult.Navigated
        }

        override fun refresh() {}
        override fun getParentPath(path: String): String? = path.substringBeforeLast('/', "").ifEmpty { null }
        override fun displayNameOf(path: String): String = path.substringAfterLast('/')
    }
}
