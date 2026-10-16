package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPConnectionClosedException
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FTP 파일 다운로드와 업로드를 스트리밍으로 수행하며 진행률을 보고한다.
 * 전송의 한쪽 끝이 로컬 파일이므로 로컬 스트림 개폐는 이 클래스가 담당하고,
 * 스트림 획득 실패 처리·전송 완료 확인은 공통 private 함수로, 디렉터리 진행률은 로컬 작업과 공유하는 [ByteProgressTracker]로 계산한다.
 * @param session 전송에 사용할 FTPClient와 잠금을 소유한 FTP 세션
 * @param cmd 디렉터리 다운로드 시 원격 트리를 수집하는 FtpCommandExecutor
 */
@Singleton
class FtpTransferrer @Inject constructor(
    private val session: FtpSession,
    private val cmd: FtpCommandExecutor
) {

    /**
     * remotePath의 파일 또는 디렉터리를 localPath에 스트리밍 다운로드한다.
     * isDirectory가 true이면 원격 트리를 재귀 수집 후 각 파일을 순서대로 다운로드한다.
     * 미연결 시 error ProgressState를 방출하고 종료한다.
     * @param remotePath ftp://server/path 형식의 원격 절대 경로
     * @param localPath 저장할 로컬 파일 또는 디렉터리의 절대 경로
     * @param isDirectory remotePath가 디렉터리인 경우 true
     */
    fun download(remotePath: String, localPath: String, isDirectory: Boolean = false, onProgress: (ProgressState) -> Unit = {}): Flow<ProgressState> = channelFlow {
        launch(session.ioContext()) {
            if (!session.isReady()) {
                send(ProgressState(error = "Not connected"))
                return@launch
            }
            if (!isDirectory) downloadFile(remotePath, File(localPath), onProgress) { send(it) }
            else downloadDirectory(remotePath, localPath, onProgress) { send(it) }
        }.join()
    }

    /**
     * localPath의 파일 또는 디렉터리를 remotePath에 스트리밍 업로드한다.
     * localPath가 디렉터리이면 로컬 트리를 walkTopDown으로 수집 후 각 항목을 순서대로 업로드한다.
     * 미연결 시 error ProgressState를 방출하고 종료한다.
     * @param localPath 업로드할 로컬 파일 또는 디렉터리의 절대 경로
     * @param remotePath 저장될 원격 파일 또는 디렉터리의 절대 경로 (ftp://server/path 형식)
     */
    fun upload(localPath: String, remotePath: String, onProgress: (ProgressState) -> Unit = {}): Flow<ProgressState> = channelFlow {
        launch(session.ioContext()) {
            if (!session.isReady()) {
                send(ProgressState(error = "Not connected"))
                return@launch
            }
            val localFile = File(localPath)
            val ftpPath = extractFtpPath(remotePath)
            if (!localFile.isDirectory) uploadFile(localFile, ftpPath, onProgress) { send(it) }
            else uploadDirectory(localFile, ftpPath, onProgress) { send(it) }
        }.join()
    }

    /** 단일 원격 파일을 [localFile]로 다운로드한다. 크기 미확인 시 바이트마다, 확인 시 100% 미만까지만 보고한다. */
    private suspend fun downloadFile(
        remotePath: String,
        localFile: File,
        onProgress: (ProgressState) -> Unit,
        send: suspend (ProgressState) -> Unit
    ) {
        val ftpPath = extractFtpPath(remotePath)
        session.withClient { client ->
            localFile.parentFile?.mkdirs()
            val totalBytes = runCatching { client.mlistFile(ftpPath)?.size ?: -1L }.getOrDefault(-1L)
            var writtenBytes = 0L
            downloadOne(
                client, ftpPath, localFile, errorSuffix = "", send = send,
                onChunk = { bytesRead ->
                    writtenBytes += bytesRead
                    // ACK 전 100% 조기 발행 방지 — totalBytes 미확인 시는 항상 보고
                    if (totalBytes <= 0 || writtenBytes < totalBytes) onProgress(buildProgressState(totalBytes, writtenBytes, localFile.name))
                },
                onAcked = {
                    // totalBytes > 0 인 경우에만 100% 보고 — 크기 미확인 시 progress=null이므로 생략
                    if (totalBytes > 0) onProgress(buildProgressState(totalBytes, totalBytes, localFile.name))
                }
            )
        }
    }

    /** 원격 디렉터리 트리를 [localPath] 아래로 다운로드한다. */
    private suspend fun downloadDirectory(
        remotePath: String,
        localPath: String,
        onProgress: (ProgressState) -> Unit,
        send: suspend (ProgressState) -> Unit
    ) {
        val allEntries = cmd.collectRemoteTree(remotePath)
        val fileEntries = allEntries.filter { !it.isDirectory }
        val progress = directoryProgress(fileEntries.sumOf { it.size }.coerceAtLeast(1), fileEntries.size, onProgress)
        File(localPath).mkdirs()
        allEntries.forEach { entry ->
            val relativePath = entry.path.removePrefix(remotePath).trimStart('/')
            val localTarget = File(localPath, relativePath)
            if (entry.isDirectory) {
                localTarget.mkdirs()
            } else {
                progress.startFile()
                val entryFtpPath = extractFtpPath(entry.path)
                session.withClient { client ->
                    localTarget.parentFile?.mkdirs()
                    downloadOne(
                        client, entryFtpPath, localTarget, errorSuffix = " entry=${entry.name}", send = send,
                        onChunk = { bytesRead -> progress.addBytes(bytesRead.toLong(), entry.name, entry.size) },
                        onAcked = { progress.onFileCompleted(entry.name, entry.size) }
                    )
                }
            }
        }
        progress.reportIfEmpty()
    }

    /** 단일 로컬 파일을 [ftpPath]로 업로드한다. 서버 확인 전까지 100% 미만만 보고한다. */
    private suspend fun uploadFile(
        localFile: File,
        ftpPath: String,
        onProgress: (ProgressState) -> Unit,
        send: suspend (ProgressState) -> Unit
    ) {
        session.withClient { client ->
            val totalBytes = localFile.length()
            var writtenBytes = 0L
            uploadOne(
                client, ftpPath, localFile, errorSuffix = "", send = send,
                onChunk = { bytesRead ->
                    writtenBytes += bytesRead
                    // ACK 전 100% 조기 발행 방지 — 서버 확인 후 onAcked에서 보고
                    if (writtenBytes < totalBytes) onProgress(buildProgressState(totalBytes, writtenBytes, localFile.name))
                },
                onAcked = { onProgress(buildProgressState(totalBytes, totalBytes, localFile.name)) }
            )
        }
    }

    /** 로컬 디렉터리 트리를 [ftpPath] 아래로 업로드한다. */
    private suspend fun uploadDirectory(
        localDir: File,
        ftpPath: String,
        onProgress: (ProgressState) -> Unit,
        send: suspend (ProgressState) -> Unit
    ) {
        val allEntries = localDir.walkTopDown().toList()
        val fileEntries = allEntries.filter { it.isFile }
        val progress = directoryProgress(fileEntries.sumOf { it.length() }.coerceAtLeast(1), fileEntries.size, onProgress)
        allEntries.forEach { entry ->
            val relativePath = localDir.toPath().relativize(entry.toPath()).toString().replace("\\", "/")
            val entryFtpPath = if (relativePath.isEmpty()) ftpPath else "$ftpPath/$relativePath"
            if (entry.isDirectory) {
                session.withClient { client -> runCatching { client.makeDirectory(entryFtpPath) } }
            } else {
                progress.startFile()
                val entryBytes = entry.length()
                session.withClient { client ->
                    uploadOne(
                        client, entryFtpPath, entry, errorSuffix = " entry=${entry.name}", send = send,
                        onChunk = { bytesRead -> progress.addBytes(bytesRead.toLong(), entry.name, entryBytes) },
                        // 마지막 파일 인덱스 기준으로 100% 보고 — 0바이트 파일 포함 시 바이트 비교 오판 방지
                        onAcked = { progress.onFileCompleted(entry.name, entryBytes) }
                    )
                }
            }
        }
    }

    /**
     * 세션 잠금 안에서 원격 파일 하나를 [localFile]로 받는다. 단일 파일·디렉터리 다운로드가 공유하며,
     * 진행률 보고 규칙은 호출부가 [onChunk]·[onAcked]로 결정한다. 스트림을 열지 못하면 오류를 보고하고 반환한다.
     * @param client 잠금을 확보한 FTPClient
     * @param ftpPath 받을 원격 파일의 FTP 경로
     * @param localFile 저장할 로컬 파일 — 취소 시 삭제된다
     * @param errorSuffix 스트림 열기 실패 메시지 뒤에 붙일 문자열
     * @param send 오류 ProgressState를 방출하는 함수
     * @param onChunk 버퍼마다 읽은 바이트 수를 받는 콜백
     * @param onAcked 서버가 전송 완료를 확인한 뒤 호출되는 콜백
     */
    private suspend fun downloadOne(
        client: FTPClient,
        ftpPath: String,
        localFile: File,
        errorSuffix: String,
        send: suspend (ProgressState) -> Unit,
        onChunk: (Int) -> Unit,
        onAcked: suspend () -> Unit
    ) {
        val input = openStreamOrReport(client, "retrieveFileStream", errorSuffix, send) { it.retrieveFileStream(ftpPath) }
            ?: return
        executeTransfer(client, input, "download 실패", onCancel = { runCatching { localFile.delete() } }, onSend = send, onSuccess = onAcked) {
            localFile.outputStream().use { output -> pump(input, output, onChunk) }
        }
    }

    /**
     * 세션 잠금 안에서 로컬 파일 [localFile] 하나를 원격으로 올린다. 단일 파일·디렉터리 업로드가 공유하며,
     * 진행률 보고 규칙은 호출부가 [onChunk]·[onAcked]로 결정한다. 스트림을 열지 못하면 오류를 보고하고 반환한다.
     * @param client 잠금을 확보한 FTPClient
     * @param ftpPath 저장될 원격 파일의 FTP 경로
     * @param localFile 업로드할 로컬 파일
     * @param errorSuffix 스트림 열기 실패 메시지 뒤에 붙일 문자열
     * @param send 오류 ProgressState를 방출하는 함수
     * @param onChunk 버퍼마다 쓴 바이트 수를 받는 콜백
     * @param onAcked 서버가 전송 완료를 확인한 뒤 호출되는 콜백
     */
    private suspend fun uploadOne(
        client: FTPClient,
        ftpPath: String,
        localFile: File,
        errorSuffix: String,
        send: suspend (ProgressState) -> Unit,
        onChunk: (Int) -> Unit,
        onAcked: suspend () -> Unit
    ) {
        val output = openStreamOrReport(client, "storeFileStream", errorSuffix, send) { it.storeFileStream(ftpPath) }
            ?: return
        executeTransfer(client, output, "upload 실패", onSend = send, onSuccess = onAcked) {
            localFile.inputStream().use { input -> pump(input, output, onChunk) }
        }
    }

    /**
     * FTP 데이터 스트림을 연다. 실패하면 응답을 정리하고 오류를 보고하며,
     * 5xx(영구 오류)가 아니면 연결 끊김으로 간주해 세션을 정리한 뒤 null을 반환한다.
     */
    private suspend fun <T> openStreamOrReport(
        client: FTPClient,
        commandName: String,
        errorSuffix: String,
        send: suspend (ProgressState) -> Unit,
        open: (FTPClient) -> T?
    ): T? {
        val stream = open(client)
        if (stream == null) {
            val replyCode = client.replyCode
            val replyString = client.replyString.trim()
            runCatching { client.completePendingCommand() }
            send(ProgressState(error = "$commandName 실패: $replyString$errorSuffix"))
            if (replyCode !in 500..599) session.markConnectionDropped()
        }
        return stream
    }

    /** FTP 스트림 전송의 오류 처리와 채널 정리를 담당한다. 취소 시 onCancel을 실행하고 CancellationException을 재전파한다. */
    private suspend fun executeTransfer(
        client: FTPClient,
        stream: Closeable,
        errorMessage: String,
        onCancel: () -> Unit = {},
        onSend: suspend (ProgressState) -> Unit,
        onSuccess: suspend () -> Unit = {},
        block: suspend () -> Unit
    ) {
        var transferFailed = false
        try {
            block()
        } catch (e: CancellationException) {
            onCancel()
            throw e
        } catch (e: Exception) {
            transferFailed = true
            if (e is FTPConnectionClosedException) session.markConnectionDropped()
            onSend(ProgressState(error = e.message ?: errorMessage))
        } finally {
            runCatching { stream.close() }
            val ok = runCatching { client.completePendingCommand() }.getOrDefault(false)
            if (!transferFailed && !ok) {
                onSend(ProgressState(error = "전송 미완료: ${client.replyString.trim()}"))
            } else if (!transferFailed) {
                onSuccess()
            }
        }
    }

    /** [input]을 버퍼 단위로 [output]에 복사하며 청크마다 [onChunk]에 읽은 바이트 수를 전달한다. */
    private inline fun pump(input: InputStream, output: OutputStream, onChunk: (Int) -> Unit) {
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            onChunk(bytesRead)
        }
    }

    /**
     * 디렉터리 전송용 누적 진행률 계산기를 만든다. 서버 ACK 전 100% 조기 보고를 막기 위해 100%는 마지막 파일 완료 시에만 보고한다.
     * @param totalBytes 전송 대상 전체 바이트 수
     * @param totalCount 전송 대상 파일 개수
     * @param onProgress 진행 상태를 수신하는 콜백
     */
    private fun directoryProgress(totalBytes: Long, totalCount: Int, onProgress: (ProgressState) -> Unit) =
        ByteProgressTracker(totalBytes, totalCount, onProgress, holdBelow100 = true, reportProgressFloat = true)

    private fun buildProgressState(totalBytes: Long, writtenBytes: Long, fileName: String) = ProgressState(
        totalBytes = totalBytes,
        writtenBytes = writtenBytes,
        progress = if (totalBytes > 0) (writtenBytes * 100 / totalBytes).toInt() else null,
        progressFloat = if (totalBytes > 0) writtenBytes.toFloat() / totalBytes else null,
        currentFileName = fileName
    )

    companion object {
        private const val BUFFER_SIZE = 128 * 1024
    }
}
