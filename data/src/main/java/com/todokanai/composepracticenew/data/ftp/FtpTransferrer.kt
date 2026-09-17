package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import org.apache.commons.net.ftp.FTPConnectionClosedException
import java.io.Closeable
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** FTP 파일 다운로드와 업로드를 스트리밍으로 수행하며 진행률을 ProgressState Flow로 방출한다. */
@Singleton
class FtpTransferrer @Inject constructor(
    private val cs: FtpConnectionState,
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
        launch(cs.ftpIoJob + Dispatchers.IO) {
            val (connected, ftpPath) = cs.stateMutex.withLock {
                (cs.client.isConnected && cs.isLoggedIn) to extractFtpPath(remotePath)
            }
            if (!connected) {
                send(ProgressState(error = "Not connected"))
                return@launch
            }
            if (!isDirectory) {
                cs.ioMutex.withLock {
                    val localFile = File(localPath)
                    localFile.parentFile?.mkdirs()
                    val totalBytes = runCatching { cs.client.mlistFile(ftpPath)?.size ?: -1L }.getOrDefault(-1L)
                    val inputStream = cs.client.retrieveFileStream(ftpPath)
                    if (inputStream == null) {
                        val replyCode = cs.client.replyCode
                        val replyString = cs.client.replyString.trim()
                        runCatching { cs.client.completePendingCommand() }
                        send(ProgressState(error = "retrieveFileStream 실패: $replyString"))
                        if (replyCode !in 500..599) cs.markConnectionDropped()
                        return@withLock
                    }
                    executeTransfer(inputStream, "download 실패", onCancel = { runCatching { localFile.delete() } }, onSend = { send(it) }, onSuccess = {
                        // totalBytes > 0 인 경우에만 100% 보고 — 크기 미확인 시 progress=null이므로 생략
                        if (totalBytes > 0) onProgress(buildProgressState(totalBytes, totalBytes, localFile.name))
                    }) {
                        val buffer = ByteArray(BUFFER_SIZE)
                        var writtenBytes = 0L
                        localFile.outputStream().use { output ->
                            var bytesRead: Int
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                writtenBytes += bytesRead
                                // ACK 전 100% 조기 발행 방지 — totalBytes 미확인 시는 항상 보고
                                if (totalBytes <= 0 || writtenBytes < totalBytes) onProgress(buildProgressState(totalBytes, writtenBytes, localFile.name))
                            }
                        }
                    }
                }
            } else {
                val allEntries = cmd.collectRemoteTree(remotePath)
                val fileEntries = allEntries.filter { !it.isDirectory }
                val totalBytes = fileEntries.sumOf { it.size }.coerceAtLeast(1)
                val totalCount = fileEntries.size
                File(localPath).mkdirs()
                var writtenBytes = 0L
                var prevProgress = -1
                var fileIndex = 0
                allEntries.forEach { entry ->
                    val relativePath = entry.path.removePrefix(remotePath).trimStart('/')
                    val localTarget = File(localPath, relativePath)
                    if (entry.isDirectory) {
                        localTarget.mkdirs()
                    } else {
                        fileIndex++
                        val entryFtpPath = extractFtpPath(entry.path)
                        cs.ioMutex.withLock {
                            localTarget.parentFile?.mkdirs()
                            val inputStream = cs.client.retrieveFileStream(entryFtpPath)
                            if (inputStream == null) {
                                val replyCode = cs.client.replyCode
                                val replyString = cs.client.replyString.trim()
                                runCatching { cs.client.completePendingCommand() }
                                send(ProgressState(error = "retrieveFileStream 실패: $replyString entry=${entry.name}"))
                                if (replyCode !in 500..599) cs.markConnectionDropped()
                                return@withLock
                            }
                            executeTransfer(inputStream, "download 실패", onCancel = { runCatching { localTarget.delete() } }, onSend = { send(it) }, onSuccess = {
                                // 마지막 파일(fileIndex == totalCount) ACK 후에만 100% 보고 — writtenBytes 누적 오차 방지
                                if (fileIndex == totalCount) {
                                    onProgress(ProgressState(
                                        progress = 100,
                                        progressFloat = 1f,
                                        totalBytes = totalBytes,
                                        writtenBytes = totalBytes,
                                        listSize = totalCount,
                                        currentIndex = fileIndex,
                                        currentFileName = entry.name,
                                        currentFileBytes = entry.size
                                    ))
                                }
                            }) {
                                val buffer = ByteArray(BUFFER_SIZE)
                                localTarget.outputStream().use { output ->
                                    var bytesRead: Int
                                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                        output.write(buffer, 0, bytesRead)
                                        writtenBytes += bytesRead
                                        val progress = (writtenBytes * 100 / totalBytes).toInt()
                                        // ACK 전 100% 조기 발행 방지 — 서버 확인 후 onSuccess에서 보고
                                        if (progress != prevProgress && progress < 100) {
                                            onProgress(ProgressState(
                                                progress = progress,
                                                progressFloat = writtenBytes.toFloat() / totalBytes,
                                                totalBytes = totalBytes,
                                                writtenBytes = writtenBytes,
                                                listSize = totalCount,
                                                currentIndex = fileIndex,
                                                currentFileName = entry.name,
                                                currentFileBytes = entry.size
                                            ))
                                            prevProgress = progress
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (totalCount == 0) {
                    onProgress(ProgressState(progress = 100, progressFloat = 1f, totalBytes = 1, writtenBytes = 1, listSize = 0, currentIndex = 0))
                }
            }
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
        launch(cs.ftpIoJob + Dispatchers.IO) {
            val (connected, ftpPath) = cs.stateMutex.withLock {
                (cs.client.isConnected && cs.isLoggedIn) to extractFtpPath(remotePath)
            }
            if (!connected) {
                send(ProgressState(error = "Not connected"))
                return@launch
            }
            val localFile = File(localPath)
            if (!localFile.isDirectory) {
                cs.ioMutex.withLock {
                    val totalBytes = localFile.length()
                    val outputStream = cs.client.storeFileStream(ftpPath)
                    if (outputStream == null) {
                        val replyCode = cs.client.replyCode
                        val replyString = cs.client.replyString.trim()
                        runCatching { cs.client.completePendingCommand() }
                        send(ProgressState(error = "storeFileStream 실패: $replyString"))
                        if (replyCode !in 500..599) cs.markConnectionDropped()
                        return@withLock
                    }
                    executeTransfer(outputStream, "upload 실패", onSend = { send(it) }, onSuccess = {
                        onProgress(buildProgressState(totalBytes, totalBytes, localFile.name))
                    }) {
                        val buffer = ByteArray(BUFFER_SIZE)
                        var writtenBytes = 0L
                        localFile.inputStream().use { input ->
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                writtenBytes += bytesRead
                                // ACK 전 100% 조기 발행 방지 — 서버 확인 후 onSuccess에서 보고
                                if (writtenBytes < totalBytes) onProgress(buildProgressState(totalBytes, writtenBytes, localFile.name))
                            }
                        }
                    }
                }
            } else {
                val allEntries = localFile.walkTopDown().toList()
                val fileEntries = allEntries.filter { it.isFile }
                val totalBytes = fileEntries.sumOf { it.length() }.coerceAtLeast(1)
                val totalCount = fileEntries.size
                var writtenBytes = 0L
                var prevProgress = -1
                var fileIndex = 0
                allEntries.forEach { entry ->
                    val relativePath = localFile.toPath().relativize(entry.toPath()).toString().replace("\\", "/")
                    val entryFtpPath = if (relativePath.isEmpty()) ftpPath else "$ftpPath/$relativePath"
                    if (entry.isDirectory) {
                        cs.ioMutex.withLock { runCatching { cs.client.makeDirectory(entryFtpPath) } }
                    } else {
                        fileIndex++
                        cs.ioMutex.withLock {
                            val outputStream = cs.client.storeFileStream(entryFtpPath)
                            if (outputStream == null) {
                                val replyCode = cs.client.replyCode
                                val replyString = cs.client.replyString.trim()
                                runCatching { cs.client.completePendingCommand() }
                                send(ProgressState(error = "storeFileStream 실패: $replyString entry=${entry.name}"))
                                if (replyCode !in 500..599) cs.markConnectionDropped()
                                return@withLock
                            }
                            executeTransfer(outputStream, "upload 실패", onSend = { send(it) }, onSuccess = {
                                // 마지막 파일 인덱스 기준으로 100% 보고 — 0바이트 파일 포함 시 바이트 비교 오판 방지
                                if (fileIndex == totalCount) {
                                    onProgress(ProgressState(
                                        progress = 100,
                                        progressFloat = 1f,
                                        totalBytes = totalBytes,
                                        writtenBytes = totalBytes,
                                        listSize = totalCount,
                                        currentIndex = fileIndex,
                                        currentFileName = entry.name,
                                        currentFileBytes = entry.length()
                                    ))
                                }
                            }) {
                                val buffer = ByteArray(BUFFER_SIZE)
                                entry.inputStream().use { input ->
                                    var bytesRead: Int
                                    while (input.read(buffer).also { bytesRead = it } != -1) {
                                        outputStream.write(buffer, 0, bytesRead)
                                        writtenBytes += bytesRead
                                        val progress = (writtenBytes * 100 / totalBytes).toInt()
                                        // ACK 전 100% 조기 발행 방지 — 서버 확인 후 onSuccess에서 보고
                                        if (progress != prevProgress && progress < 100) {
                                            onProgress(ProgressState(
                                                progress = progress,
                                                progressFloat = writtenBytes.toFloat() / totalBytes,
                                                totalBytes = totalBytes,
                                                writtenBytes = writtenBytes,
                                                listSize = totalCount,
                                                currentIndex = fileIndex,
                                                currentFileName = entry.name,
                                                currentFileBytes = entry.length()
                                            ))
                                            prevProgress = progress
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.join()
    }

    /** FTP 스트림 전송의 오류 처리와 채널 정리를 담당한다. 취소 시 onCancel을 실행하고 CancellationException을 재전파한다. */
    private suspend fun executeTransfer(
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
            if (e is FTPConnectionClosedException) cs.markConnectionDropped()
            onSend(ProgressState(error = e.message ?: errorMessage))
        } finally {
            runCatching { stream.close() }
            val ok = runCatching { cs.client.completePendingCommand() }.getOrDefault(false)
            if (!transferFailed && !ok) {
                onSend(ProgressState(error = "전송 미완료: ${cs.client.replyString.trim()}"))
            } else if (!transferFailed) {
                onSuccess()
            }
        }
    }

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
