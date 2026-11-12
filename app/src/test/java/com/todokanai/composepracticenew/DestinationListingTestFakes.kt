package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.ArchiveFormat
import com.todokanai.composepracticenew.model.ExtractLayout
import com.todokanai.composepracticenew.model.FileLaunchResult
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.composepracticenew.model.NavigateResult
import com.todokanai.composepracticenew.model.OperationNoticeKind
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.model.SortMode
import com.todokanai.composepracticenew.operation.FileOperationNotifier
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator
import com.todokanai.composepracticenew.operation.OperationProtection
import com.todokanai.composepracticenew.repository.FileActionRepository
import com.todokanai.composepracticenew.repository.FileLauncher
import com.todokanai.composepracticenew.repository.FileNavigatorRepository
import com.todokanai.composepracticenew.repository.StoragePathRules
import com.todokanai.composepracticenew.repository.OperationNoticeReporter
import com.todokanai.composepracticenew.repository.ProgressRepository
import com.todokanai.composepracticenew.repository.RemoteConnectionRepository
import com.todokanai.composepracticenew.repository.RemoteFileMutationRepository
import com.todokanai.composepracticenew.repository.RemoteFileTransferRepository
import com.todokanai.composepracticenew.repository.SortPreferenceRepository
import com.todokanai.composepracticenew.tools.OperationMessages
import com.todokanai.composepracticenew.usecase.CancelOperationUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 현재 경로·조회 요청과 목록 조회 상태를 테스트가 정하는 테스트용 탐색 저장소. */
internal class FakeNavigator : FileNavigatorRepository, StoragePathRules {
    override val currentPath = MutableStateFlow<String?>(null)
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

    override suspend fun navigate(path: String) = NavigateResult.Navigated
    override fun refresh() {}
    override fun getParentPath(path: String): String? = null
    override fun displayNameOf(path: String): String = path.substringAfterLast('/').ifEmpty { path }
}

/** 진행 상태를 기록하지 않는 테스트용 진행 저장소. */
internal class RecordingProgressRepository : ProgressRepository {
    override val progressMap: StateFlow<Map<Int, ProgressState>> = MutableStateFlow(emptyMap())
    override fun setProgressState(instanceId: Int, state: ProgressState) {}
    override fun removeProgress(instanceId: Int) {}
}

/** 요청된 다운로드·업로드를 기록하는 테스트용 원격 전송 저장소. */
internal class RecordingTransferRepository : RemoteFileTransferRepository {
    val calls = mutableListOf<String>()
    override suspend fun download(remotePath: String, localDirPath: String, name: String, isDirectory: Boolean, onProgress: (ProgressState) -> Unit): IoResult<Unit> {
        calls += "download $remotePath"
        return IoResult.Done
    }
    override suspend fun upload(localPath: String, remoteDirPath: String, name: String, onProgress: (ProgressState) -> Unit): IoResult<Unit> {
        calls += "upload $localPath"
        return IoResult.Done
    }
}

/** 원격 변경을 수행하지 않는 테스트용 원격 변경 저장소. 이 테스트의 대상 작업은 원격 변경을 쓰지 않는다. */
internal object NoOpMutationRepository : RemoteFileMutationRepository {
    override suspend fun rename(path: String, newName: String) = IoResult.Done
    override suspend fun deleteFile(path: String) = IoResult.Done
    override suspend fun makeDirectory(parentPath: String, name: String) = IoResult.Done
    override suspend fun removeDirectoryRecursive(path: String) = IoResult.Done
}

/** 호출된 로컬 작업을 기록하는 테스트용 로컬 파일 작업 저장소. */
internal class RecordingFileActionRepository : FileActionRepository {
    val calls = mutableListOf<String>()

    /** 압축 해제 호출마다 넘어온 배치 방식. */
    val unzipLayouts = mutableListOf<ExtractLayout>()

    /** 아카이브 경로와 배치 방식을 받아 해제가 목적지 바로 아래에 만들 이름을 돌려주는 함수. 테스트가 정한다. */
    var extractedNamesOf: (zipFile: String, layout: ExtractLayout) -> Set<String> = { _, _ -> emptySet() }

