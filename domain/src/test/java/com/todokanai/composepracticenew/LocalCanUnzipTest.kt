package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.ProgressPhase
import com.todokanai.composepracticenew.model.OperationKind
import com.todokanai.composepracticenew.repository.ArchivePasswordProvider
import com.todokanai.composepracticenew.model.QuestionKind
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.repository.WriteConflictResolver
import com.todokanai.composepracticenew.model.ArchiveFormat
import com.todokanai.composepracticenew.model.ExtractLayout
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.repository.FileActionRepository
import com.todokanai.composepracticenew.repository.ProgressReporter
import com.todokanai.composepracticenew.usecase.FileNameUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.LocalFileOperationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** [LocalFileOperationUseCase.canUnzip]이 저장소가 묶은 압축 해제 대상이 하나일 때만 허용하고 폴더는 제외하는지 검증하는 단위 테스트. */
class LocalCanUnzipTest {

    private val scope = CoroutineScope(Job())

    /** 넣은 실패·완료 메시지를 기록하는 메시지 저장소. */
    private val notices = RecordingOperationNotices()
    private val local = LocalFileOperationUseCase(FileOperationOrchestrator(NoOpNotifier, NoOpProgressReporter, notices, GrantedProtection, UnexpectedQuestions, scope), notices, ZipOnlyRepository, FileNameUseCase(NoStorageNameRules), LocalPathRules())

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `저장소가 지원하는 형식의 파일은 풀 수 있다`() {
        assertEquals(true, local.canUnzip(listOf(item("/d/a.zip", isDirectory = false))))
    }

    @Test
    fun `저장소가 지원하지 않는 형식은 풀 수 없다`() {
        assertEquals(false, local.canUnzip(listOf(item("/d/a.txt", isDirectory = false))))
    }

    @Test
    fun `이름이 아카이브 형식이어도 폴더는 풀 수 없다`() {
        assertEquals(false, local.canUnzip(listOf(item("/d/a.zip", isDirectory = true))))
    }

    @Test
    fun `저장소가 압축 파일 하나로 묶는 여러 항목은 풀 수 있다`() {
        assertEquals(true, local.canUnzip(listOf(item("/d/a.zip", isDirectory = false), item("/d/a.z01", isDirectory = false))))
    }

    @Test
    fun `저장소가 압축 파일 여럿으로 묶는 항목은 풀 수 없다`() {
        assertEquals(false, local.canUnzip(listOf(item("/d/a.zip", isDirectory = false), item("/d/b.zip", isDirectory = false))))
    }

    @Test
    fun `선택이 비어 있으면 풀 수 없다`() {
        assertEquals(false, local.canUnzip(emptyList()))
    }

    /** [path]의 마지막 경로 조각을 이름으로 갖는 테스트 항목을 만든다. */
    private fun item(path: String, isDirectory: Boolean) =
        FileHolderItem(path = path, isDirectory = isDirectory, name = path.substringAfterLast('/'), sizeBytes = null, lastModifiedMillis = 0L)

    /** `.zip`으로 끝나는 경로를 압축 해제 대상으로, `.z01`로 끝나는 경로를 앞의 대상에 합치는 볼륨으로 답하는 테스트용 저장소. 다른 작업은 쓰지 않는다. */
    private object ZipOnlyRepository : FileActionRepository {
        override suspend fun deleteFiles(targetFiles: Set<String>, onProgress: (ProgressPhase) -> Unit) = IoResult.Done
        override suspend fun compressAction(targetFiles: Set<String>, parentPath: String, baseName: String, format: ArchiveFormat, password: String?, onProgress: (ProgressPhase) -> Unit) = IoResult.Done
        override fun supportedArchiveFormats() = ArchiveFormat.entries.toSet()
        override suspend fun copyAction(targetFiles: Set<String>, targetPath: String, resolveConflict: WriteConflictResolver, onProgress: (ProgressPhase) -> Unit) = IoResult.Done
        override suspend fun renameFile(targetFile: String, newName: String, overwrite: Boolean) = IoResult.Done
        override suspend fun moveFile(targetFiles: Set<String>, targetPath: String, resolveConflict: WriteConflictResolver, onProgress: (ProgressPhase) -> Unit) = IoResult.Done
        override suspend fun unzipAction(zipFiles: Set<String>, destPath: String, layout: ExtractLayout, resolveConflict: WriteConflictResolver, askPassword: ArchivePasswordProvider, onProgress: (ProgressPhase) -> Unit) = IoResult.Done
        override fun extractionTargetOf(paths: Set<String>): String? =
            paths.filterNot { it.endsWith(".z01") }.takeIf { targets -> targets.all { it.endsWith(".zip") } }?.singleOrNull()
        override suspend fun makeDirectory(parentPath: String, name: String) = IoResult.Done
    }

    /** 진행 상태를 기록하지 않는 테스트용 진행 저장소. */
    private object NoOpProgressReporter : ProgressReporter {
        override fun setProgressState(instanceId: Int, state: ProgressState) {}
        override fun removeProgress(instanceId: Int) {}
    }

    /** 알림을 표시하지 않는 테스트용 알림기. */
    private object NoOpNotifier : FileOperationNotifier {
        override fun progressNotification(state: ProgressState, instanceId: Int) {}
        override fun completedNotification(kind: OperationKind, instanceId: Int) {}
        override fun cancelNotification(instanceId: Int) {}
        override fun awaitingAnswerNotification(operationType: FileOperationType, kind: QuestionKind, instanceId: Int) {}
    }
}
