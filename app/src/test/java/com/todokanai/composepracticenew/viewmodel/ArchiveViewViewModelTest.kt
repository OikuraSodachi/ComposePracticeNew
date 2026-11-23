package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.todokanai.composepracticenew.InMemorySortPreference
import com.todokanai.composepracticenew.ViewableArchives
import com.todokanai.composepracticenew.model.ArchiveEntry
import com.todokanai.composepracticenew.model.ArchiveFolder
import com.todokanai.composepracticenew.model.ArchivePasswordPrompt
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.SortMode
import com.todokanai.composepracticenew.ui.model.ArchiveEntryRow
import com.todokanai.composepracticenew.ui.model.ArchiveEntryRowKind
import com.todokanai.composepracticenew.usecase.ArchiveViewUseCase
import com.todokanai.composepracticenew.usecase.SortModeUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [ArchiveViewViewModel]이 압축 파일 목록을 한 번 읽어 정렬해 보이고, 폴더 안팎으로 오가며 루트에서 뒤로가면 화면을 닫고,
 * 읽기 실패를 빈 폴더가 아니라 실패로 보이며, 목록 암호화 압축 파일의 비밀번호를 묻고 취소하면 화면을 닫는지 검증하는 단위 테스트.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveViewViewModelTest {

    /** 압축 파일 저장소. 읽을 때마다 경로를 남긴다. */
    private val archives = ViewableArchives()
    /** 정렬 설정. */
    private val sortPreference = InMemorySortPreference()

    /** 루트에 파일 둘(b, a)과 폴더 하나(dir — 안에 in.txt)를 둔 트리. */
    private val tree = ArchiveFolder(listOf(
        ArchiveEntry.File("b.txt", 2, null, isEncrypted = false),
        ArchiveEntry.Folder("dir", null, ArchiveFolder(listOf(ArchiveEntry.File("in.txt", 1, null, isEncrypted = true)))),
        ArchiveEntry.File("a.txt", 1, null, isEncrypted = false),
    ))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        archives.contents = { IoResult.Success(tree) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `열면 목록을 읽어 정렬 기준대로 보인다`() {
        val viewModel = viewModel()

        assertEquals(listOf("dir", "a.txt", "b.txt"), loadedRows(viewModel).map { it.name })
        assertEquals(listOf("/s/a.zip"), archives.reads)
    }

    @Test
    fun `정렬 기준이 바뀌면 다시 읽지 않고 다시 정렬한다`() {
        val viewModel = viewModel()
        loadedRows(viewModel)

        sortPreference.saveSortBy(SortMode.BY_NAME_DESCENDING)

        awaitRows(viewModel) { rows -> rows.map { it.name } == listOf("dir", "b.txt", "a.txt") }
        assertEquals(1, archives.reads.size)
    }

    @Test
    fun `폴더 행을 누르면 들어가고 뒤로가면 나오며 루트에서 뒤로가면 화면을 닫는다`() {
        val viewModel = viewModel()
        val folder = loadedRows(viewModel).first { it.kind == ArchiveEntryRowKind.FOLDER }

        viewModel.openEntry(folder)
        val inner = awaitRows(viewModel) { rows -> rows.singleOrNull()?.name == "in.txt" }
        assertEquals(ArchiveEntryRowKind.ENCRYPTED_FILE, inner.single().kind)
        assertEquals(listOf("dir"), viewModel.uiState.value.folderNames)

        var closed = false
        viewModel.onBackPressed { closed = true }
        assertEquals(listOf("dir", "a.txt", "b.txt"), awaitRows(viewModel) { rows -> rows.size == 3 }.map { it.name })
        assertFalse(closed)

        viewModel.onBackPressed { closed = true }
        assertTrue(closed)
    }

    @Test
    fun `파일 행을 누르면 아무것도 하지 않는다`() {
        val viewModel = viewModel()
        val file = loadedRows(viewModel).first { it.kind == ArchiveEntryRowKind.FILE }

        viewModel.openEntry(file)

        assertEquals(emptyList<String>(), viewModel.uiState.value.folderNames)
    }

    @Test
    fun `읽지 못하면 빈 폴더가 아니라 실패를 보이고 다시 시도하면 다시 읽는다`() {
        archives.contents = { IoResult.ItemFailed(FailureReason.UnreadableArchive("a.zip")) }
        val viewModel = viewModel()

        val failed = await(viewModel) { it.content is ArchiveViewContent.Failed }.content as ArchiveViewContent.Failed
        assertTrue(failed.detail != null)

        archives.contents = { IoResult.Success(tree) }
        viewModel.retry()

        assertEquals(3, loadedRows(viewModel).size)
        assertEquals(2, archives.reads.size)
    }

    @Test
    fun `목록 암호화 압축 파일은 비밀번호를 묻고 답을 받아 읽는다`() {
        var received: String? = null
        archives.contents = { ask ->
            received = ask.passwordFor("a.zip", ArchivePasswordPrompt.FIRST)
            IoResult.Success(tree)
        }
        val viewModel = viewModel()

        val request = viewModel.passwordRequest.value!!
        assertEquals("a.zip", request.archiveName)
        assertEquals(ArchivePasswordPrompt.FIRST, request.prompt)

        viewModel.answerPassword(request.id, "secret")

        assertEquals(3, loadedRows(viewModel).size)
        assertEquals("secret", received)
        assertNull(viewModel.passwordRequest.value)
    }

    @Test
    fun `비밀번호 질문에서 취소하면 읽기를 끝내고 화면을 닫으라고 요청한다`() {
        archives.contents = { ask ->
            ask.passwordFor("a.zip", ArchivePasswordPrompt.FIRST)
            IoResult.Success(tree)
        }
        val viewModel = viewModel()

        viewModel.cancelPassword(viewModel.passwordRequest.value!!.id)

        runBlocking { withTimeout(TIMEOUT_MILLIS) { viewModel.closeRequest.first() } }
        assertNull(viewModel.passwordRequest.value)
        assertEquals(ArchiveViewContent.Loading, viewModel.uiState.value.content)
    }

    @Test
    fun `프로세스가 다시 시작된 뒤 저장한 폴더가 없으면 루트로 돌아간다`() {
        val viewModel = viewModel(SavedStateHandle(mapOf(ArchiveViewViewModel.ARG_ARCHIVE_PATH to "/s/a.zip", "folderNames" to arrayListOf("gone"))))

        assertEquals(3, loadedRows(viewModel).size)
        assertEquals(emptyList<String>(), viewModel.uiState.value.folderNames)
    }

    /** `/s/a.zip`을 보는 ViewModel을 만든다. */
    private fun viewModel(handle: SavedStateHandle = SavedStateHandle(mapOf(ArchiveViewViewModel.ARG_ARCHIVE_PATH to "/s/a.zip"))) =
        ArchiveViewViewModel(handle, ArchiveViewUseCase(archives), SortModeUseCase(sortPreference))

    /** 목록을 다 읽은 상태가 될 때까지 기다려 그 행들을 돌려준다. */
    private fun loadedRows(viewModel: ArchiveViewViewModel) = awaitRows(viewModel) { true }

    /** 다 읽은 상태의 행들이 [predicate]를 만족할 때까지 기다린다. */
    private fun awaitRows(viewModel: ArchiveViewViewModel, predicate: (List<ArchiveEntryRow>) -> Boolean) =
        (await(viewModel) { state -> (state.content as? ArchiveViewContent.Loaded)?.rows?.let(predicate) == true }.content as ArchiveViewContent.Loaded).rows

    /** 화면 상태가 [predicate]를 만족할 때까지 기다린다. 정렬은 다른 스레드에서 하므로 실제 시간으로 기다린다. */
    private fun await(viewModel: ArchiveViewViewModel, predicate: (ArchiveViewUiState) -> Boolean): ArchiveViewUiState =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { viewModel.uiState.first(predicate) } }

    private companion object {
        /** 상태를 기다리는 최대 시간. */
        const val TIMEOUT_MILLIS = 5_000L
    }
}
