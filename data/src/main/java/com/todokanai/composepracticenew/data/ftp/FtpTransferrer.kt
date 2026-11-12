package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.data.progress.TransferTotal
import com.todokanai.composepracticenew.datasource.localIo
import com.todokanai.composepracticenew.datasource.slashRelativePathOf
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.OperationNoticeKind
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.onSuccess
import com.todokanai.composepracticenew.model.valueOr
import com.todokanai.composepracticenew.operation.forEachCollectingFailures
import com.todokanai.composepracticenew.repository.OperationNoticeReporter
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FTP 파일 다운로드와 업로드를 스트리밍으로 수행하며 진행률을 보고한다.
 * 전송의 한쪽 끝이 로컬 파일이므로 로컬 스트림 개폐는 이 클래스가 담당하고,
 * 데이터 연결의 개폐·전송 완료 확인은 목록 조회와 같은 [DataCommandRunner]로, 진행률은 파일·디렉터리 모두 로컬 작업과 공유하는 [ByteProgressTracker]로 계산한다(단일 파일은 파일 하나짜리 작업).
 * 실패는 결과로 알리며, 연결이 없거나 전송 중 끊기면 [IoResult.ConnectionLost]를 돌려준다.
 * 세션 경계 안의 로컬 파일 I/O는 [DataCommandRunner]의 exchange에서만 한다 — 경계는 던져진 IOException을 세션 종료로 보기 때문이다.
 * 다운로드의 로컬 파일은 서버가 전송을 받아들인 뒤에(거부되면 기존 파일을 비우지 않도록), 업로드의 로컬 파일은 STOR 전에([uploadingFrom]) 연다.
 * @param session 전송에 사용할 FTPClient와 잠금을 소유한 FTP 세션
 * @param cmd 디렉터리 다운로드 시 원격 트리를 수집하고, 폴더 업로드 시 원격 폴더를 만드는 FtpCommandExecutor
 * @param dataCommands 전송 명령(RETR·STOR)을 목록 조회와 같은 규칙으로 실행하는 실행기
 * @param notices 취소한 업로드가 서버에 남겼을 수 있는 파일을 알리는 OperationNoticeReporter — 취소는 결과로 돌려줄 수 없으므로 직접 알린다
 */
