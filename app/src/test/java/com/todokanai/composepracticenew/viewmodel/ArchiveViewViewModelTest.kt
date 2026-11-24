package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.todokanai.composepracticenew.InMemorySortPreference
import com.todokanai.composepracticenew.TEST_LISTING
import com.todokanai.composepracticenew.FakeNavigator
import com.todokanai.composepracticenew.GrantedProtection
import com.todokanai.composepracticenew.NoOpFailureRecorder
import com.todokanai.composepracticenew.NoOpNotifier
import com.todokanai.composepracticenew.RecordingArchiveAddRepository
import com.todokanai.composepracticenew.RecordingOperationNotices
import com.todokanai.composepracticenew.RecordingProgressReporter
import com.todokanai.composepracticenew.UnexpectedQuestions
import com.todokanai.composepracticenew.ViewableArchives
import com.todokanai.composepracticenew.model.ArchiveEntry
import com.todokanai.composepracticenew.model.ArchiveEntryKey
import com.todokanai.composepracticenew.model.ArchiveEntrySelection
import com.todokanai.composepracticenew.model.ArchiveFolder
import com.todokanai.composepracticenew.model.ArchivePasswordPrompt
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.SortMode
import com.todokanai.composepracticenew.ui.model.ArchiveEntryRow
import com.todokanai.composepracticenew.ui.model.ArchiveEntryRowKind
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.LocalSameStorageOperation
import com.todokanai.composepracticenew.ui.model.LocalTransferMode
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.ui.model.SelectionState
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.usecase.ArchiveAddUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.ArchiveViewUseCase
import com.todokanai.composepracticenew.usecase.SortModeUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
 * 읽기 실패를 빈 폴더가 아니라 실패로 보이며, 목록 암호화 압축 파일의 비밀번호를 묻고 취소하면 화면을 닫고,
 * 다중 선택 중에는 행을 누르면 폴더로 들어가지 않고 선택을 토글하며 뒤로가기가 선택만 비우는지 검증하는 단위 테스트.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveViewViewModelTest {

    /** 압축 파일 저장소. 읽을 때마다 경로를 남긴다. */
    private val archives = ViewableArchives()
    /** 정렬 설정. */
    private val sortPreference = InMemorySortPreference()
    /** 압축 파일에 추가 저장소. `/s/a.zip`만 넣을 수 있다. */
    private val archiveAdds = RecordingArchiveAddRepository(setOf("/s/a.zip"))
    /** 작업 실행 스코프. 작업이 그 자리에서 끝나도록 Unconfined로 둔다. */
    private val operationScope = CoroutineScope(Job() + Dispatchers.Unconfined)
    /** 추가 뒤 다시 조회하는 로컬 탐색. */
    private val localNav = FakeNavigator()

    /** 루트에 파일 둘(b, a)과 폴더 하나(dir — 안에 in.txt)를 둔 트리. */
    private val tree = ArchiveFolder(listOf(
        ArchiveEntry.File("b.txt", 2, null, isEncrypted = false, ArchiveEntryKey.Item(0)),
        ArchiveEntry.Folder(
            "dir", null,
            ArchiveFolder(listOf(ArchiveEntry.File("in.txt", 1, null, isEncrypted = true, ArchiveEntryKey.Item(1)))),
            ArchiveEntryKey.Folder("dir")
        ),
        ArchiveEntry.File("a.txt", 1, null, isEncrypted = false, ArchiveEntryKey.Item(2)),
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

        viewModel.onRowClick(folder)
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

        viewModel.onRowClick(file)

        assertEquals(emptyList<String>(), viewModel.uiState.value.folderNames)
    }

    @Test
    fun `다중 선택 중에는 폴더 행을 눌러도 들어가지 않고 선택을 토글한다`() {
        val viewModel = viewModel()
        val rows = loadedRows(viewModel)
        val folder = rows.first { it.kind == ArchiveEntryRowKind.FOLDER }
        val file = rows.first { it.name == "a.txt" }

        viewModel.startMultiSelect(file)
        viewModel.onRowClick(folder)
        assertEquals(listOf("dir", "a.txt"), selectedNames(viewModel))
        assertEquals(emptyList<String>(), viewModel.uiState.value.folderNames)

        viewModel.onRowClick(file)
        assertEquals(listOf("dir"), selectedNames(viewModel))
    }

    @Test
    fun `선택은 이름이 아니라 키로 들어 이름이 같은 파일을 따로 고른다`() {
        archives.contents = { IoResult.Success(ArchiveFolder(listOf(
            ArchiveEntry.File("same.txt", 1, null, isEncrypted = false, ArchiveEntryKey.Item(0)),
            ArchiveEntry.File("same.txt", 2, null, isEncrypted = false, ArchiveEntryKey.Item(1)),
        ))) }
        val viewModel = viewModel()
        val second = loadedRows(viewModel).single { it.key == ArchiveEntryKey.Item(1) }

        viewModel.startMultiSelect(second)

        val selected = await(viewModel) { it.selection is ArchiveViewSelection.MultiSelect }.selection as ArchiveViewSelection.MultiSelect
        assertEquals(listOf(ArchiveEntryKey.Item(1)), selected.selected.map { it.key })
    }

    @Test
    fun `정렬이 바뀌어도 선택이 이어진다`() {
        val viewModel = viewModel()
        viewModel.startMultiSelect(loadedRows(viewModel).first { it.name == "a.txt" })

        sortPreference.saveSortBy(SortMode.BY_NAME_DESCENDING)

        awaitRows(viewModel) { rows -> rows.map { it.name } == listOf("dir", "b.txt", "a.txt") }
        assertEquals(listOf("a.txt"), selectedNames(viewModel))
    }

    @Test
    fun `모두 선택으로 비워도 다중 선택은 유지하고 뒤로가기는 선택만 비운다`() {
        val viewModel = viewModel()
        val rows = loadedRows(viewModel)
        viewModel.startMultiSelect(rows.first())

        viewModel.replaceSelection(emptyList())
        assertEquals(emptyList<String>(), selectedNames(viewModel))

        var closed = false
        viewModel.onBackPressed { closed = true }
        assertFalse(closed)
        assertEquals(ArchiveViewSelection.None, await(viewModel) { it.selection == ArchiveViewSelection.None }.selection)

        viewModel.replaceSelection(rows)
        assertEquals(ArchiveViewSelection.None, viewModel.uiState.value.selection)
    }

    @Test
    fun `복사로 꺼내면 지금 폴더와 고른 키와 목록 식별값을 담고 기본 모드로 돌아간다`() {
        val viewModel = viewModel()
        viewModel.onRowClick(loadedRows(viewModel).first { it.kind == ArchiveEntryRowKind.FOLDER })
        viewModel.startMultiSelect(awaitRows(viewModel) { rows -> rows.singleOrNull()?.name == "in.txt" }.single())

        val selection = viewModel.submitCopy()

        assertEquals(ArchiveEntrySelection("/s/a.zip", TEST_LISTING, listOf("dir"), setOf(ArchiveEntryKey.Item(1))), selection)
        assertEquals(ArchiveViewSelection.None, await(viewModel) { it.selection == ArchiveViewSelection.None }.selection)
    }

    @Test
    fun `다중 선택이 아니거나 비었으면 복사로 꺼내지 않고 선택을 그대로 둔다`() {
        val viewModel = viewModel()
        val rows = loadedRows(viewModel)
        assertNull(viewModel.submitCopy())

        viewModel.startMultiSelect(rows.first())
        viewModel.replaceSelection(emptyList())

        assertNull(viewModel.submitCopy())
        assertTrue(viewModel.uiState.value.selection is ArchiveViewSelection.MultiSelect)
    }

    @Test
    fun `다시 읽으면 다중 선택을 끝낸다`() {
        val viewModel = viewModel()
        viewModel.startMultiSelect(loadedRows(viewModel).first())

        viewModel.retry()

        loadedRows(viewModel)
        assertEquals(ArchiveViewSelection.None, viewModel.uiState.value.selection)
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

        assertEquals(Unit, viewModel.closeRequest.value)
        assertNull(viewModel.passwordRequest.value)
        assertEquals(ArchiveViewContent.Loading, viewModel.uiState.value.content)
    }

    @Test
    fun `넣을 수 있는 압축 파일은 목록을 다 읽은 뒤에만 추가할 수 있다`() {
        archives.contents = { IoResult.ItemFailed(FailureReason.UnreadableArchive("a.zip")) }
        val failed = viewModel()
        archives.contents = { IoResult.Success(tree) }
        val loaded = viewModel()
        val other = viewModel(SavedStateHandle(mapOf(ArchiveViewViewModel.ARG_ARCHIVE_PATH to "/s/b.rar")))

        assertFalse(await(failed) { it.content is ArchiveViewContent.Failed }.canAdd)
        assertTrue(await(loaded) { it.content is ArchiveViewContent.Loaded }.canAdd)
        assertFalse(await(other) { it.content is ArchiveViewContent.Loaded }.canAdd)
    }

    @Test
    fun `지금 폴더에 로컬 선택을 넣고 선택을 비우며 끝나면 목록과 로컬 목록을 다시 읽는다`() {
        val viewModel = viewModel()
        viewModel.onRowClick(loadedRows(viewModel).first { it.kind == ArchiveEntryRowKind.FOLDER })
        awaitRows(viewModel) { rows -> rows.singleOrNull()?.name == "in.txt" }
        val selection = addingSelection("/local/x.txt", "/local/y.txt")

        viewModel.addHere(selection::submit)

        assertEquals(listOf(Triple(setOf("/local/x.txt", "/local/y.txt"), "/s/a.zip", listOf("dir"))), archiveAdds.calls)
        assertEquals(SelectionState.Default, selection.state.value)
        awaitRows(viewModel) { true }
        assertEquals(2, archives.reads.size)
        assertEquals(1, localNav.refreshCount)
    }

    @Test
    fun `넣을 수 없는 압축 파일이면 로컬 선택을 꺼내지 않는다`() {
        val viewModel = viewModel(SavedStateHandle(mapOf(ArchiveViewViewModel.ARG_ARCHIVE_PATH to "/s/b.rar")))
        loadedRows(viewModel)
        val selection = addingSelection("/local/x.txt")

        viewModel.addHere(selection::submit)

        assertEquals(emptyList<Any>(), archiveAdds.calls)
        assertEquals(SelectMode.AddToArchive, (selection.state.value as SelectionState.Operation).operation)
    }

    @Test
    fun `프로세스가 다시 시작된 뒤 저장한 폴더가 없으면 루트로 돌아간다`() {
        val viewModel = viewModel(SavedStateHandle(mapOf(ArchiveViewViewModel.ARG_ARCHIVE_PATH to "/s/a.zip", "folderNames" to arrayListOf("gone"))))

        assertEquals(3, loadedRows(viewModel).size)
        assertEquals(emptyList<String>(), viewModel.uiState.value.folderNames)
    }

    /** `/s/a.zip`을 보는 ViewModel을 만든다. */
    private fun viewModel(handle: SavedStateHandle = SavedStateHandle(mapOf(ArchiveViewViewModel.ARG_ARCHIVE_PATH to "/s/a.zip"))) =
        ArchiveViewViewModel(handle, ArchiveViewUseCase(archives), SortModeUseCase(sortPreference), archiveAddUseCase(), FileNavigatorUseCase(localNav, localNav))

    /** 이 테스트의 가짜 저장소로 압축 파일에 추가 UseCase를 만든다. */
    private fun archiveAddUseCase(): ArchiveAddUseCase {
        val orchestrator = FileOperationOrchestrator(NoOpNotifier, RecordingProgressReporter(), RecordingOperationNotices(), GrantedProtection, UnexpectedQuestions, NoOpFailureRecorder, operationScope)
        return ArchiveAddUseCase(orchestrator, archiveAdds)
    }

    /** 로컬 다중 선택에서 [paths]를 압축 파일에 추가할 곳을 찾는 중인 선택 상태를 만든다. */
    private fun addingSelection(vararg paths: String) = SelectionStateHolder<LocalSameStorageOperation, LocalTransferMode>().apply {
        paths.forEachIndexed { index, path -> if (index == 0) startMultiSelect(localItem(path)) else add(localItem(path)) }
        enterOperation(SelectMode.AddToArchive)
    }

    /** [path]의 로컬 파일 항목을 만든다. */
    private fun localItem(path: String) =
        FileHolderItem(path = path, isDirectory = false, name = path.substringAfterLast("/"), size = "", lastModified = "", sizeBytes = null, lastModifiedMillis = 0L)

    /** 다중 선택 상태가 될 때까지 기다려 선택된 행의 이름을 화면 순서로 돌려준다. */
    private fun selectedNames(viewModel: ArchiveViewViewModel): List<String> =
        (await(viewModel) { it.selection is ArchiveViewSelection.MultiSelect }.selection as ArchiveViewSelection.MultiSelect).selected.map { it.name }

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
