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
import com.todokanai.composepracticenew.repository.StoragePathRules
import com.todokanai.composepracticenew.usecase.FileNameUseCase
import com.todokanai.composepracticenew.usecase.LocalFileOperationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 로컬 복사·이동이 출발지와 목적지의 위치 관계를 경로 문자열이 아니라 저장소의 부모 규칙으로 판정해,
 * 목적지가 원본의 부모(제자리)이거나 원본 자신·그 안쪽이면 실행 전에 사유별 메시지로 거부하는지 검증하는 단위 테스트(#228, #302).
 * 부모 규칙을 '/'와 무관한 표로 두어, 판정이 경로를 직접 쪼개지 않음을 함께 확인한다.
 */
class LocalSamePathTest {

    private val scope = CoroutineScope(Job())

    /** 넣은 실패·완료 메시지를 기록하는 메시지 저장소. */
    private val notices = RecordingOperationNotices()

    /** 'root ⊃ src ⊃ inner ⊃ deep', 'root ⊃ other ⊃ otherInner' 관계를 '/' 없는 경로로 정한 부모 규칙. */
    private val paths = TablePathRules(
        mapOf("deep" to "inner", "inner" to "src", "src" to "root", "otherInner" to "other", "other" to "root")
    )
    private val local = LocalFileOperationUseCase(
        FileOperationOrchestrator(NoOpNotifier, NoOpProgressRepository, notices, GrantedProtection, UnexpectedConflictQuestions, scope),
        notices, NoOpFileActionRepository, FileNameUseCase(NoStorageNameRules), paths
    )

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `목적지가 원본의 부모면 복사를 거부한다`() {
        local.copy(setOf("src"), "root", "done") {}

        assertEquals(listOf("복사 실패: 출발지와 목적지가 동일합니다."), notices.failures)
    }

    @Test
    fun `목적지가 원본 자신이면 복사를 거부한다`() {
        local.copy(setOf("src"), "src", "done") {}

        assertEquals(listOf("복사 실패: 대상 폴더가 원본 폴더 안에 있습니다 (src)"), notices.failures)
    }

    @Test
    fun `목적지가 원본 자신이면 이동을 거부한다`() {
        local.move(setOf("src"), "src", "done") {}

        assertEquals(listOf("이동 실패: 대상 폴더가 원본 폴더 안에 있습니다 (src)"), notices.failures)
    }

    @Test
    fun `목적지가 원본 안쪽이면 이동을 거부한다`() {
        local.move(setOf("src"), "inner", "done") {}

        assertEquals(listOf("이동 실패: 대상 폴더가 원본 폴더 안에 있습니다 (src)"), notices.failures)
    }

    @Test
    fun `목적지가 원본보다 여러 단계 안쪽이어도 이동을 거부한다`() {
        local.move(setOf("src"), "deep", "done") {}

        assertEquals(listOf("이동 실패: 대상 폴더가 원본 폴더 안에 있습니다 (src)"), notices.failures)
    }

    @Test
    fun `여러 원본 중 하나라도 목적지를 안에 품으면 그 원본 이름으로 거부한다`() {
        local.copy(setOf("other", "src"), "inner", "done") {}

        assertEquals(listOf("복사 실패: 대상 폴더가 원본 폴더 안에 있습니다 (src)"), notices.failures)
    }

    @Test
    fun `목적지가 원본과 관계없는 곳이면 거부하지 않는다`() {
        local.copy(setOf("src"), "other", "done") {}

        assertEquals(emptyList<String>(), notices.failures)
    }

    @Test
    fun `목적지가 원본의 형제 안쪽이면 거부하지 않는다`() {
        local.move(setOf("src"), "otherInner", "done") {}

        assertEquals(emptyList<String>(), notices.failures)
    }

    /** 경로별 부모를 표로 정한 테스트용 경로 규칙. 표에 없는 경로는 루트다. */
    private class TablePathRules(private val parents: Map<String, String>) : StoragePathRules {
        override fun getParentPath(path: String): String? = parents[path]
        override fun displayNameOf(path: String): String = path
    }

    /** 어떤 작업도 하지 않는 테스트용 저장소. */
    private object NoOpFileActionRepository : FileActionRepository {
        override suspend fun compressAction(targetFiles: Set<String>, parentPath: String, baseName: String, format: ArchiveFormat, onProgress: (ProgressState) -> Unit) = IoResult.Done
        override fun supportedArchiveFormats() = ArchiveFormat.entries.toSet()
        override suspend fun copyAction(targetFiles: Set<String>, targetPath: String, resolveConflict: WriteConflictResolver, onProgress: (ProgressState) -> Unit) = IoResult.Done
        override suspend fun renameFile(targetFile: String, newName: String, overwrite: Boolean) = IoResult.Done
        override suspend fun deleteFile(targetFile: String, onProgress: (ProgressState) -> Unit) = IoResult.Done
        override suspend fun moveFile(targetFiles: Set<String>, targetPath: String, resolveConflict: WriteConflictResolver, onProgress: (ProgressState) -> Unit) = IoResult.Done
        override suspend fun unzipAction(zipFiles: Set<String>, destPath: String, layout: ExtractLayout, resolveConflict: WriteConflictResolver, onProgress: (ProgressState) -> Unit) = IoResult.Done
        override fun canExtract(path: String) = false
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
        override fun awaitingAnswerNoti(operationType: FileOperationType, instanceId: Int) {}
    }
}
