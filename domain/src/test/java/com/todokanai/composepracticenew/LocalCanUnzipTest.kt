package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.repository.FileActionRepository
import com.todokanai.composepracticenew.repository.ProgressRepository
import com.todokanai.composepracticenew.usecase.FileNameUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import com.todokanai.composepracticenew.usecase.LocalFileOperationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** [LocalFileOperationUseCase.canUnzip]이 저장소의 지원 형식을 따르되 폴더는 제외하는지 검증하는 단위 테스트. */
class LocalCanUnzipTest {

    private val scope = CoroutineScope(Job())

    /** 넣은 실패·완료 메시지를 기록하는 메시지 저장소. */
    private val notices = RecordingOperationNotices()
    private val local = LocalFileOperationUseCase(FileOperationOrchestrator(NoOpNotifier, NoOpProgressRepository, notices, GrantedProtection, scope), notices, ZipOnlyRepository, FileNameUseCase(), FileNavigatorUseCase(LocalPathNavigator()))

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `저장소가 지원하는 형식의 파일은 풀 수 있다`() {
        assertEquals(true, local.canUnzip(item("/d/a.zip", isDirectory = false)))
    }

    @Test
    fun `저장소가 지원하지 않는 형식은 풀 수 없다`() {
        assertEquals(false, local.canUnzip(item("/d/a.txt", isDirectory = false)))
    }

    @Test
    fun `이름이 아카이브 형식이어도 폴더는 풀 수 없다`() {
        assertEquals(false, local.canUnzip(item("/d/a.zip", isDirectory = true)))
    }

    /** [path]의 마지막 경로 조각을 이름으로 갖는 테스트 항목을 만든다. */
    private fun item(path: String, isDirectory: Boolean) =
        FileHolderItem(path = path, isDirectory = isDirectory, name = path.substringAfterLast('/'), sizeBytes = null, lastModifiedMillis = 0L)

    /** `.zip`으로 끝나는 경로만 압축 해제할 수 있다고 답하는 테스트용 저장소. 다른 작업은 쓰지 않는다. */
    private object ZipOnlyRepository : FileActionRepository {
        override suspend fun deleteFile(targetFile: String, onProgress: (ProgressState) -> Unit) = IoResult.Done
        override suspend fun zipAction(targetFiles: Set<String>, parentPath: String, zipName: String, onProgress: (ProgressState) -> Unit) = IoResult.Done
        override suspend fun copyAction(targetFiles: Set<String>, targetPath: String, onProgress: (ProgressState) -> Unit) = IoResult.Done
        override suspend fun renameFile(targetFile: String, newName: String, overwrite: Boolean) = IoResult.Done
        override suspend fun moveFile(targetFiles: Set<String>, targetPath: String, onProgress: (ProgressState) -> Unit) = IoResult.Done
        override suspend fun unzipAction(zipFiles: Set<String>, destPath: String, unzipHere: Boolean, onProgress: (ProgressState) -> Unit) = IoResult.Done
        override fun canExtract(path: String) = path.endsWith(".zip")
        override suspend fun makeDirectory(parentPath: String, name: String) = IoResult.Done
    }

    /** 진행 상태를 기록하지 않는 테스트용 진행 저장소. */
    private object NoOpProgressRepository : ProgressRepository {
        override val progressMap: StateFlow<Map<Int, ProgressState>> = MutableStateFlow(emptyMap())
        override fun setProgressState(instanceId: Int, state: ProgressState) {}
        override fun removeProgress(instanceId: Int) {}
    }

    /** 알림을 표시하지 않는 테스트용 알림기. */
    private object NoOpNotifier : FileOperationNotifier {
        override fun progressNoti(state: ProgressState, instanceId: Int) {}
        override fun completedNotification(message: String, instanceId: Int) {}
        override fun cancelNotification(instanceId: Int) {}
    }
}
