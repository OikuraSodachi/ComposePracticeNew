package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.DestinationConflicts
import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.ListingCompleteness
import com.todokanai.composepracticenew.model.ListingFailureReason
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.RenameConflict
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.ProgressRepository
import com.todokanai.composepracticenew.usecase.ConflictCheckUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ConflictCheckUseCase]가 현재 조회 요청의 확인된 목록으로만 이름 충돌을 판정하고,
 * 조회 중(직전 목록 표시 포함)·실패·이전 요청의 목록이면 판정하지 않고 거부를 알리는지 검증하는 단위 테스트(#215).
 */
class ConflictCheckVerificationTest {

    private val scope = CoroutineScope(Job())
    private val progressRepo = RecordingProgressRepository()
    private val nav = FakeNavigator()
    private val navigator = FileNavigatorUseCase(nav)
    private val useCase = ConflictCheckUseCase(FileOperationOrchestrator(NoOpNotifier, progressRepo, scope)) { UNVERIFIED }

    private val current = ListingRequest("/a", generation = 2L)
    private val previous = ListingRequest("/a", generation = 1L)
    private val incoming = listOf(item("/src/x.txt"))

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `현재 요청의 조회를 마친 목록이면 같은 이름의 항목을 충돌로 판정하고 그 목록의 경로를 목적지로 돌려준다`() {
        show(DirectoryListing.Loaded(current, listOf(item("/a/x.txt"), item("/a/y.txt"))))

        assertEquals(DestinationConflicts("/a", incoming, ListingCompleteness.COMPLETE), useCase.findCopyMoveConflicts(incoming, navigator) { it.name })
        assertTrue(progressRepo.errors.isEmpty())
    }

    @Test
    fun `조회를 마친 빈 폴더면 충돌이 없다`() {
        show(DirectoryListing.Loaded(current, emptyList()))

        assertEquals(DestinationConflicts("/a", emptyList<FileHolderItem>(), ListingCompleteness.COMPLETE), useCase.findCopyMoveConflicts(incoming, navigator) { it.name })
    }

    @Test
    fun `확인되지 않은 빈 목록이면 충돌 없음으로 단정하지 않고 확인되지 않았음을 판정 결과에 담는다`() {
        show(DirectoryListing.Loaded(current, emptyList(), ListingCompleteness.UNCONFIRMED))

        assertEquals(
            DestinationConflicts("/a", emptyList<FileHolderItem>(), ListingCompleteness.UNCONFIRMED),
            useCase.findCopyMoveConflicts(incoming, navigator) { it.name }
        )
        assertTrue(progressRepo.errors.isEmpty())
    }

    @Test
    fun `조회에 실패했으면 판정하지 않고 거부를 알린다`() {
        show(DirectoryListing.Failed(current, ListingFailureReason.UNREADABLE, "550"))

        assertUnverified(useCase.findCopyMoveConflicts(incoming, navigator) { it.name })
    }

    @Test
    fun `같은 경로를 다시 조회하는 중이면 직전 목록이 있어도 판정하지 않는다`() {
        show(DirectoryListing.Loading(current, stale = DirectoryListing.Loaded(previous, listOf(item("/a/old.txt")))))

        assertUnverified(useCase.findCopyMoveConflicts(incoming, navigator) { it.name })
    }

    @Test
    fun `목록이 이전 요청의 것이면 경로가 같아도 판정하지 않는다`() {
        show(DirectoryListing.Loaded(previous, emptyList()))

        assertUnverified(useCase.findUnzipConflicts(incoming, navigator) { it.name })
    }

    @Test
    fun `이름 변경도 확인된 목록이 없으면 판정하지 않고 거부를 알린다`() {
        show(DirectoryListing.Loaded(previous, listOf(item("/a/y.txt"))))

        assertUnverified(useCase.findRenameConflict("/a/x.txt", false, "y.txt", navigator, ignoreCase = false))
    }

    @Test
    fun `이름 변경은 현재 요청의 목록으로 겹치는 방식을 판정한다`() {
        show(DirectoryListing.Loaded(current, listOf(item("/a/x.txt"), item("/a/y.txt"))))

        assertEquals(RenameConflict.FILE, useCase.findRenameConflict("/a/x.txt", false, "y.txt", navigator, ignoreCase = false))
    }

    @Test
    fun `이름을 바꿀 항목이 확인된 목록에 없으면 다른 폴더의 목록이므로 판정하지 않는다`() {
        show(DirectoryListing.Loaded(current, listOf(item("/a/y.txt"))))

        assertUnverified(useCase.findRenameConflict("/other/x.txt", false, "y.txt", navigator, ignoreCase = false))
    }

    @Test
    fun `입력 중 미리 보기는 확인된 목록이 없어도 거부를 알리지 않는다`() {
        show(DirectoryListing.Loading(current))

        assertNull(useCase.previewRenameConflict("/a/x.txt", false, "y.txt", navigator, ignoreCase = false))
        assertTrue(progressRepo.errors.isEmpty())
    }

    /** 현재 요청을 [current]로 두고 목록 상태를 [listing]으로 정한다. */
    private fun show(listing: DirectoryListing) {
        nav.listingRequest.value = current
        nav.directoryListing.value = listing
    }

    /** 판정하지 않고(null) 거부를 한 번 알렸는지 확인한다. */
    private fun assertUnverified(result: Any?) {
        assertNull(result)
        assertEquals(listOf(UNVERIFIED), progressRepo.errors)
    }

    /** [path]의 파일 항목을 만든다. */
    private fun item(path: String) =
        FileHolderItem(path = path, isDirectory = false, name = path.substringAfterLast('/'), sizeBytes = 1L, lastModifiedMillis = 0L)

    private companion object {
        /** 테스트용 미확인 거부 메시지. */
        const val UNVERIFIED = "목록 미확인"
    }

    /** 현재 요청과 목록 상태를 테스트가 정하는 테스트용 탐색 저장소. */
    private class FakeNavigator : FileNavigatorRepository {
        override val currentPath = MutableStateFlow<String?>(null)
        override val dirTree: Flow<List<FileEntry>> = emptyFlow()
        override val listingRequest = MutableStateFlow<ListingRequest?>(null)
        override val directoryListing = MutableStateFlow<DirectoryListing>(DirectoryListing.NoPath)
        override suspend fun navigate(path: String) {}
        override fun refresh() {}
        override fun getParentPath(path: String): String? = null
    }

    /** 방출된 오류 메시지를 기록하는 테스트용 진행 저장소. */
    private class RecordingProgressRepository : ProgressRepository {
        val errors = mutableListOf<String>()
        override val progressMap: StateFlow<Map<Int, ProgressState>> = MutableStateFlow(emptyMap())
        override val operationErrors: SharedFlow<String> = MutableSharedFlow()
        override val operationCompletions: SharedFlow<String> = MutableSharedFlow()
        override fun setProgressState(instanceId: Int, state: ProgressState) {}
        override fun removeProgress(instanceId: Int) {}
        override fun emitError(message: String) { errors += message }
        override fun emitCompletion(message: String) {}
    }

    /** 알림을 표시하지 않는 테스트용 알림기. */
    private object NoOpNotifier : FileOperationNotifier {
        override fun progressNoti(state: ProgressState, instanceId: Int) {}
        override fun completedNotification(title: String, message: String, operationType: FileOperationType?, notifId: Int) {}
        override fun cancelNotification(notifId: Int) {}
    }
}
