package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.composepracticenew.ui.model.ListingUiState
import com.todokanai.composepracticenew.ui.model.ListingUiStateMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import com.todokanai.composepracticenew.model.FileHolderItem as DomainFileHolderItem

/**
 * [ListingUiStateMapper]가 같은 경로를 다시 조회하는 동안 직전 목록을 다시 조회 중 표시와 함께 보여주고,
 * 같은 domain 목록이면 화면용 항목을 다시 만들지 않는지 검증하는 단위 테스트(#215).
 */
class ListingUiStateMapperTest {

    private val mapper = ListingUiStateMapper(isLocalFile = false)
    private val loaded = DirectoryListing.Loaded(ListingRequest("ftp://h/a", 1L), listOf(domainItem("ftp://h/a/x.txt")))

    @Test
    fun `조회를 마친 목록은 다시 조회 중이 아닌 목록이다`() {
        val state = mapper.map(loaded) as ListingUiState.Loaded

        assertFalse(state.isRefreshing)
        assertEquals(listOf("ftp://h/a/x.txt"), state.items.map { it.path })
    }

    @Test
    fun `같은 경로를 다시 조회하는 중이면 직전 목록을 다시 조회 중 표시와 함께 보여준다`() {
        val before = mapper.map(loaded) as ListingUiState.Loaded

        val refreshing = mapper.map(DirectoryListing.Loading(ListingRequest("ftp://h/a", 2L), stale = loaded)) as ListingUiState.Loaded

        assertTrue(refreshing.isRefreshing)
        assertSame("같은 domain 목록이면 화면용 항목을 다시 만들지 않아야 함", before.items, refreshing.items)
    }

    @Test
    fun `직전 목록 없이 조회 중이면 조회 중 상태다`() {
        assertEquals(ListingUiState.Loading, mapper.map(DirectoryListing.Loading(ListingRequest("ftp://h/b", 3L))))
    }

    /** [path]의 domain 파일 항목을 만든다. */
    private fun domainItem(path: String) =
        DomainFileHolderItem(path = path, isDirectory = false, name = path.substringAfterLast('/'), sizeBytes = 1L, lastModifiedMillis = 0L)
}
