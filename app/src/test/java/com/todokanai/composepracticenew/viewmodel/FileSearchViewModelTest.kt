package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.RecordingFileLauncher
import com.todokanai.composepracticenew.RecordingOperationNotices
import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.composepracticenew.model.NavigateResult
import com.todokanai.composepracticenew.model.SearchBatch
import com.todokanai.composepracticenew.model.SearchQuery
import com.todokanai.composepracticenew.model.StorageVolumeInfo
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.StoragePathRules
import com.todokanai.composepracticenew.repository.FileSearchRepository
import com.todokanai.composepracticenew.repository.StorageVolumeRepository
import com.todokanai.composepracticenew.ui.model.SearchStatus
import com.todokanai.composepracticenew.ui.model.SearchUiState
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.usecase.FileSearchUseCase
import com.todokanai.composepracticenew.usecase.OpenFileUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import com.todokanai.composepracticenew.model.FileHolderItem as DomainFileHolderItem

/**
 * [FileSearchViewModel]이 검색어 입력이 멈춘 뒤에만 찾고, 찾는 대로 결과를 더해 다 훑으면 끝났음을 표시하며,
 * 검색어가 바뀌면 이전 결과를 버리고, 저장소가 없거나 읽지 못한 항목이 있거나 상한에서 멈추면 결과 없음·다 훑음과 구별하며,
 * 결과를 누르면 목록 행과 같은 규칙으로 이동·열기를 하고, 이동이 거부되면 검색을 닫지 않고 알리는지 검증하는 단위 테스트.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FileSearchViewModelTest {

    /** 검색어 입력 멈춤(디바운스) 대기를 가상 시간으로 진행시키는 스케줄러. */
    private val scheduler = TestCoroutineScheduler()
    /** 검색마다 테스트가 결과를 보내는 저장소. */
    private val repo = ChannelSearchRepository()
    /** 검색할 저장소 볼륨 목록. */
    private val volumes = MutableStateFlow(listOf(StorageVolumeInfo("/storage/0", 0, 0)))
    /** 이동한 경로를 기록하는 탐색 저장소. */
    private val nav = RecordingNavigator()
    /** 외부 앱으로 연 파일 경로를 기록하는 파일 열기. */
    private val launcher = RecordingFileLauncher()
    private lateinit var viewModel: FileSearchViewModel

    /** 검색 결과로 쓸 폴더. */
    private val music = DomainFileHolderItem(path = "/storage/0/music", isDirectory = true, name = "music", sizeBytes = null, lastModifiedMillis = 0L)
    /** 검색 결과로 쓸 파일. */
    private val song = DomainFileHolderItem(path = "/storage/0/music/song.mp3", isDirectory = false, name = "song.mp3", sizeBytes = 3, lastModifiedMillis = 0L)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        val storage = object : StorageVolumeRepository {
            override val storageList = volumes
            override fun load() {}
        }
        viewModel = FileSearchViewModel(FileSearchUseCase(repo, storage, nav, nav), OpenFileUseCase(nav, launcher, RecordingOperationNotices()))
        viewModel.open()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `입력이 멈추기 전에는 찾지 않고, 멈추면 찾는 대로 결과를 더해 다 훑으면 끝난다`() {
        viewModel.onQueryChange("mus")
        scheduler.advanceTimeBy(299)
        scheduler.runCurrent()
        assertEquals(0, repo.searches.size)
        assertEquals(SearchStatus.Searching, viewModel.state.value?.status)

        passDebounce()
        assertEquals(listOf("mus"), repo.searches.map { it.first.text })
        repo.send(SearchBatch(listOf(music), emptyList()))
        awaitState { it.results.size == 1 }
        repo.send(SearchBatch(listOf(song), emptyList()))
        repo.finish()

        val finished = awaitState { it.status == SearchStatus.Finished }
        assertEquals(listOf(music.path, song.path), finished.results.map { it.item.path })
        assertEquals(listOf("/storage/0", "/storage/0/music"), finished.results.map { it.location })
    }

    @Test
    fun `검색어를 바꾸면 이전 결과를 바로 지우고 이전 검색의 늦은 결과는 더하지 않는다`() {
        viewModel.onQueryChange("mus")
        passDebounce()
        repo.send(SearchBatch(listOf(music), emptyList()))
        awaitState { it.results.size == 1 }
        val previous = repo.searches.last().second

        viewModel.onQueryChange("song")
        assertEquals(SearchUiState(status = SearchStatus.Searching), viewModel.state.value)
        previous.trySend(SearchBatch(listOf(music), emptyList()))
        passDebounce()
        repo.send(SearchBatch(listOf(song), emptyList()))
        repo.finish()

        assertEquals(listOf(song.path), awaitState { it.status == SearchStatus.Finished }.results.map { it.item.path })
    }

    @Test
    fun `빈 검색어는 찾지 않는다`() {
        viewModel.onQueryChange("  ")
        passDebounce()

        assertEquals(0, repo.searches.size)
        assertEquals(SearchUiState(), viewModel.state.value)
    }

    @Test
    fun `검색할 저장소가 없으면 결과 없음이 아니라 저장소 없음이다`() {
        volumes.value = emptyList()

        viewModel.onQueryChange("mus")
        passDebounce()

        assertEquals(SearchStatus.NoStorage, viewModel.state.value?.status)
    }

    @Test
    fun `읽지 못한 항목 수를 묶음마다 더한다`() {
        viewModel.onQueryChange("mus")
        passDebounce()
        repo.send(SearchBatch(emptyList(), listOf(FailedItem("/storage/0/Android/data", null))))
        repo.send(SearchBatch(emptyList(), listOf(FailedItem("/storage/0/a", null), FailedItem("/storage/0/b", null))))
        repo.finish()

        val finished = awaitState { it.status == SearchStatus.Finished }
        assertEquals(3, finished.unreadableCount)
    }

    @Test
    fun `결과가 상한을 넘으면 다 훑은 것이 아니라 상한에서 멈춘 것으로 표시한다`() {
        viewModel.onQueryChange("mus")
        passDebounce()
        val many = List(FileSearchUseCase.MAX_RESULTS + 1) {
            DomainFileHolderItem(path = "/storage/0/mus$it", isDirectory = false, name = "mus$it", sizeBytes = 0, lastModifiedMillis = 0L)
        }
        repo.send(SearchBatch(many, emptyList()))

        val stopped = awaitState { it.status == SearchStatus.LimitReached }
        assertEquals(FileSearchUseCase.MAX_RESULTS, stopped.results.size)
    }

    @Test
    fun `폴더 결과를 누르면 그 폴더로 이동하고 검색을 닫는다`() {
        val result = searchedResults().first { it.isDirectory }

        viewModel.onResultClick(result, SelectMode.Default)

        assertEquals(listOf(music.path), nav.navigated)
        assertNull(viewModel.state.value)
        assertEquals("", viewModel.query)
    }

    @Test
    fun `폴더 결과로 이동이 거부되면 검색과 결과를 그대로 두고 열 수 없음을 알린다`() {
        nav.rejected += music.path
        val result = searchedResults().first { it.isDirectory }
        val before = viewModel.state.value

        viewModel.onResultClick(result, SelectMode.Default)

        awaitUnopenableFolder()
        assertEquals(emptyList<String>(), nav.navigated)
        assertEquals(before, viewModel.state.value)
        assertEquals("mus", viewModel.query)
    }

    @Test
    fun `위치 열기가 거부되면 검색과 결과를 그대로 두고 열 수 없음을 알린다`() {
        nav.rejected += "/storage/0/music"
        val file = searchedResults().first { !it.isDirectory }
        val before = viewModel.state.value

        viewModel.onOpenLocation(file)

        awaitUnopenableFolder()
        assertEquals(before, viewModel.state.value)
    }

    @Test
    fun `기본 모드에서 파일 결과를 누르면 열고 검색은 그대로 둔다`() {
        val result = searchedResults().first { !it.isDirectory }

        viewModel.onResultClick(result, SelectMode.Default)

        assertEquals(listOf(song.path), launcher.launched)
        assertEquals(2, viewModel.state.value?.results?.size)
    }

    @Test
    fun `목적지 확인 중에는 파일 결과를 열지 않고 폴더 결과로는 이동한다`() {
        val results = searchedResults()

        viewModel.onResultClick(results.first { !it.isDirectory }, SelectMode.LocalOperation.Copy)
        assertEquals(emptyList<String>(), launcher.launched)

        viewModel.onResultClick(results.first { it.isDirectory }, SelectMode.Download)
        assertEquals(listOf(music.path), nav.navigated)
    }

    @Test
    fun `위치 열기는 항목이 들어 있는 폴더로 이동하고 검색을 닫는다`() {
        val file = searchedResults().first { !it.isDirectory }

        viewModel.onOpenLocation(file)

        assertEquals(listOf("/storage/0/music"), nav.navigated)
        assertNull(viewModel.state.value)
    }

    @Test
    fun `검색을 닫으면 검색어와 결과를 버리고 닫힌 동안의 입력은 무시한다`() {
        viewModel.onQueryChange("mus")
        viewModel.close()

        viewModel.onQueryChange("song")
        passDebounce()

        assertNull(viewModel.state.value)
        assertEquals("", viewModel.query)
        assertEquals(0, repo.searches.size)
    }

    /** "mus"로 찾아 폴더와 파일 결과를 받고 다 훑은 뒤의 결과 항목을 돌려준다. */
    private fun searchedResults(): List<com.todokanai.composepracticenew.ui.model.FileHolderItem> {
        viewModel.onQueryChange("mus")
        passDebounce()
        repo.send(SearchBatch(listOf(music, song), emptyList()))
        repo.finish()
        return awaitState { it.status == SearchStatus.Finished }.results.map { it.item }
    }

    /** 검색어 입력 멈춤 대기를 지나 검색을 시작시킨다. */
    private fun passDebounce() {
        scheduler.advanceTimeBy(300)
        scheduler.runCurrent()
    }

    /** 결과 변환이 다른 스레드에서 끝나 [predicate]를 만족하는 상태가 될 때까지 기다린다. */
    private fun awaitState(predicate: (SearchUiState) -> Boolean): SearchUiState = runBlocking {
        withTimeout(5_000) { viewModel.state.first { it != null && predicate(it) }!! }
    }

    /** 이동이 거부됐다는 알림이 올 때까지 기다린다. 오지 않으면 시간 초과로 실패한다. */
    private fun awaitUnopenableFolder() = runBlocking {
        withTimeout(5_000) { viewModel.unopenableFolder.first() }
    }

    /**
     * 검색마다 채널을 하나 만들어, 테스트가 그 채널로 결과 묶음을 보내고 닫아 검색을 끝내게 하는 저장소.
     */
    private class ChannelSearchRepository : FileSearchRepository {
        /** 받은 검색어와 그 검색의 결과 채널. 받은 순서다. */
        val searches = mutableListOf<Pair<SearchQuery, Channel<SearchBatch>>>()

        override fun search(roots: List<String>, query: SearchQuery): Flow<SearchBatch> {
            val channel = Channel<SearchBatch>(Channel.UNLIMITED)
            searches += query to channel
            return channel.receiveAsFlow()
        }

        /** 마지막 검색에 [batch]를 보낸다. */
        fun send(batch: SearchBatch) {
            searches.last().second.trySend(batch)
        }

        /** 마지막 검색을 다 훑은 것으로 끝낸다. */
        fun finish() {
            searches.last().second.close()
        }
    }

    /** 이동한 경로를 기록하고, 상위 폴더를 마지막 '/' 앞까지로 구하는 탐색 저장소. [rejected]에 든 경로로는 이동을 거부한다. */
    private class RecordingNavigator : FileNavigatorRepository, StoragePathRules {
        /** [navigate]로 이동한 경로. 이동한 순서다. 거부한 경로는 넣지 않는다. */
        val navigated = mutableListOf<String>()
        /** 읽을 수 없는 폴더처럼 이동을 거부할 경로. */
        val rejected = mutableSetOf<String>()

        override val currentPath = MutableStateFlow<String?>(null)
        override val listingRequest = MutableStateFlow<ListingRequest?>(null)
        override val directoryListing = MutableStateFlow<DirectoryListing>(DirectoryListing.NoPath)

        override suspend fun navigate(path: String): NavigateResult {
            if (path in rejected) return NavigateResult.Rejected
            navigated += path
            return NavigateResult.Navigated
        }

        override fun refresh() {}
        override fun getParentPath(path: String): String? = path.substringBeforeLast('/', "").ifEmpty { null }
        override fun displayNameOf(path: String): String = path.substringAfterLast('/').ifEmpty { path }
    }
}
