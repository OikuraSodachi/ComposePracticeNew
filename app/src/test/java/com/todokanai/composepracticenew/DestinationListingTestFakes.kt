package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.model.SortMode
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.repository.FileActionRepository
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.ProgressRepository
import com.todokanai.composepracticenew.repository.RemoteConnectionRepository
import com.todokanai.composepracticenew.repository.RemoteFileMutationRepository
import com.todokanai.composepracticenew.repository.RemoteFileTransferRepository
import com.todokanai.composepracticenew.repository.SortPreferenceRepository
import com.todokanai.composepracticenew.tools.OperationMessages
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/** 현재 경로·조회 요청과 목록 조회 상태를 테스트가 정하는 테스트용 탐색 저장소. */
internal class FakeNavigator : FileNavigatorRepository {
    override val currentPath = MutableStateFlow<String?>(null)
    override val dirTree: Flow<List<FileEntry>> = emptyFlow()
    override val listingRequest = MutableStateFlow<ListingRequest?>(null)
    override val directoryListing = MutableStateFlow<DirectoryListing>(DirectoryListing.NoPath)
    private var generation = 0L

    /**
     * [path]로 이동한 것처럼 현재 경로와 새 조회 요청을 정한다. 목록 상태는 바꾸지 않는다.
     * @param path 이동할 경로
     * @return 새 조회 요청
     */
    fun moveTo(path: String): ListingRequest {
        currentPath.value = path
        return ListingRequest(path, ++generation).also { listingRequest.value = it }
    }

    override suspend fun navigate(path: String) {}
    override fun refresh() {}
    override fun getParentPath(path: String): String? = null
}

/** 방출된 오류 메시지를 기록하는 테스트용 진행 저장소. */
internal class RecordingProgressRepository : ProgressRepository {
    val errors = mutableListOf<String>()
    override val progressMap: StateFlow<Map<Int, ProgressState>> = MutableStateFlow(emptyMap())
    override val operationErrors: SharedFlow<String> = MutableSharedFlow()
    override val operationCompletions: SharedFlow<String> = MutableSharedFlow()
    override fun setProgressState(instanceId: Int, state: ProgressState) {}
    override fun removeProgress(instanceId: Int) {}
    override fun emitError(message: String) { errors += message }
    override fun emitCompletion(message: String) {}
}

/** 요청된 다운로드·업로드를 기록하는 테스트용 원격 전송 저장소. */
internal class RecordingTransferRepository : RemoteFileTransferRepository {
    val calls = mutableListOf<String>()
    override suspend fun download(remotePath: String, localPath: String, isDirectory: Boolean, onProgress: (ProgressState) -> Unit): IoResult<Unit> {
        calls += "download $remotePath"
        return IoResult.Done
    }
    override suspend fun upload(localPath: String, remotePath: String, onProgress: (ProgressState) -> Unit): IoResult<Unit> {
        calls += "upload $localPath"
        return IoResult.Done
    }
}

/** 원격 변경을 수행하지 않는 테스트용 원격 변경 저장소. 이 테스트의 대상 작업은 원격 변경을 쓰지 않는다. */
internal object NoOpMutationRepository : RemoteFileMutationRepository {
    override suspend fun rename(fromPath: String, toPath: String) = IoResult.Done
    override suspend fun deleteFile(path: String) = IoResult.Done
    override suspend fun makeDirectory(path: String) = IoResult.Done
    override suspend fun removeDirectoryRecursive(path: String) = IoResult.Done
}

/** 호출된 로컬 작업을 기록하는 테스트용 로컬 파일 작업 저장소. */
internal class RecordingFileActionRepository : FileActionRepository {
    val calls = mutableListOf<String>()
    override suspend fun zipAction(targetFiles: Set<String>, zipFile: String, onProgress: (ProgressState) -> Unit) = IoResult.Done.also { calls += "zip" }
    override suspend fun copyAction(targetFiles: Set<String>, targetPath: String, onProgress: (ProgressState) -> Unit) = IoResult.Done.also { calls += "copy" }
    override suspend fun renameFile(targetFile: String, newName: String, overwrite: Boolean) = IoResult.Done.also { calls += "rename" }
    override suspend fun deleteFile(targetFile: String, onProgress: (ProgressState) -> Unit) = IoResult.Done.also { calls += "delete" }
    override suspend fun moveFile(targetFiles: Set<String>, targetPath: String, onProgress: (ProgressState) -> Unit) = IoResult.Done.also { calls += "move" }
    override suspend fun unzipAction(zipFiles: Set<String>, destPath: String, unzipHere: Boolean, onProgress: (ProgressState) -> Unit) = IoResult.Done.also { calls += "unzip" }
    override suspend fun makeDirectory(parentPath: String, name: String) = IoResult.Done.also { calls += "mkdir" }
}

/** 정렬 기준을 메모리에만 두는 테스트용 정렬 설정 저장소. */
internal class InMemorySortPreference : SortPreferenceRepository {
    override val sortBy = MutableStateFlow(SortMode.BY_DEFAULT)
    override fun saveSortBy(value: SortMode) { sortBy.value = value }
}

/** 알림을 표시하지 않는 테스트용 알림기. */
internal object NoOpNotifier : FileOperationNotifier {
    override fun progressNoti(state: ProgressState, instanceId: Int) {}
    override fun completedNotification(title: String, message: String, operationType: FileOperationType?, notifId: Int) {}
    override fun cancelNotification(notifId: Int) {}
}

/** 고정 문구를 돌려주는 테스트용 메시지 제공자. */
internal object FixedMessages : OperationMessages {
    override val notiComplete = "완료"
    override val notiMoveComplete = "이동 완료"
    override val notiDeleteComplete = "삭제 완료"
    override val notiDownloadComplete = "다운로드 완료"
    override val notiUploadComplete = "업로드 완료"
    override val destinationUnverified = "목록 미확인"
    override val defaultNewFolderName = "새 폴더"
}

/** 연결 상태를 테스트가 직접 바꾸는 테스트용 원격 연결 저장소. 연결·해제는 상태만 바꾼다. */
internal class FakeConnectionRepository : RemoteConnectionRepository {
    override val isConnecting = MutableStateFlow(false)
    override val connectionState = MutableStateFlow(RemoteConnectionState.CONNECTED)
    override suspend fun connect(item: RemoteStorageItem): ConnectResult {
        connectionState.value = RemoteConnectionState.CONNECTED
        return ConnectResult.Connected
    }
    override suspend fun disconnect() { connectionState.value = RemoteConnectionState.DISCONNECTED }
    override fun rootPathOf(item: RemoteStorageItem) = "ftp://${item.address}"
}
