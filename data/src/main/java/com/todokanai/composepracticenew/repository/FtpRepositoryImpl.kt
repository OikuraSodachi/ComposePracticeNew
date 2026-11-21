package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.ftp.DownloadSource
import com.todokanai.composepracticenew.data.ftp.FtpSessionManager
import com.todokanai.composepracticenew.data.ftp.RemoteDeletionTarget
import com.todokanai.composepracticenew.data.ftp.UploadSource
import com.todokanai.composepracticenew.data.ftp.childFtpUri
import com.todokanai.composepracticenew.data.ftp.siblingFtpUri
import com.todokanai.composepracticenew.datasource.LeftoverDiscarder
import com.todokanai.composepracticenew.model.ConnectResult
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.RemoteConnectionState
import com.todokanai.composepracticenew.model.RemoteSession
import com.todokanai.composepracticenew.model.RemoteStorageItem
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 원격 스토리지 역할 인터페이스(연결·세션·전송·변경)의 FTP 구현체.
 * 진행 표시가 있는 작업(다운로드·업로드·삭제)은 작업마다 자기 세션을 열어 실행하고, 이름 변경·폴더 생성은 탐색 세션에서 실행한다(#436).
 * @param sessions 탐색 세션·작업 세션을 열고 닫으며 연결 상태를 정하는 관리자
 * @param notices 취소한 업로드의 정리 실패를 받을 알림함
 * @param leftovers 다운로드가 받지 못한 로컬 파일을 지우고, 지우지 못하면 알리는 LeftoverDiscarder
 */
@Singleton
class FtpRepositoryImpl @Inject internal constructor(
    private val sessions: FtpSessionManager,
    private val notices: OperationNoticeReporter,
    private val leftovers: LeftoverDiscarder
): RemoteConnectionRepository, RemoteSessionProvider, RemoteFileTransferRepository, RemoteFileMutationRepository {

    override val isConnecting: StateFlow<Boolean> = sessions.isConnecting

    override val connectionState: StateFlow<RemoteConnectionState> = sessions.connectionState

    override suspend fun connect(item: RemoteStorageItem): ConnectResult = sessions.connect(item)

    override suspend fun disconnect() { sessions.disconnect() }

    override suspend fun drop(): Boolean = sessions.drop()

    override fun current(): RemoteSession? = sessions.current()

    override suspend fun download(session: RemoteSession, items: List<FileHolderItem>, localDirPath: String, resolveConflict: WriteConflictResolver, onProgress: (ProgressState) -> Unit) =
        sessions.withOperationSession(session) { parts ->
            parts.downloader(leftovers).download(items.map { DownloadSource(it.path, File(localDirPath, it.name).path, it.isDirectory) }, resolveConflict, onProgress)
        }

    override suspend fun upload(session: RemoteSession, items: List<FileHolderItem>, remoteDirPath: String, onProgress: (ProgressState) -> Unit) =
        sessions.withOperationSession(session) { parts ->
            parts.uploader(notices).upload(items.map { UploadSource(it.path, childFtpUri(remoteDirPath, it.name)) }, onProgress)
        }

    override suspend fun rename(session: RemoteSession, path: String, newName: String) =
        sessions.withBrowsingSession(session) { parts -> parts.executor.rename(path, siblingFtpUri(path, newName)) }

    override suspend fun delete(session: RemoteSession, items: List<FileHolderItem>, onProgress: (ProgressState) -> Unit) =
        sessions.withOperationSession(session) { parts ->
            parts.deletion.delete(items.map { RemoteDeletionTarget(it.path, it.name, it.isDirectory) }, onProgress)
        }

    override suspend fun makeDirectory(session: RemoteSession, parentPath: String, name: String) =
        sessions.withBrowsingSession(session) { parts -> parts.executor.makeDirectory(childFtpUri(parentPath, name)) }
}
