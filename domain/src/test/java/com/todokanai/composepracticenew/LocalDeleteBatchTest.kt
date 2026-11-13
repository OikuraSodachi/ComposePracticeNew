package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.ArchiveFormat
import com.todokanai.composepracticenew.model.ExtractLayout
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.repository.FileActionRepository
import com.todokanai.composepracticenew.repository.ProgressRepository
import com.todokanai.composepracticenew.repository.WriteConflictResolver
import com.todokanai.composepracticenew.usecase.FileNameUseCase
import com.todokanai.composepracticenew.usecase.LocalFileOperationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 로컬 삭제가 고른 대상 전체를 저장소에 한 번에 맡기는지 검증하는 단위 테스트(#355).
 * 대상마다 저장소를 부르면 진행이 대상마다 0%로 돌아간다 — 폴더 안쪽 수는 구현만 알아 UseCase가 합칠 수 없다.
 * 저장소 가짜가 일시 중단하지 않으므로 Unconfined 스코프에서 작업이 호출 즉시 끝난다.
 */
class LocalDeleteBatchTest {

    /** 작업을 실행하는 스코프. */
    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)

    /** 삭제 호출을 기록하는 저장소 가짜. */
    private val fileActionRepo = RecordingDeleteRepository()

    /** 검증 대상. */
    private val local = LocalFileOperationUseCase(
        FileOperationOrchestrator(NoOpNotifier, NoOpProgressRepository, RecordingOperationNotices(), GrantedProtection, UnexpectedConflictQuestions, scope),
        RecordingOperationNotices(),
        fileActionRepo,
        FileNameUseCase(NoStorageNameRules),
        LocalPathRules()
    )

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `여러 대상을 지우면 저장소를 한 번만 부르고 모든 대상을 함께 넘긴다`() {
        local.delete(listOf(item("/d/a"), item("/d/b.txt"), item("/d/c")), "done") {}

        assertEquals(listOf(setOf("/d/a", "/d/b.txt", "/d/c")), fileActionRepo.deleteCalls)
    }

    /** [path]의 마지막 경로 조각을 이름으로 갖는 테스트 항목을 만든다. */
    private fun item(path: String) =
        FileHolderItem(path = path, isDirectory = false, name = path.substringAfterLast('/'), sizeBytes = null, lastModifiedMillis = 0L)

    /** 진행 상태를 기록하지 않는 테스트용 진행 저장소. */
    private object NoOpProgressRepository : ProgressRepository {
        override val progressMap: StateFlow<Map<Int, ProgressState>> = MutableStateFlow(emptyMap())
        override fun setProgressState(instanceId: Int, state: ProgressState) {}
        override fun removeProgress(instanceId: Int) {}
    }

    /** 삭제 호출마다 받은 대상을 기록하는 테스트용 로컬 파일 작업 저장소. 삭제 외 작업은 쓰지 않는다. */
    private class RecordingDeleteRepository : FileActionRepository {
        /** 삭제 호출마다 받은 대상 집합. */
        val deleteCalls = mutableListOf<Set<String>>()
        override suspend fun deleteFiles(targetFiles: Set<String>, onProgress: (ProgressState) -> Unit) = IoResult.Done.also { deleteCalls += targetFiles }
        override suspend fun compressAction(targetFiles: Set<String>, parentPath: String, baseName: String, format: ArchiveFormat, onProgress: (ProgressState) -> Unit) = IoResult.Done
        override fun supportedArchiveFormats() = ArchiveFormat.entries.toSet()
        override suspend fun copyAction(targetFiles: Set<String>, targetPath: String, resolveConflict: WriteConflictResolver, onProgress: (ProgressState) -> Unit) = IoResult.Done
        override suspend fun renameFile(targetFile: String, newName: String, overwrite: Boolean) = IoResult.Done
        override suspend fun moveFile(targetFiles: Set<String>, targetPath: String, resolveConflict: WriteConflictResolver, onProgress: (ProgressState) -> Unit) = IoResult.Done
        override suspend fun unzipAction(zipFiles: Set<String>, destPath: String, layout: ExtractLayout, resolveConflict: WriteConflictResolver, onProgress: (ProgressState) -> Unit) = IoResult.Done
        override fun canExtract(path: String) = false
        override suspend fun makeDirectory(parentPath: String, name: String) = IoResult.Done
    }

    /** 알림을 표시하지 않는 테스트용 알림기. */
    private object NoOpNotifier : FileOperationNotifier {
        override fun progressNoti(state: ProgressState, instanceId: Int) {}
        override fun completedNotification(message: String, instanceId: Int) {}
        override fun cancelNotification(instanceId: Int) {}
        override fun awaitingAnswerNoti(operationType: FileOperationType, instanceId: Int) {}
    }
}
