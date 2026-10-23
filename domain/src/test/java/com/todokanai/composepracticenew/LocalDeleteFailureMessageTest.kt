package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.repository.FileActionRepository
import com.todokanai.composepracticenew.repository.ProgressRepository
import com.todokanai.composepracticenew.usecase.FileNameUseCase
import com.todokanai.composepracticenew.usecase.LocalFileOperationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 로컬 삭제가 저장소의 부분 실패를 감싸지 않고 대상 이름 아래 경로로 평탄화해, 머리말이 하나뿐인 메시지로 알리는지 검증하는 단위 테스트.
 * 저장소 가짜가 일시 중단하지 않으므로 Unconfined 스코프에서 작업이 호출 즉시 끝난다.
 */
class LocalDeleteFailureMessageTest {

    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
    private val progressRepo = RecordingProgressRepository()
    private val fileActionRepo = FailingDeleteRepository()
    private val local = LocalFileOperationUseCase(FileOperationOrchestrator(NoOpNotifier, progressRepo, scope), fileActionRepo, FileNameUseCase())

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `파일 자체를 지우지 못하면 대상 이름만으로 알린다`() {
        // 저장소는 대상 자신의 실패를 빈 경로로 알린다
        fileActionRepo.failures["/d/a.txt"] = IoResult.PartiallyFailed("삭제하지 못함", listOf(FailedItem("", null)))

        local.delete(setOf("/d/a.txt"), "done") {}

        assertEquals(listOf("삭제 실패: a.txt"), progressRepo.errors)
    }

    @Test
    fun `폴더 안 항목을 지우지 못하면 대상 이름 아래 경로로 알린다`() {
        fileActionRepo.failures["/d/dir"] = IoResult.PartiallyFailed("삭제하지 못함", listOf(FailedItem("sub/x.txt", null)))

        local.delete(linkedSetOf("/d/dir", "/d/ok.txt"), "done") {}

        assertEquals(listOf("삭제 실패: dir/sub/x.txt"), progressRepo.errors)
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

    /** 지정한 경로의 삭제가 부분 실패를 돌려주는 테스트용 로컬 파일 작업 저장소. 삭제 외 작업은 쓰지 않는다. */
    private class FailingDeleteRepository : FileActionRepository {
        /** 경로 → 그 경로 삭제가 돌려줄 부분 실패. */
        val failures = mutableMapOf<String, IoResult.PartiallyFailed>()
        override suspend fun deleteFile(targetFile: String, onProgress: (ProgressState) -> Unit) = failures[targetFile] ?: IoResult.Done
        override suspend fun zipAction(targetFiles: Set<String>, zipFile: String, onProgress: (ProgressState) -> Unit) = IoResult.Done
        override suspend fun copyAction(targetFiles: Set<String>, targetPath: String, onProgress: (ProgressState) -> Unit) = IoResult.Done
        override suspend fun renameFile(targetFile: String, newName: String, overwrite: Boolean) = IoResult.Done
        override suspend fun moveFile(targetFiles: Set<String>, targetPath: String, onProgress: (ProgressState) -> Unit) = IoResult.Done
        override suspend fun unzipAction(zipFiles: Set<String>, destPath: String, unzipHere: Boolean, onProgress: (ProgressState) -> Unit) = IoResult.Done
        override suspend fun makeDirectory(parentPath: String, name: String) = IoResult.Done
    }

    /** 알림을 표시하지 않는 테스트용 알림기. */
    private object NoOpNotifier : FileOperationNotifier {
        override fun progressNoti(state: ProgressState, instanceId: Int) {}
        override fun completedNotification(title: String, message: String, operationType: FileOperationType?, notifId: Int) {}
        override fun cancelNotification(notifId: Int) {}
    }
}