    /** 정해 두면 압축 해제가 만들 이름을 구하는 호출이 이 값이 완료될 때까지 기다린다 — 아카이브를 읽는 동안의 일시 중단을 흉내 낸다. */
    var extractedNamesGate: CompletableDeferred<Unit>? = null
    override suspend fun compressAction(targetFiles: Set<String>, parentPath: String, baseName: String, format: ArchiveFormat, onProgress: (ProgressState) -> Unit) = IoResult.Done.also { calls += "compress" }
    override fun supportedArchiveFormats() = ArchiveFormat.entries.toSet()
    override suspend fun copyAction(targetFiles: Set<String>, targetPath: String, onProgress: (ProgressState) -> Unit) = IoResult.Done.also { calls += "copy" }
    override suspend fun renameFile(targetFile: String, newName: String, overwrite: Boolean) = IoResult.Done.also { calls += "rename" }
    override suspend fun deleteFile(targetFile: String, onProgress: (ProgressState) -> Unit) = IoResult.Done.also { calls += "delete" }
    override suspend fun moveFile(targetFiles: Set<String>, targetPath: String, onProgress: (ProgressState) -> Unit) = IoResult.Done.also { calls += "move" }
    override suspend fun unzipAction(zipFiles: Set<String>, destPath: String, layout: ExtractLayout, onProgress: (ProgressState) -> Unit) =
        IoResult.Done.also { calls += "unzip"; unzipLayouts += layout }
    override suspend fun extractedNames(zipFile: String, destPath: String, layout: ExtractLayout): IoResult<Set<String>> {
        extractedNamesGate?.await()
        return IoResult.Success(extractedNamesOf(zipFile, layout))
    }
    override fun canExtract(path: String) = false
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
    override fun completedNotification(message: String, instanceId: Int) {}
    override fun cancelNotification(instanceId: Int) {}
}

/** 언제나 바로 보호를 주는 테스트용 작업 보호. */
internal object GrantedProtection : OperationProtection {
    override suspend fun acquire(): IoResult<Unit> = IoResult.Done
}

/** 고정 문구를 돌려주는 테스트용 메시지 제공자. */
internal object FixedMessages : OperationMessages {
    override val notiComplete = "완료"
    override val notiMoveComplete = "이동 완료"
    override val notiDeleteComplete = "삭제 완료"
    override val notiDownloadComplete = "다운로드 완료"
    override val notiUploadComplete = "업로드 완료"
    override val destinationUnverified = "목록 미확인"
    override val conflictAwaiting = "확인 대기 중"
    override val defaultNewFolderName = "새 폴더"
    override val protectionRefused = "보호 거부"
}

/** 연결 상태를 테스트가 직접 바꾸는 테스트용 원격 연결 저장소. 연결·해제는 상태만 바꾼다. */
internal class FakeConnectionRepository : RemoteConnectionRepository {
    override val isConnecting = MutableStateFlow(false)
    override val connectionState = MutableStateFlow<RemoteConnectionState>(RemoteConnectionState.Connected("server"))
    override suspend fun connect(item: RemoteStorageItem): ConnectResult {
        connectionState.value = RemoteConnectionState.Connected(item.name)
        return ConnectResult.Connected
    }
    override suspend fun disconnect() { connectionState.value = RemoteConnectionState.Disconnected }
    override suspend fun drop(): Boolean {
        // 실제 구현과 같이 연결된 세션만 끊김으로 끝낸다
        if (connectionState.value !is RemoteConnectionState.Connected) return false
        connectionState.value = RemoteConnectionState.Lost
        return true
    }
    override fun rootPathOf(item: RemoteStorageItem) = "ftp://${item.address}"
}

/** 실행한 작업이 없어 취소할 것이 없는 테스트용 취소 UseCase. 취소를 다루지 않는 화면 테스트가 ViewModel을 만들 때 쓴다. */
internal fun idleCancelOperationUseCase() =
    CancelOperationUseCase(FileOperationOrchestrator(NoOpNotifier, RecordingProgressRepository(), RecordingOperationNotices(), GrantedProtection, CoroutineScope(Job())))

/** 열려고 한 경로를 기록하고 언제나 다른 앱으로 넘긴 것으로 답하는 테스트용 파일 열기. */
internal class RecordingFileLauncher : FileLauncher {
    /** 열려고 한 경로. 받은 순서대로 쌓는다. */
    val launched = mutableListOf<String>()

    override fun launch(path: String): FileLaunchResult {
        launched += path
        return FileLaunchResult.Launched
    }
}

/** 넣은 실패·완료 메시지를 종류별로 기록하는 테스트용 메시지 저장소. */
internal class RecordingOperationNotices : OperationNoticeReporter {
    /** 넣은 실패(거부 포함) 메시지(넣은 순서). */
    val failures = mutableListOf<String>()

    /** 넣은 완료 메시지(넣은 순서). */
    val completions = mutableListOf<String>()

    override fun report(kind: OperationNoticeKind, message: String) {
        when (kind) {
            OperationNoticeKind.FAILURE -> failures += message
            OperationNoticeKind.COMPLETION -> completions += message
        }
    }
}
