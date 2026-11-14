package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ListingFailureReason
import com.todokanai.composepracticenew.model.SortMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [DirectoryListingNavigation]이 같은 경로로 다시 이동해도(재연결 등) 새 조회 요청을 만들어 목록을 다시 조회하고(#215),
 * 공유가 멈추면 남아 있는 목록이 현재 요청의 목록으로 쓰이지 않도록 요청을 바꾸며,
 * 초기 경로로 생성하면 생성 중에 바로 공유를 시작해도 초기 요청을 조회하는지(초기화 순서) 검증하는 단위 테스트.
 * 보유자가 생성 중인 자신의 필드를 조회 방법으로 넘기지 않는 것(#245)은 보유자 쪽 구조(의존 객체의 함수를 넘김)가 지킨다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DirectoryListingNavigationTest {

    private val sortPreference = object : SortPreferenceRepository {
        override val sortBy = MutableStateFlow(SortMode.BY_DEFAULT)
        override fun saveSortBy(value: SortMode) { sortBy.value = value }
    }
    /** 다음 조회가 연결 끊김으로 실패해야 하면 true. */
    private var disconnected = false
    /** 조회 횟수. */
    private var fetchCount = 0
    /** 테스트가 만든 공유 스코프. 테스트가 끝나면 취소한다. */
    private val sharingScopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() = sharingScopes.forEach { it.cancel() }

    @Test
    fun `연결이 끊겨 실패한 경로로 다시 이동하면 다시 조회해 실패 상태가 풀린다`() = runTest {
        val navigation = navigationIn(this)
        disconnected = true
        navigation.navigateTo("/root")
        runCurrent()
        assertEquals(ListingFailureReason.CONNECTION_LOST, (navigation.directoryListing.value as DirectoryListing.Failed).reason)

        disconnected = false
        navigation.navigateTo("/root")
        runCurrent()

        assertEquals(DirectoryListing.Loaded(navigation.listingRequest.value!!, emptyList(), unreadableCount = 0), navigation.directoryListing.value)
        assertEquals(2, fetchCount)
    }

    @Test
    fun `새로고침은 같은 경로의 새 요청이다`() = runTest {
        val navigation = navigationIn(this)
        navigation.navigateTo("/root")
        runCurrent()
        val before = navigation.listingRequest.value!!

        navigation.refresh()
        runCurrent()

        val after = navigation.listingRequest.value!!
        assertEquals(before.path, after.path)
        assertNotEquals(before, after)
        assertEquals(2, fetchCount)
    }

    @Test
    fun `공유가 멈추면 요청이 바뀌어 남아 있는 목록이 현재 요청의 목록이 아니게 된다`() = runTest {
        val navigation = navigationIn(this, SharingStarted.WhileSubscribed())
        val subscriber = launch { navigation.directoryListing.collect {} }
        navigation.navigateTo("/root")
        runCurrent()
        val loaded = navigation.directoryListing.value as DirectoryListing.Loaded
        assertEquals(navigation.listingRequest.value, loaded.request)

        subscriber.cancel()
        runCurrent()

        assertEquals("멈춘 동안 목록은 그대로 남는다", loaded, navigation.directoryListing.value)
        assertNotEquals("남은 목록은 현재 요청의 목록으로 확인되지 않아야 함", navigation.listingRequest.value, loaded.request)
        assertEquals("/root", navigation.listingRequest.value?.path)
    }

    @Test
    fun `초기 경로로 생성하면 생성 중에 바로 공유를 시작해도 초기 요청을 조회한다`() {
        val listed = mutableListOf<String>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        sharingScopes += scope

        // Eagerly + Unconfined — 생성자 안에서 공유가 시작돼 초기 경로를 곧바로 조회한다
        val navigation = DirectoryListingNavigation(
            DataConverter(), sortPreference, scope, SharingStarted.Eagerly, initialPath = "/start",
            listFiles = { path -> listed += path; IoResult.Success(ListedDirectory(emptyList(), unreadableCount = 0)) }
        )

        assertEquals(listOf("/start"), listed)
        assertEquals("/start", navigation.currentPath.value)
        assertEquals(DirectoryListing.Loaded(navigation.listingRequest.value!!, emptyList(), unreadableCount = 0), navigation.directoryListing.value)
    }

    /** 테스트 스케줄러를 쓰는 공유 스코프에서 경로 없이 시작하는 탐색 상태를 만든다. */
    private fun navigationIn(testScope: TestScope, started: SharingStarted = SharingStarted.Eagerly): DirectoryListingNavigation {
        val scope = CoroutineScope(StandardTestDispatcher(testScope.testScheduler))
        sharingScopes += scope
        return DirectoryListingNavigation(DataConverter(), sortPreference, scope, started, initialPath = null) { _ ->
            fetchCount++
            if (disconnected) IoResult.ConnectionLost("끊김") else IoResult.Success(ListedDirectory(emptyList(), unreadableCount = 0))
        }
    }
}
