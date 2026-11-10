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

/** [DirTreeHolder]가 현재 경로의 단계를 표시 항목으로 바꾸고, 누른 항목의 경로로 이동을 요청하는지 검증하는 단위 테스트. */
@OptIn(ExperimentalCoroutinesApi::class)
class DirTreeHolderTest {

    /** 이동 요청을 기록하는 탐색 저장소. */
    private val nav = RecordingNavigator()

    @Test
    fun `현재 경로의 루트부터 현재 디렉터리까지를 표시 항목으로 내보낸다`() = runTest(UnconfinedTestDispatcher()) {
        val holder = DirTreeHolder(FileNavigatorUseCase(nav, nav), backgroundScope)
        backgroundScope.launch { holder.dirTree.collect {} }

        nav.currentPath.value = "/a/b"

        assertEquals(listOf(DirectoryItem("a", "/a"), DirectoryItem("b", "/a/b")), holder.dirTree.value)
    }

    @Test
    fun `항목을 누르면 그 항목의 경로로 이동을 요청한다`() = runTest(UnconfinedTestDispatcher()) {
        val holder = DirTreeHolder(FileNavigatorUseCase(nav, nav), backgroundScope)

        holder.onDirClick(DirectoryItem("a", "/a"))

        assertEquals(listOf("/a"), nav.navigated)
    }

    /** 이동 요청을 기록하고 '/'로 경로를 나누는 테스트용 탐색 저장소. */
    private class RecordingNavigator : FileNavigatorRepository, StoragePathRules {
        override val currentPath = MutableStateFlow<String?>(null)
        override val listingRequest = MutableStateFlow<ListingRequest?>(null)
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
