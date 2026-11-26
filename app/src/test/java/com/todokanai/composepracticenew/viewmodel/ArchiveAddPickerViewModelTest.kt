package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.todokanai.composepracticenew.compose.frag.pickerClickActionOf
import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.composepracticenew.model.NavigateResult
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.StoragePathRules
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.ItemClickAction
import com.todokanai.composepracticenew.ui.model.SelectionState
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.todokanai.composepracticenew.model.FileHolderItem as DomainItem

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveAddPickerViewModelTest {

    /** 고르는 화면 전용 탐색. `/s`가 저장소 볼륨 루트다. */
    private val nav = PickerNavigator()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        nav.items["/s/dir"] = listOf(domainFile("/s/dir/a.zip"), domainFile("/s/dir/x.txt"), domainFolder("/s/dir/sub"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `열면 압축 파일이 있는 폴더로 이동하고 넣을 폴더와 함께 열린다`() {
        val viewModel = viewModel()

        viewModel.open(listOf("docs"))

        assertTrue(viewModel.isOpen.value)
        assertEquals(listOf("/s/dir"), nav.navigations)
    }

    @Test
    fun `시작 폴더를 읽을 수 없으면 열지 않는다`() {
        nav.rejected += "/s/dir"
        val viewModel = viewModel()

        viewModel.open(emptyList())

        assertFalse(viewModel.isOpen.value)
    }

    @Test
    fun `이미 열려 있으면 다시 열어도 고르던 폴더를 시작 폴더로 되돌리지 않는다`() {
        val viewModel = viewModel()
        viewModel.open(emptyList())
        viewModel.openFolder(folder("/s/dir/sub"))

        viewModel.open(emptyList())

        assertEquals(listOf("/s/dir", "/s/dir/sub"), nav.navigations)
    }

    @Test
    fun `파일을 누르면 고르고 확인하면 고른 항목과 넣을 폴더를 꺼내며 화면을 닫는다`() {
        val viewModel = viewModel()
        viewModel.open(listOf("docs"))
        viewModel.select(file("/s/dir/x.txt"))
        viewModel.select(file("/s/dir/a.zip"))

        val picked = viewModel.submit()

        assertEquals(listOf("/s/dir/x.txt", "/s/dir/a.zip"), picked?.selection?.items?.map { it.path })
        assertEquals(listOf("docs"), picked?.targetFolder)
        assertFalse(viewModel.isOpen.value)
        assertEquals(SelectionState.Default, viewModel.selectionState.value)
        assertNull(viewModel.submit())
    }

    @Test
    fun `고른 항목이 없으면 꺼내지 않고 열린 채로 둔다`() {
        val viewModel = viewModel()
        viewModel.open(emptyList())

        assertNull(viewModel.submit())
        assertTrue(viewModel.isOpen.value)
    }

    @Test
    fun `닫혀 있으면 고른 항목이 있어도 꺼내지 않는다`() {
        val viewModel = viewModel()
        viewModel.startMultiSelect(file("/s/dir/x.txt"))

        assertNull(viewModel.submit())
    }

    @Test
    fun `뒤로가기는 선택을 비우고 바깥 폴더로 가며 볼륨 루트에서 화면을 닫는다`() {
        val viewModel = viewModel()
        viewModel.open(emptyList())
        viewModel.select(file("/s/dir/x.txt"))

        viewModel.onBackPressed()
        assertEquals(SelectionState.Default, viewModel.selectionState.value)
        assertEquals(listOf("/s/dir"), nav.navigations)

        viewModel.onBackPressed()
        assertEquals(listOf("/s/dir", "/s"), nav.navigations)
        assertTrue(viewModel.isOpen.value)

        viewModel.onBackPressed()
        assertFalse(viewModel.isOpen.value)
    }

    @Test
    fun `다중 선택은 다시 조회한 목록에 없는 항목을 뺀다`() {
        val viewModel = viewModel()
        viewModel.open(emptyList())
        viewModel.select(file("/s/dir/x.txt"))
        viewModel.select(file("/s/dir/a.zip"))

        nav.relist("/s/dir", listOf(domainFile("/s/dir/a.zip")))

        assertEquals(listOf("/s/dir/a.zip"), viewModel.selectionState.value.selected.map { it.path })
    }

    @Test
    fun `프로세스가 다시 시작돼 열린 채 복원되면 저장한 폴더로 다시 이동한다`() {
        val first = viewModel()
        first.open(listOf("docs"))
        first.openFolder(folder("/s/dir/sub"))
        val saved = SavedStateHandle(first.savedKeys())

        val restored = viewModel(saved)

        assertTrue(restored.isOpen.value)
        assertEquals("/s/dir/sub", nav.navigations.last())
    }

    @Test
    fun `복원할 폴더를 읽을 수 없으면 고르는 화면을 닫는다`() {
        val first = viewModel()
        first.open(emptyList())
        first.openFolder(folder("/s/dir/sub"))
        val saved = SavedStateHandle(first.savedKeys())
        nav.rejected += "/s/dir/sub"

        val restored = viewModel(saved)

        assertFalse(restored.isOpen.value)
    }

    @Test
    fun `고르는 화면에서 누르면 기본 모드의 폴더는 들어가고 파일과 다중 선택 중의 항목은 고른다`() {
        assertEquals(ItemClickAction.OPEN, pickerClickActionOf(isMultiSelect = false, folder("/s/dir/sub")))
        assertEquals(ItemClickAction.TOGGLE, pickerClickActionOf(isMultiSelect = false, file("/s/dir/x.txt")))
        assertEquals(ItemClickAction.TOGGLE, pickerClickActionOf(isMultiSelect = true, folder("/s/dir/sub")))
    }

    /** 이 테스트의 저장 상태로 ViewModel을 만든다. 압축 파일은 `/s/dir/a.zip`이다. */
    private fun viewModel(handle: SavedStateHandle = SavedStateHandle(mapOf(ArchiveViewViewModel.ARG_ARCHIVE_PATH to ARCHIVE))) =
        ArchiveAddPickerViewModel(handle, FileNavigatorUseCase(nav, nav)).also { handles[it] = handle }

    /** 만든 ViewModel마다 넘긴 저장 상태 — 프로세스 재시작을 흉내 내려고 그 내용을 새 저장 상태로 옮긴다. */
    private val handles = HashMap<ArchiveAddPickerViewModel, SavedStateHandle>()

    /** 이 ViewModel의 저장 상태에 든 값을 모두 꺼낸다. */
    private fun ArchiveAddPickerViewModel.savedKeys(): Map<String, Any?> {
        val handle = checkNotNull(handles[this])
        return handle.keys().associateWith { handle.get<Any?>(it) }
    }

    /** [path]의 로컬 파일 항목. */
    private fun file(path: String) = item(path, isDirectory = false)

    /** [path]의 로컬 폴더 항목. */
    private fun folder(path: String) = item(path, isDirectory = true)

    /** [path]의 로컬 항목을 만든다. */
    private fun item(path: String, isDirectory: Boolean) =
        FileHolderItem(path = path, isDirectory = isDirectory, name = path.substringAfterLast("/"), size = "", lastModified = "", sizeBytes = null, lastModifiedMillis = 0L)

    /** [path]의 domain 파일 항목. */
    private fun domainFile(path: String) = DomainItem(path, isDirectory = false, name = path.substringAfterLast("/"), sizeBytes = 0L, lastModifiedMillis = 0L)

    /** [path]의 domain 폴더 항목. */
    private fun domainFolder(path: String) = DomainItem(path, isDirectory = true, name = path.substringAfterLast("/"), sizeBytes = null, lastModifiedMillis = 0L)

    private companion object {
        /** 항목을 넣을 압축 파일. */
        const val ARCHIVE = "/s/dir/a.zip"
    }
}

/**
 * 이동하면 현재 경로와 새 조회 요청을 정하고 [items]의 목록을 조회한 것처럼 하는 테스트용 탐색 저장소. `/s`가 저장소 볼륨 루트다.
 */
private class PickerNavigator : FileNavigatorRepository, StoragePathRules {
    override val currentPath = MutableStateFlow<String?>(null)
    override val listingRequest = MutableStateFlow<ListingRequest?>(null)
    override val directoryListing = MutableStateFlow<DirectoryListing>(DirectoryListing.NoPath)

    /** 경로마다 조회하면 나올 항목. */
    val items = HashMap<String, List<DomainItem>>()

    /** 읽을 수 없어 이동을 거부할 경로. */
    val rejected = HashSet<String>()

    /** 받은 이동 요청 경로를 순서대로(거부한 것 포함). */
    val navigations = mutableListOf<String>()

    /** 조회 요청 세대. */
    private var generation = 0L

    override suspend fun navigate(path: String): NavigateResult {
        navigations += path
        if (path in rejected) return NavigateResult.Rejected
        relist(path, items[path].orEmpty())
        return NavigateResult.Navigated
    }

    /**
     * [path]를 새 요청으로 조회해 [listed]가 나온 것처럼 한다.
     * @param path 조회한 경로
     * @param listed 조회 결과 항목
     */
    fun relist(path: String, listed: List<DomainItem>) {
        currentPath.value = path
        val request = ListingRequest(path, ++generation)
        listingRequest.value = request
        directoryListing.value = DirectoryListing.Loaded(request, listed, unreadableCount = 0)
    }

    override fun refresh() {}
    override fun getParentPath(path: String): String? = if (path == "/s") null else path.substringBeforeLast('/')
    override fun displayNameOf(path: String): String = path.substringAfterLast('/')
}
