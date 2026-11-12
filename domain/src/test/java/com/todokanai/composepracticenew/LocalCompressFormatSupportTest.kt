package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.repository.WriteConflictResolver
import com.todokanai.composepracticenew.model.ArchiveFormat
import com.todokanai.composepracticenew.model.ExtractLayout
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.repository.FileActionRepository
import com.todokanai.composepracticenew.repository.ProgressRepository
import com.todokanai.composepracticenew.usecase.FileNameUseCase
import com.todokanai.composepracticenew.usecase.LocalFileOperationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [LocalFileOperationUseCase.compress]가 저장소가 지원하지 않는 형식을 진행 표시 없이 실행 전에 거부하는지 검증하는 단위 테스트. */
class LocalCompressFormatSupportTest {

    private val scope = CoroutineScope(Job())

    /** 넣은 실패·완료 메시지를 기록하는 메시지 저장소. */
    private val notices = RecordingOperationNotices()
    /** 시작된 진행 상태를 기록하는 진행 저장소. */
    private val progressRepo = RecordingProgressRepository()

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `저장소가 지원하지 않는 형식은 저장소를 호출하지 않고 진행 표시 없이 거부한다`() {
        val repo = SupportingRepository(supported = emptySet())

        useCaseWith(repo).compress(setOf("/dir/a.txt"), "/dir", "archive", ArchiveFormat.ZIP, "done") {}

        assertEquals(1, notices.failures.size)
        assertTrue(notices.failures[0], notices.failures[0].startsWith("압축 실패"))
        assertTrue("저장소가 호출되면 안 됨: ${repo.calls}", repo.calls.isEmpty())
        assertTrue("진행이 시작되면 안 됨: ${progressRepo.started}", progressRepo.started.isEmpty())
    }

    @Test
    fun `저장소가 지원하는 형식은 거부하지 않는다`() {
        useCaseWith(SupportingRepository(supported = setOf(ArchiveFormat.ZIP))).compress(setOf("/dir/a.txt"), "/dir", "archive", ArchiveFormat.ZIP, "done") {}

        assertTrue(notices.failures.toString(), notices.failures.isEmpty())
    }

    /** [repo]로 압축하는 검증 대상 UseCase를 만든다. */
    private fun useCaseWith(repo: FileActionRepository) =
        LocalFileOperationUseCase(FileOperationOrchestrator(NoOpNotifier, progressRepo, notices, GrantedProtection, UnexpectedConflictQuestions, scope), notices, repo, FileNameUseCase(NoStorageNameRules), LocalPathRules())

    /**
     * [supported] 형식만 만들 수 있다고 답하고 호출된 작업 이름을 기록하는 테스트용 저장소.
     * @param supported 지원한다고 답할 아카이브 형식
     */
    private class SupportingRepository(private val supported: Set<ArchiveFormat>) : FileActionRepository {
        /** 호출된 작업 이름. */
        val calls = mutableListOf<String>()
        override suspend fun compressAction(targetFiles: Set<String>, parentPath: String, baseName: String, format: ArchiveFormat, onProgress: (ProgressState) -> Unit) = IoResult.Done.also { calls += "compress" }
        override fun supportedArchiveFormats() = supported
        override suspend fun copyAction(targetFiles: Set<String>, targetPath: String, resolveConflict: WriteConflictResolver, onProgress: (ProgressState) -> Unit) = IoResult.Done
        override suspend fun renameFile(targetFile: String, newName: String, overwrite: Boolean) = IoResult.Done
        override suspend fun deleteFile(targetFile: String, onProgress: (ProgressState) -> Unit) = IoResult.Done
        override suspend fun moveFile(targetFiles: Set<String>, targetPath: String, resolveConflict: WriteConflictResolver, onProgress: (ProgressState) -> Unit) = IoResult.Done
        override suspend fun unzipAction(zipFiles: Set<String>, destPath: String, layout: ExtractLayout, resolveConflict: WriteConflictResolver, onProgress: (ProgressState) -> Unit) = IoResult.Done
        override fun canExtract(path: String) = false
        override suspend fun makeDirectory(parentPath: String, name: String) = IoResult.Done
    }

    /** 진행 상태를 받은 작업 인스턴스 ID를 기록하는 테스트용 진행 저장소. */
    private class RecordingProgressRepository : ProgressRepository {
        /** 진행 상태를 받은 작업 인스턴스 ID. */
        val started = mutableListOf<Int>()
        override val progressMap: StateFlow<Map<Int, ProgressState>> = MutableStateFlow(emptyMap())
        override fun setProgressState(instanceId: Int, state: ProgressState) { started += instanceId }
        override fun removeProgress(instanceId: Int) {}
    }

    /** 알림을 표시하지 않는 테스트용 알림기. */
    private object NoOpNotifier : FileOperationNotifier {
        override fun progressNoti(state: ProgressState, instanceId: Int) {}
        override fun completedNotification(message: String, instanceId: Int) {}
        override fun cancelNotification(instanceId: Int) {}
        override fun awaitingAnswerNoti(operationType: FileOperationType, instanceId: Int) {}
    }
}
