package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.data.progress.ProgressThrottle
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.RemoteConnectionLostException
import com.todokanai.composepracticenew.operation.forEachCollectingFailures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FTP 파일 다운로드와 업로드를 스트리밍으로 수행하며 진행률을 보고한다.
 * 전송의 한쪽 끝이 로컬 파일이므로 로컬 스트림 개폐는 이 클래스가 담당하고,
 * 스트림 획득 실패 처리·전송 완료 확인은 공통 private 함수로, 디렉터리 진행률은 로컬 작업과 공유하는 [ByteProgressTracker]로 계산한다.
 * 실패는 예외로 알리며, 연결이 없거나 전송 중 끊기면 [RemoteConnectionLostException]을 던진다.
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
     * isDirectory가 true이면 원격 트리를 재귀 수집 후 각 파일을 순서대로 다운로드하며, 일부 파일이 실패해도 끝까지 진행한 뒤 실패를 모아 던진다.
     * 받다 실패한 로컬 파일은 남겨 두고, 취소된 경우에만 지운다.
     * @param remotePath ftp://server/path 형식의 원격 절대 경로
     * @param localPath 저장할 로컬 파일 또는 디렉터리의 절대 경로
     * @param isDirectory remotePath가 디렉터리인 경우 true
     * @param onProgress 진행 상태를 수신하는 콜백
     */
    suspend fun download(remotePath: String, localPath: String, isDirectory: Boolean = false, onProgress: (ProgressState) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            if (!isDirectory) downloadFile(remotePath, File(localPath), onProgress)
            else downloadDirectory(remotePath, localPath, onProgress)
        }

    /**
     * localPath의 파일 또는 디렉터리를 remotePath에 스트리밍 업로드한다.
     * localPath가 디렉터리이면 로컬 트리를 walkTopDown으로 수집 후 각 항목을 순서대로 업로드하며, 일부 파일이 실패해도 끝까지 진행한 뒤 실패를 모아 던진다.
     * @param localPath 업로드할 로컬 파일 또는 디렉터리의 절대 경로
     * @param remotePath 저장될 원격 파일 또는 디렉터리의 절대 경로 (ftp://server/path 형식)
     * @param onProgress 진행 상태를 수신하는 콜백
     */
    suspend fun upload(localPath: String, remotePath: String, onProgress: (ProgressState) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            val localFile = File(localPath)
            val ftpPath = extractFtpPath(remotePath)
            if (!localFile.isDirectory) uploadFile(localFile, ftpPath, onProgress)
            else uploadDirectory(localFile, ftpPath, onProgress)
        }

    /**
     * 단일 원격 파일을 [localFile]로 다운로드한다. 크기 확인 시 100% 미만까지만 보고하며,
     * 청크 단위 보고는 [ProgressThrottle]로 퍼센트 변경 또는 일정 시간 경과 시에만 전달한다.
     */
    private suspend fun downloadFile(
        remotePath: String,
        localFile: File,
        onProgress: (ProgressState) -> Unit
    ) {
        val ftpPath = extractFtpPath(remotePath)
        session.withConnectedClient { client ->
            localFile.parentFile?.mkdirs()
            val totalBytes = runCatching { client.mlistFile(ftpPath)?.size ?: -1L }.getOrDefault(-1L)
            var writtenBytes = 0L
            val throttle = ProgressThrottle()
            downloadOne(
                client, ftpPath, localFile,
                onChunk = { bytesRead ->
                    writtenBytes += bytesRead
                    // ACK 전 100% 조기 발행 방지 — totalBytes 미확인 시는 시간 기준으로만 보고
                    if (totalBytes <= 0 || writtenBytes < totalBytes) {
                        val state = buildProgressState(totalBytes, writtenBytes, localFile.name)
                        if (throttle.shouldReport(state.progress)) onProgress(state)
                    }
                },
                onAcked = {
                    // totalBytes > 0 인 경우에만 100% 보고 — 크기 미확인 시 progress=null이므로 생략
                    if (totalBytes > 0) onProgress(buildProgressState(totalBytes, totalBytes, localFile.name))
                }
            )
        }
    }

    /** 원격 디렉터리 트리를 [localPath] 아래로 다운로드한다. 실패한 파일은 모아서 마지막에 던진다. */
    private suspend fun downloadDirectory(
        remotePath: String,
        localPath: String,
        onProgress: (ProgressState) -> Unit
    ) {
        val allEntries = cmd.collectRemoteTree(remotePath)
        val fileEntries = allEntries.filter { !it.isDirectory }
        val progress = directoryProgress(fileEntries.sumOf { it.size }.coerceAtLeast(1), fileEntries.size, onProgress)
        File(localPath).mkdirs()
        forEachCollectingFailures(allEntries, "다운로드 실패", nameOf = { relativePathOf(it.path, remotePath) }) { entry ->
            val localTarget = File(localPath, relativePathOf(entry.path, remotePath))
            if (entry.isDirectory) {
                localTarget.mkdirs()
            } else {
                progress.startFile()
                val entryFtpPath = extractFtpPath(entry.path)
                session.withConnectedClient { client ->
                    localTarget.parentFile?.mkdirs()
                    downloadOne(
                        client, entryFtpPath, localTarget,
                        onChunk = { bytesRead -> progress.addBytes(bytesRead.toLong(), entry.name, entry.size) },
                        onAcked = { progress.onFileCompleted(entry.name, entry.size) }
                    )
                }
            }
        }
        progress.reportIfEmpty()
    }

    /**
     * 단일 로컬 파일을 [ftpPath]로 업로드한다. 서버 확인 전까지 100% 미만만 보고하며,
     * 청크 단위 보고는 [ProgressThrottle]로 퍼센트 변경 또는 일정 시간 경과 시에만 전달한다.
     */
    private suspend fun uploadFile(
        localFile: File,
        ftpPath: String,
        onProgress: (ProgressState) -> Unit
    ) {
        session.withConnectedClient { client ->
            val totalBytes = localFile.length()
            var writtenBytes = 0L
            val throttle = ProgressThrottle()
            uploadOne(
                client, ftpPath, localFile,
                onChunk = { bytesRead ->
                    writtenBytes += bytesRead
                    // ACK 전 100% 조기 발행 방지 — 서버 확인 후 onAcked에서 보고
                    if (writtenBytes < totalBytes) {
                        val state = buildProgressState(totalBytes, writtenBytes, localFile.name)
                        if (throttle.shouldReport(state.progress)) onProgress(state)
                    }
                },
                onAcked = { onProgress(buildProgressState(totalBytes, totalBytes, localFile.name)) }
            )
        }
    }

    /**
     * 로컬 디렉터리 트리를 [ftpPath] 아래로 업로드한다. 실패한 파일은 모아서 마지막에 던진다.
     * 원격 폴더 생성은 이미 있는 경우에도 false가 오므로 결과를 보지 않으며, 폴더가 실제로 없으면 그 안의 파일 업로드가 실패로 집계된다.
     */
    private suspend fun uploadDirectory(
        localDir: File,
        ftpPath: String,
        onProgress: (ProgressState) -> Unit
    ) {
        val allEntries = localDir.walkTopDown().toList()
        val fileEntries = allEntries.filter { it.isFile }
        val progress = directoryProgress(fileEntries.sumOf { it.length() }.coerceAtLeast(1), fileEntries.size, onProgress)
        forEachCollectingFailures(allEntries, "업로드 실패", nameOf = { localDir.relativePathOf(it).ifEmpty { it.name } }) { entry ->
            val relativePath = localDir.relativePathOf(entry)
            val entryFtpPath = if (relativePath.isEmpty()) ftpPath else "$ftpPath/$relativePath"
            if (entry.isDirectory) {
                session.withConnectedClient { client -> client.makeDirectory(entryFtpPath) }
            } else {
                progress.startFile()
                val entryBytes = entry.length()
                session.withConnectedClient { client ->
                    uploadOne(
                        client, entryFtpPath, entry,
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
     * 진행률 보고 규칙은 호출부가 [onChunk]·[onAcked]로 결정한다. 실패하면 예외를 던지고, 받다 만 파일은 취소된 경우에만 지운다.
     * @param client 잠금을 확보한 FTPClient
     * @param ftpPath 받을 원격 파일의 FTP 경로
     * @param localFile 저장할 로컬 파일 — 취소 시 삭제된다
     * @param onChunk 버퍼마다 읽은 바이트 수를 받는 콜백
     * @param onAcked 서버가 전송 완료를 확인한 뒤 호출되는 콜백
     */
    private suspend fun downloadOne(
        client: FTPClient,
        ftpPath: String,
        localFile: File,
        onChunk: (Int) -> Unit,
        onAcked: suspend () -> Unit
    ) {
        val input = openStreamOrThrow(client, "retrieveFileStream") { it.retrieveFileStream(ftpPath) }
        executeTransfer(client, input, onCancel = { runCatching { localFile.delete() } }, onAcked = onAcked) {
            localFile.outputStream().use { output -> pump(input, output, onChunk) }
        }
    }

    /**
     * 세션 잠금 안에서 로컬 파일 [localFile] 하나를 원격으로 올린다. 단일 파일·디렉터리 업로드가 공유하며,
     * 진행률 보고 규칙은 호출부가 [onChunk]·[onAcked]로 결정한다. 실패하면 예외를 던진다.
     * @param client 잠금을 확보한 FTPClient
     * @param ftpPath 저장될 원격 파일의 FTP 경로
     * @param localFile 업로드할 로컬 파일
     * @param onChunk 버퍼마다 쓴 바이트 수를 받는 콜백
     * @param onAcked 서버가 전송 완료를 확인한 뒤 호출되는 콜백
     */
    private suspend fun uploadOne(
        client: FTPClient,
        ftpPath: String,
        localFile: File,
        onChunk: (Int) -> Unit,
        onAcked: suspend () -> Unit
    ) {
        val output = openStreamOrThrow(client, "storeFileStream") { it.storeFileStream(ftpPath) }
        executeTransfer(client, output, onAcked = onAcked) {
            localFile.inputStream().use { input -> pump(input, output, onChunk) }
        }
    }

    /**
     * FTP 데이터 스트림을 연다. 열지 못하면 응답을 정리하고, 5xx(영구 오류)가 아니면 연결 끊김으로 간주해 세션을 끊김 상태로 바꾼 뒤
     * 서버 응답을 담아 예외를 던진다. 세션이 끊김 상태가 되었으므로 [FtpSession.withConnectedClient]가 이를 연결 끊김으로 알린다.
     * @param client 잠금을 확보한 FTPClient
     * @param commandName 실패 메시지에 표시할 명령 이름
     * @param open 스트림을 여는 FTP 명령. 실패하면 null을 반환한다
     * @return 열린 스트림
     */
    private suspend fun <T : Any> openStreamOrThrow(
        client: FTPClient,
        commandName: String,
        open: (FTPClient) -> T?
    ): T {
        open(client)?.let { return it }
        val replyCode = client.replyCode
        val replyString = client.replyString.trim()
        runCatching { client.completePendingCommand() }
        if (replyCode !in 500..599) session.markConnectionDropped()
        throw IOException("$commandName 실패: $replyString")
    }

    /**
     * FTP 스트림 전송을 실행하고 채널을 정리한다. 전송 중 예외는 그대로 전파하고, 서버가 전송 완료를 확인하지 않으면 예외를 던진다.
     * 취소 시 onCancel을 실행하고 CancellationException을 재전파한다.
     * @param client 잠금을 확보한 FTPClient
     * @param stream 전송에 사용한 데이터 스트림 — 끝나면 닫는다
     * @param onCancel 취소 시 실행할 정리 작업
     * @param onAcked 서버가 전송 완료를 확인한 뒤 호출되는 콜백
     * @param block 실제 전송 작업
     */
    private suspend fun executeTransfer(
        client: FTPClient,
        stream: Closeable,
        onCancel: () -> Unit = {},
        onAcked: suspend () -> Unit,
        block: suspend () -> Unit
    ) {
        var completed = false
        try {
            block()
        } catch (e: CancellationException) {
            onCancel()
            throw e
        } finally {
            runCatching { stream.close() }
            completed = runCatching { client.completePendingCommand() }.getOrDefault(false)
        }
        if (!completed) throw IOException("전송 미완료: ${client.replyString.trim()}")
        onAcked()
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
     * 원격 트리 항목 경로를 기준 디렉터리에 대한 상대 경로로 바꾼다.
     * @param entryPath 원격 트리 항목의 경로
     * @param rootPath 다운로드 기준 원격 디렉터리 경로
     * @return 기준 디렉터리 아래 상대 경로
     */
    private fun relativePathOf(entryPath: String, rootPath: String): String = entryPath.removePrefix(rootPath).trimStart('/')

    /**
     * 로컬 트리 항목을 이 디렉터리에 대한 '/' 구분 상대 경로로 바꾼다. 자기 자신이면 빈 문자열이다.
     * @param entry 이 디렉터리 아래의 항목
     * @return '/' 구분 상대 경로
     */
    private fun File.relativePathOf(entry: File): String = toPath().relativize(entry.toPath()).toString().replace("\\", "/")

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