@Singleton
class FtpTransferrer @Inject internal constructor(
    private val session: FtpCommandSession,
    private val cmd: FtpCommandExecutor,
    private val dataCommands: DataCommandRunner,
    private val notices: OperationNoticeReporter
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
     * 취소되면 올리던 파일을 서버에서 지우기를 시도하고, 이미 다 올린 파일은 남긴다.
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
     * 단일 원격 파일을 [localFile]로 다운로드한다. 진행률은 파일 하나짜리 작업으로 보고하며, 크기를 모르면 퍼센트 없이 받은 바이트를 보고한다.
     */
    private suspend fun downloadFile(
        remotePath: String,
        localFile: File,
        onProgress: (ProgressState) -> Unit
    ): IoResult<Unit> {
        val ftpPath = extractFtpPath(remotePath)
        localFile.parentFile?.mkdirs()
        return session.withConnectedClient { client ->
            val fileBytes = remoteFileSize(client, ftpPath)
            val progress = ByteProgressTracker(singleFileTotal(fileBytes), 1, onProgress)
            progress.startFile()
            downloadOne(
                client, ftpPath, localFile,
                onChunk = { bytesRead -> progress.addBytes(bytesRead.toLong(), localFile.name, fileBytes) },
                onAcked = { progress.onFileCompleted(localFile.name, fileBytes) }
            ).onSuccess { progress.reportComplete() }
        }
    }

    /**
     * 원격 디렉터리 트리를 [localPath] 아래로 다운로드한다. 실패한 파일은 모아서 마지막에 알린다.
     * 알려진 문제(방치하기로 결정): 서버가 목록에서 숨긴 항목은 트리에 없어 받지 않으며, 누락을 알리지 않고 완료로 보고한다([FtpCommandExecutor.collectRemoteTree] 참고).
     */
    private suspend fun downloadDirectory(
        remotePath: String,
        localPath: String,
        onProgress: (ProgressState) -> Unit
    ): IoResult<Unit> {
        val allEntries = cmd.collectRemoteTree(remotePath).valueOr { return it }
        val fileEntries = allEntries.filter { !it.isDirectory }
        val progress = ByteProgressTracker(transferTotalOf(fileEntries), fileEntries.size, onProgress)
        File(localPath).mkdirs()
        return forEachCollectingFailures(allEntries, "다운로드 실패", nameOf = { relativeRemotePath(it.path, remotePath) }) { entry ->
            val localTarget = File(localPath, relativeRemotePath(entry.path, remotePath))
            if (entry.isDirectory) {
                localTarget.mkdirs()
                IoResult.Done
            } else {
                progress.startFile()
                val entryFtpPath = extractFtpPath(entry.path)
                localTarget.parentFile?.mkdirs()
                session.withConnectedClient { client ->
                    downloadOne(
                        client, entryFtpPath, localTarget,
                        onChunk = { bytesRead -> progress.addBytes(bytesRead.toLong(), entry.name, entry.size) },
                        onAcked = { progress.onFileCompleted(entry.name, entry.size) }
                    )
                }
            }
        }.onSuccess { progress.reportComplete() }
    }

    /**
     * 단일 로컬 파일을 [ftpPath]로 업로드한다. 진행률은 파일 하나짜리 작업으로 보고한다.
     */
    private suspend fun uploadFile(
        localFile: File,
        ftpPath: String,
        onProgress: (ProgressState) -> Unit
    ): IoResult<Unit> {
        val fileBytes = localFile.length()
        return uploadingFrom(localFile) { input ->
            session.withConnectedClient { client ->
                val progress = ByteProgressTracker(fileBytes, 1, onProgress)
                progress.startFile()
                uploadOne(
                    client, ftpPath, input,
                    onChunk = { bytesRead -> progress.addBytes(bytesRead.toLong(), localFile.name, fileBytes) },
                    onAcked = { progress.onFileCompleted(localFile.name, fileBytes) }
                ).onSuccess { progress.reportComplete() }
            }
        }
    }

    /**
     * 로컬 디렉터리 트리를 [ftpPath] 아래로 업로드한다. 실패한 파일은 모아서 마지막에 알린다.
     * 원격 폴더는 이미 있어도 되며, 만들 수도 없고 있지도 않으면 그 폴더의 실패로 집계된다([FtpCommandExecutor.ensureDirectory]).
     */
    private suspend fun uploadDirectory(
        localDir: File,
        ftpPath: String,
        onProgress: (ProgressState) -> Unit
    ): IoResult<Unit> {
        val allEntries = localDir.walkTopDown().toList()
        val fileEntries = allEntries.filter { it.isFile }
        val progress = ByteProgressTracker(fileEntries.sumOf { it.length() }, fileEntries.size, onProgress)
        return forEachCollectingFailures(allEntries, "업로드 실패", nameOf = { localDir.slashRelativePathOf(it).ifEmpty { it.name } }) { entry ->
            val relativePath = localDir.slashRelativePathOf(entry)
            val entryFtpPath = if (relativePath.isEmpty()) ftpPath else "$ftpPath/$relativePath"
            if (entry.isDirectory) {
                cmd.ensureDirectory(entryFtpPath)
            } else {
                progress.startFile()
                val entryBytes = entry.length()
                uploadingFrom(entry) { input ->
                    session.withConnectedClient { client ->
                        uploadOne(
                            client, entryFtpPath, input,
                            onChunk = { bytesRead -> progress.addBytes(bytesRead.toLong(), entry.name, entryBytes) },
                            onAcked = { progress.onFileCompleted(entry.name, entryBytes) }
                        )
                    }
                }
            }
        }.onSuccess { progress.reportComplete() }
    }

    /**
     * [localFile]을 열어 [upload]에 넘기고 끝나면 닫는다. 서버가 STOR를 받아들이면 같은 이름의 파일이 비워지므로,
     * 열 수 없는 파일은 STOR를 보내기 전에 이 항목의 실패로 끝내 서버 파일을 건드리지 않는다. 세션 경계 밖이므로 이 실패는 세션과 무관하다.
     * @param localFile 업로드할 로컬 파일
     * @param upload 열린 스트림으로 업로드하는 동작
     * @return [upload]의 결과, 또는 파일을 열지 못한 실패
     */
    private suspend fun uploadingFrom(localFile: File, upload: suspend (InputStream) -> IoResult<Unit>): IoResult<Unit> {
        val input = localIo(describe = { it.message ?: TRANSFER_FAILED_MESSAGE }) { IoResult.Success(localFile.inputStream()) }.valueOr { return it }
        return try {
            upload(input)
        } finally {
            // 읽기만 한 스트림이라 닫기 실패는 전송 결과와 무관하다
            runCatching { input.close() }
        }
    }

    /**
     * 세션 잠금 안에서 원격 파일 하나를 [localFile]로 받는다. 단일 파일·디렉터리 다운로드가 공유하며,
     * 진행률 보고 규칙은 호출부가 [onChunk]·[onAcked]로 결정한다. 받다 만 파일은 취소된 경우에만 지운다.
     * @param client 잠금을 확보한 FTP 클라이언트
     * @param ftpPath 받을 원격 파일의 FTP 경로
     * @param localFile 저장할 로컬 파일 — 취소 시 삭제된다
     * @param onChunk 버퍼마다 읽은 바이트 수를 받는 콜백
     * @param onAcked 서버가 전송 완료를 확인한 뒤 호출되는 콜백
     * @return 전송 결과
     */
    private suspend fun downloadOne(
        client: FtpCommandClient,
        ftpPath: String,
        localFile: File,
        onChunk: (Int) -> Unit,
        onAcked: suspend () -> Unit
    ): IoResult<Unit> {
        val outcome = dataCommands.run(
            client,
            open = { client.retrieveFileStream(ftpPath) },
            onCancel = { runCatching { localFile.delete() } }
        ) { stream -> localFile.outputStream().use { output -> pump(stream, output, onChunk) } }
        return transferResult(FtpCommand.RETR, client, outcome, onAcked)
    }

    /**
     * 세션 잠금 안에서 열어 둔 로컬 파일 하나를 원격으로 올린다. 단일 파일·디렉터리 업로드가 공유하며,
     * 진행률 보고 규칙은 호출부가 [onChunk]·[onAcked]로 결정한다.
     * @param client 잠금을 확보한 FTP 클라이언트
     * @param ftpPath 저장될 원격 파일의 FTP 경로
     * @param input 업로드할 로컬 파일의 스트림([uploadingFrom])
     * @param onChunk 버퍼마다 쓴 바이트 수를 받는 콜백
     * @param onAcked 서버가 전송 완료를 확인한 뒤 호출되는 콜백
     * @return 전송 결과
     */
    private suspend fun uploadOne(
        client: FtpCommandClient,
        ftpPath: String,
        input: InputStream,
        onChunk: (Int) -> Unit,
        onAcked: suspend () -> Unit
    ): IoResult<Unit> {
        // 알려진 문제(방치하기로 결정): STOR는 기존 파일을 덮어쓴다. 서버가 목록에서 숨긴 같은 이름의 파일은 충돌 검사에 보이지 않아 묻지 않고 덮어쓴다
        // ([FtpCommandExecutor.identifiedListing] 참고)
        val outcome = try {
            dataCommands.run(client, open = { client.storeFileStream(ftpPath) }) { stream -> pump(input, stream, onChunk) }
        } catch (e: CancellationException) {
            discardCancelledUpload(client, ftpPath)
            throw e
        }
        return transferResult(FtpCommand.STOR, client, outcome, onAcked)
    }

    /**
     * 취소로 멈춘 업로드가 서버에 남겼을 수 있는 받다 만 파일을 DELE로 지운다. 중단된 STOR의 파일을 남길지는 서버마다 다르므로(RFC 959에 정함 없음) 지우기를 시도한다.
     * 지우지 못하면 남았을 수 있다고 이유(서버의 거부 응답, 세션 종료)와 함께 알린다.
     * 취소는 결과로 돌려줄 수 없으므로 작업 메시지 알림함에 직접 넣는다.
     * @param client 잠금을 확보한 FTP 클라이언트
     * @param ftpPath 취소된 업로드의 원격 파일 FTP 경로
     */
    private suspend fun discardCancelledUpload(client: FtpCommandClient, ftpPath: String) {
        val reason = when (val cleanup = dataCommands.cleanUpAfterCancel(client) { client.deleteFile(ftpPath) }) {
            CancelCleanup.Done -> return
            is CancelCleanup.Refused -> FtpReplyRules.refusal(FtpCommand.DELE, cleanup.reply).message
            CancelCleanup.SessionEnded -> "연결이 끊겨 지우지 못했습니다"
        }
        notices.report(OperationNoticeKind.FAILURE, "업로드 취소: 서버에 받다 만 파일이 남았을 수 있습니다 ($ftpPath) — $reason")
    }

    /**
     * 전송 명령의 결과([DataCommandRunner.run])를 이 항목의 결과로 만든다. 서버가 전송 완료를 확인해야 성공이다.
     * 전송(데이터 스트림·로컬 파일) 중 실패는 제어 채널과 무관하고 완료 응답까지 읽었으므로 이 항목만의 실패다.
     * @param command 보낸 전송 명령
     * @param client 응답을 받은 FTP 클라이언트
     * @param outcome 전송 명령의 결과
     * @param onAcked 서버가 전송 완료를 확인한 뒤 호출되는 콜백
     * @return 전송 결과
     */
    private suspend fun transferResult(
        command: FtpCommand,
        client: FtpCommandClient,
        outcome: DataCommandOutcome,
        onAcked: suspend () -> Unit
    ): IoResult<Unit> = when (outcome) {
        DataCommandOutcome.Refused -> FtpReplyRules.refusal(command, client.getReplyString())
        is DataCommandOutcome.DataFailed -> IoResult.ItemFailed(outcome.cause.message ?: TRANSFER_FAILED_MESSAGE)
        is DataCommandOutcome.Finished ->
            if (!outcome.acknowledged) FtpReplyRules.unacknowledgedTransfer(client.getReplyString())
            else {
                onAcked()
                IoResult.Done
            }
    }

    /**
     * 진행률 계산용으로 원격 파일 크기를 조회한다. 서버가 MLST를 거부하거나 응답의 항목 줄을 해석하지 못하면 크기를 모르는 것(null)으로 본다.
     * 응답을 끝까지 읽은 뒤 항목 줄만 해석하므로, 응답을 읽다 실패한 예외(연결 끊김·타임아웃·응답 형식 오류)는 삼키지 않고 전파해
     * [FtpSession.withConnectedClient]가 세션을 종료하게 한다. FTPClient.mlistFile은 응답 읽기 실패와 항목 해석 실패를 같은 예외로 섞어 던지므로 쓰지 않는다.
     * @param client 잠금을 확보한 FTP 클라이언트
     * @param ftpPath 크기를 조회할 원격 파일의 FTP 경로
     * @return 파일 크기(bytes, 0 이상), 알 수 없으면 null
     */
    private fun remoteFileSize(client: FtpCommandClient, ftpPath: String): Long? {
        val replyCode = client.sendMlst(ftpPath)
        return FtpReplyRules.mlstEntrySize(replyCode, client.getReplyStrings()).takeIf { it >= 0 }
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
     * 받을 파일들의 전체 크기를 구한다. 크기를 모르는 파일이 하나라도 있으면 전체 크기를 모른다 — 아는 크기만 더한 합계로 퍼센트를 내면
     * 받는 바이트가 합계를 넘어 진행률이 거짓이 되므로, 아는 크기의 합은 따로 둔다.
     * @param fileEntries 받을 파일 항목 목록
     * @return 모두 알면 크기 합, 아니면 아는 크기의 합과 모르는 파일 수
     */
    private fun transferTotalOf(fileEntries: List<FileEntry>): TransferTotal {
        val knownSizes = fileEntries.mapNotNull { it.size }
        return TransferTotal.of(knownSizes.sum(), fileEntries.size - knownSizes.size)
    }

    /**
     * 단일 파일 전송의 전체 크기를 구한다. 0바이트 파일도 크기를 아는 파일이다.
     * @param fileBytes 파일 크기. 모르면 null
     * @return 크기를 알면 그 크기, 모르면 크기를 모르는 파일 하나
     */
    private fun singleFileTotal(fileBytes: Long?): TransferTotal =
        TransferTotal.of(knownBytes = fileBytes ?: 0, unknownFileCount = if (fileBytes == null) 1 else 0)

    companion object {
        private const val BUFFER_SIZE = 128 * 1024
        /** 원인을 알 수 없는 전송 실패의 메시지. */
        private const val TRANSFER_FAILED_MESSAGE = "전송 실패"
    }
}
