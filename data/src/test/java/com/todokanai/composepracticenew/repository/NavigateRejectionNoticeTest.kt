package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.datasource.LocalFileDataSource
import com.todokanai.composepracticenew.model.NavigateResult
import com.todokanai.composepracticenew.model.OperationNotice
import com.todokanai.composepracticenew.model.OperationNoticeKind
import com.todokanai.composepracticenew.model.SortMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [FileExplorerRepositoryImpl]이 거부한 이동을 직접 알리는지 검증하는 단위 테스트(#279).
 * 부르는 곳이 결과를 확인하지 않아도 거부가 알려져야 한다.
 */
class NavigateRejectionNoticeTest {

    /** 이동할 수 있는 폴더를 만드는 임시 폴더. */
    @get:Rule val temp = TemporaryFolder()

    /** 탐색 저장소가 거부를 맡기는 알림함. */
    private val notices = OperationNotices()

    /** 로컬 경로만 다루는 탐색 저장소. */
    private val nav = FileExplorerRepositoryImpl(
        DataConverter(),
        sortPreference = object : SortPreferenceRepository {
            override val sortBy = MutableStateFlow(SortMode.BY_DEFAULT)
            override fun saveSortBy(value: SortMode) { sortBy.value = value }
        },
        appScope = TestScope(),
        started = SharingStarted.Lazily,
        fileSystems = setOf(LocalPathAwareFileSystem(LocalFileDataSource(), FakeStorageVolumes())),
        notices = notices
    )

    @Test
    fun `읽을 수 없는 폴더로의 이동을 거부하고 그 폴더 이름으로 알린다`() {
        // 로컬 경로 규칙은 '/'로 이름을 나누므로 어느 OS에서든 '/' 구분 경로로 넘긴다
        val missing = File(temp.root, "missing_279").invariantSeparatorsPath

        val result = runBlocking { nav.navigate(missing) }

        assertEquals(NavigateResult.Rejected, result)
        assertEquals(null, nav.currentPath.value)
        assertEquals(listOf(OperationNotice(OperationNoticeKind.FAILURE, "폴더를 열 수 없습니다 (missing_279)")), noticesReported())
    }

    @Test
    fun `이동하면 알리지 않는다`() {
        val folder = temp.newFolder("readable").invariantSeparatorsPath

        val result = runBlocking { nav.navigate(folder) }

        assertEquals(NavigateResult.Navigated, result)
        assertEquals(folder, nav.currentPath.value)
        assertEquals(emptyList<OperationNotice>(), noticesReported())
    }

    /** 알림함에 남은 알림. 넣은 순서다. */
    private fun noticesReported(): List<OperationNotice> = notices.pending.value.map { it.content }
}
