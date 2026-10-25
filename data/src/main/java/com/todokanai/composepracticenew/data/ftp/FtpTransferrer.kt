package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.data.progress.ProgressThrottle
import com.todokanai.composepracticenew.datasource.slashRelativePathOf
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.onSuccess
import com.todokanai.composepracticenew.model.valueOr
import com.todokanai.composepracticenew.operation.forEachCollectingFailures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPCmd
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.parser.MLSxEntryParser
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
 * 실패는 결과로 알리며, 연결이 없거나 전송 중 끊기면 [IoResult.ConnectionLost]를 돌려준다.
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
     * isDirectory가 true이면 원격 트리를 재귀 수집 후 각 파일을 순서대로 다운로드하며, 일부 파일이 실패해도 끝까지 진행한 뒤 실패를 모아 알린다.
     * 받다 실패한 로컬 파일은 남겨 두고, 취소된 경우에만 지운다.
     * @param remotePath ftp://server/path 형식의 원격 절대 경로
     * @param localPath 저장할 로컬 파일 또는 디렉터리의 절대 경로
     * @param isDirectory remotePath가 디렉터리인 경우 true
     * @param onProgress 진행 상태를 수신하는 콜백
     * @return 전송 결과
     */
    suspend fun download(remotePath: String, localPath: String, isDirectory: Boolean = false, onProgress: (ProgressState) -> Unit = {}): IoResult<Unit> =
        withContext(Dispatchers.IO) {
            if (!isDirectory) downloadFile(remotePath, File(localPath), onProgress)
            else downloadDirectory(remotePath, localPath, onProgress)
        }

    /**
     * localPath의 파일 또는 디렉터리를 remotePath에 스트리밍 업로드한다.
     * localPath가 디렉터리이면 로컬 트리를 walkTopDown으로 수집 후 각 항목을 순서대로 업로드하며, 일부 파일이 실패해도 끝까지 진행한 뒤 실패를 모아 알린다.
     * @param localPath 업로드할 로컬 파일 또는 디렉터리의 절대 경로
     * @param remotePath 저장될 원격 파일 또는 디렉터리의 절대 경로 (ftp://server/path 형식)
     * @param onProgress 진행 상태를 수신하는 콜백
     * @return 전송 결과
     */
    suspend fun upload(localPath: String, remotePath: String, onProgress: (ProgressState) -> Unit = {}): IoResult<Unit> =
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
    ): IoResult<Unit> {
        val ftpPath = extractFtpPath(remotePath)
        return session.withConnectedClient { client ->
            localFile.parentFile?.mkdirs()
            val totalBytes = remoteFileSize(client, ftpPath)
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

    /** 원격 디렉터리 트리를 [localPath] 아래로 다운로드한다. 실패한 파일은 모아서 마지막에 알린다. */
    private suspend fun downloadDirectory(
        remotePath: String,
        localPath: String,
        onProgress: (ProgressState) -> Unit
    ): IoResult<Unit> {
        val allEntries = cmd.collectRemoteTree(remotePath).valueOr { return it }
        val fileEntries = allEntries.filter { !it.isDirectory }
        val progress = directoryProgress(fileEntries.sumOf { it.size }.coerceAtLeast(1), fileEntries.size, onProgress)
        File(localPath).mkdirs()
        return forEachCollectingFailures(allEntries, "다운로드 실패", nameOf = { relativeRemotePath(it.path, remotePath) }) { entry ->
            val localTarget = File(localPath, relativeRemotePath(entry.path, remotePath))
            if (entry.isDirectory) {
                localTarget.mkdirs()
                IoResult.Done
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
        }.onSuccess { progress.reportIfEmpty() }
    }

    /**
     * 단일 로컬 파일을 [ftpPath]로 업로드한다. 서버 확인 전까지 100% 미만만 보고하며,
     * 청크 단위 보고는 [ProgressThrottle]로 퍼센트 변경 또는 일정 시간 경과 시에만 전달한다.
     */
    private suspend fun uploadFile(
        localFile: File,
        ftpPath: String,
        onProgress: (ProgressState) -> Unit
    ): IoResult<Unit> =
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

    /**
     * 로컬 디렉터리 트리를 [ftpPath] 아래로 업로드한다. 실패한 파일은 모아서 마지막에 알린다.
     * 원격 폴더 생성은 이미 있는 경우에도 거부 응답이 오므로 결과를 보지 않으며, 폴더가 실제로 없으면 그 안의 파일 업로드가 실패로 집계된다.
     */
    private suspend fun uploadDirectory(
        localDir: File,
        ftpPath: String,
        onProgress: (ProgressState) -> Unit
    ): IoResult<Unit> {
        val allEntries = localDir.walkTopDown().toList()
        val fileEntries = allEntries.filter { it.isFile }
        val progress = directoryProgress(fileEntries.sumOf { it.length() }.coerceAtLeast(1), fileEntries.size, onProgress)
        return forEachCollectingFailures(allEntries, "업로드 실패", nameOf = { localDir.slashRelativePathOf(it).ifEmpty { it.name } }) { entry ->
            val relativePath = localDir.slashRelativePathOf(entry)
            val entryFtpPath = if (relativePath.isEmpty()) ftpPath else "$ftpPath/$relativePath"
            if (entry.isDirectory) {
                session.withConnectedClient { client ->
                    client.makeDirectory(entryFtpPath)
                    IoResult.Done
                }
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
     * 진행률 보고 규칙은 호출부가 [onChunk]·[onAcked]로 결정한다. 받다 만 파일은 취소된 경우에만 지운다.
     * @param client 잠금을 확보한 FTPClient
     * @param ftpPath 받을 원격 파일의 FTP 경로
     * @param localFile 저장할 로컬 파일 — 취소 시 삭제된다
     * @param onChunk 버퍼마다 읽은 바이트 수를 받는 콜백
     * @param onAcked 서버가 전송 완료를 확인한 뒤 호출되는 콜백
     * @return 전송 결과
     */
    private suspend fun downloadOne(
        client: FTPClient,
        ftpPath: String,
        localFile: File,
        onChunk: (Int) -> Unit,
        onAcked: suspend () -> Unit
    ): IoResult<Unit> {
        // 스트림을 열지 못했으면(PASV·REST 실패, RETR의 1xx 아닌 응답) 응답을 끝까지 읽은 것이다 — 응답 코드와 관계없이 이 항목만 실패다.
        // 기다릴 응답이 없으므로 completePendingCommand는 부르지 않는다. 부르면 오지 않을 응답을 soTimeout까지 기다리며 세션 잠금을 쥔다
        val input = client.retrieveFileStream(ftpPath) ?: return FtpReplyRules.refusal(FtpCommand.RETR, client)
        return executeTransfer(client, input, onCancel = { runCatching { localFile.delete() } }, onAcked = onAcked) {
            localFile.outputStream().use { output -> pump(input, output, onChunk) }
        }
    }

    /**
     * 세션 잠금 안에서 로컬 파일 [localFile] 하나를 원격으로 올린다. 단일 파일·디렉터리 업로드가 공유하며,
     * 진행률 보고 규칙은 호출부가 [onChunk]·[onAcked]로 결정한다.
     * @param client 잠금을 확보한 FTPClient
     * @param ftpPath 저장될 원격 파일의 FTP 경로
     * @param localFile 업로드할 로컬 파일
     * @param onChunk 버퍼마다 쓴 바이트 수를 받는 콜백
     * @param onAcked 서버가 전송 완료를 확인한 뒤 호출되는 콜백
     * @return 전송 결과
     */
    private suspend fun uploadOne(
        client: FTPClient,
        ftpPath: String,
        localFile: File,
        onChunk: (Int) -> Unit,
        onAcked: suspend () -> Unit
    ): IoResult<Unit> {
        // 스트림을 열지 못한 경우의 처리는 [downloadOne]과 같다
        // 알려진 문제(방치하기로 결정): STOR는 기존 파일을 덮어쓴다. 서버가 목록에서 숨긴 같은 이름의 파일은 충돌 검사에 보이지 않아 묻지 않고 덮어쓴다
        // ([FtpCommandExecutor.identifiedListing] 참고)
        val output = client.storeFileStream(ftpPath) ?: return FtpReplyRules.refusal(FtpCommand.STOR, client)
        return executeTransfer(client, output, onAcked = onAcked) {
            localFile.inputStream().use { input -> pump(input, output, onChunk) }
        }
    }

    /**
     * FTP 스트림 전송을 실행하고 채널을 정리한다. 서버가 전송 완료를 확인해야 성공이다.
     * 전송(데이터 스트림·로컬 파일) 중 IOException은 제어 채널과 무관하므로, 완료 응답까지 정상적으로 읽었다면 이 항목만의 실패로 돌려준다.
     * 완료 응답을 읽다 실패하면 제어 채널을 더 쓸 수 없으므로 그 예외를 전파해 [FtpSession.withConnectedClient]가 세션을 종료하게 한다(전송 예외는 suppressed로 붙인다).
     * 취소와 구현 오류는 결과로 바꾸지 않고 전파한다. 구현 오류는 [FtpSession.withConnectedClient]가 세션을 종료하므로 스트림만 닫는다.
     * 취소는 세션을 종료하지 않고 전파되므로, onCancel을 실행하고 완료 응답을 기다려 응답 순서를 맞춘다. 완료 응답을 읽지 못했으면 여기서 세션을 종료한다.
     * @param client 잠금을 확보한 FTPClient
     * @param stream 전송에 사용한 데이터 스트림 — 끝나면 닫는다
     * @param onCancel 취소 시 실행할 정리 작업
     * @param onAcked 서버가 전송 완료를 확인한 뒤 호출되는 콜백
     * @param block 실제 전송 작업
     * @return 전송 결과
     */
    private suspend fun executeTransfer(
        client: FTPClient,
        stream: Closeable,
        onCancel: () -> Unit = {},
        onAcked: suspend () -> Unit,
        block: suspend () -> Unit
    ): IoResult<Unit> {
        val transferFailure = try {
            block()
            null
        } catch (e: IOException) {
            e
        } catch (e: CancellationException) {
            onCancel()
            try {
                closeAndAwaitCompletion(client, stream)
            } catch (_: IOException) {
                session.endUnusableSession()
            }
            throw e
        } catch (e: Exception) {
            runCatching { stream.close() }
            throw e
        }
        val acknowledged = try {
            closeAndAwaitCompletion(client, stream)
        } catch (controlFailure: IOException) {
            transferFailure?.let(controlFailure::addSuppressed)
            throw controlFailure
        }
        return when {
            transferFailure != null -> IoResult.ItemFailed(transferFailure.message ?: "전송 실패")
            !acknowledged -> IoResult.ItemFailed("전송 미완료: ${client.replyString.trim()}")
            else -> {
                onAcked()
                IoResult.Done
            }
        }
    }

    /**
     * 데이터 스트림을 닫고 서버의 전송 완료 응답을 확인한다.
     * 응답을 읽다 실패하면(연결 끊김·타임아웃 등) 그 IOException을 던진다.
     * @param client 잠금을 확보한 FTPClient
     * @param stream 닫을 데이터 스트림
     * @return 서버가 전송 완료를 확인했으면 true
     */
    private fun closeAndAwaitCompletion(client: FTPClient, stream: Closeable): Boolean {
        runCatching { stream.close() }
        return client.completePendingCommand()
    }

    /**
     * 진행률 계산용으로 원격 파일 크기를 조회한다. 서버가 MLST를 거부하거나 응답의 항목 줄을 해석하지 못하면 크기를 모르는 것(-1)으로 본다.
     * 응답을 끝까지 읽은 뒤 항목 줄만 해석하므로, 응답을 읽다 실패한 예외(연결 끊김·타임아웃·응답 형식 오류)는 삼키지 않고 전파해
     * [FtpSession.withConnectedClient]가 세션을 종료하게 한다. FTPClient.mlistFile은 응답 읽기 실패와 항목 해석 실패를 같은 예외로 섞어 던지므로 쓰지 않는다.
     * @param client 잠금을 확보한 FTPClient
     * @param ftpPath 크기를 조회할 원격 파일의 FTP 경로
     * @return 파일 크기(bytes), 알 수 없으면 -1
     */
    private fun remoteFileSize(client: FTPClient, ftpPath: String): Long {
        if (!FTPReply.isPositiveCompletion(client.sendCommand(FTPCmd.MLST, ftpPath))) return -1L
        return mlstEntrySize(client.replyStrings)
    }

    /**
     * MLST 응답의 항목 줄에서 size 사실을 읽는다. 항목 줄은 응답의 둘째 줄이며 공백으로 시작한다(RFC 3659 §7.2).
     * 항목 줄이 없거나 해석할 수 없는 값(숫자가 아닌 size, 짧은 unix.mode 등 commons-net 해석기가 예외를 던지는 값)이면 크기를 모른다.
     * @param replyLines 응답을 끝까지 읽은 MLST 응답 줄
     * @return 파일 크기(bytes), 알 수 없으면 -1
     */
    private fun mlstEntrySize(replyLines: Array<String>): Long {
        val entryLine = replyLines.getOrNull(1)?.trimStart()?.takeIf { it.isNotEmpty() } ?: return -1L
        val entry = try {
            MLSxEntryParser.parseEntry(entryLine)
        } catch (e: NumberFormatException) {
            null
        } catch (e: IndexOutOfBoundsException) {
            null
        }
        return entry?.size?.takeIf { it >= 0 } ?: -1L
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
