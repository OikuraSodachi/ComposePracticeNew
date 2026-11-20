package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.datasource.LocalEntryKind
import com.todokanai.composepracticenew.datasource.LocalTreeEntry
import com.todokanai.composepracticenew.datasource.LocalTreeEntryKind
import com.todokanai.composepracticenew.datasource.entryKind
import com.todokanai.composepracticenew.datasource.localIo
import com.todokanai.composepracticenew.datasource.localTreeTopDown
import com.todokanai.composepracticenew.datasource.slashRelativePathOf
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.LeftoverCause
import com.todokanai.composepracticenew.model.NoticeMessage
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.onSuccess
import com.todokanai.composepracticenew.model.valueOr
import com.todokanai.composepracticenew.operation.forEachCollectingFailures
import com.todokanai.composepracticenew.repository.OperationNoticeReporter
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 로컬 파일·디렉터리를 FTP 원격으로 스트리밍 업로드하며 진행률을 보고한다.
 * 전송의 한쪽 끝이 로컬 파일이므로 로컬 스트림 개폐는 이 클래스가 담당하고,
 * 데이터 연결의 개폐·전송 완료 확인은 목록 조회와 같은 [DataCommandRunner]로, 진행률은 로컬 작업과 공유하는 [ByteProgressTracker]로 계산한다(단일 파일은 파일 하나짜리 작업).
 * 실패는 결과로 알리며, 연결이 없거나 전송 중 끊기면 [IoResult.ConnectionLost]를 돌려준다.
 * 세션 경계 안의 로컬 파일 I/O는 [DataCommandRunner]의 exchange에서만 한다 — 경계는 던져진 IOException을 세션 종료로 보기 때문이다.
 * 로컬 파일은 STOR 전에([uploadingFrom]) 연다.
 * 다운로드([FtpDownloader])와 나뉜 이유: 쓰는 부품(폴더 생성·알림함 vs 트리 수집)과 바뀌는 이유(취소한 STOR 정리 vs 로컬 충돌 자리 확보)가 다르다(#443).
 * @param session 전송에 사용할 FTPClient와 잠금을 소유한 FTP 세션
 * @param cmd 폴더 업로드 시 원격 폴더를 만드는 FtpCommandExecutor
 * @param dataCommands 전송 명령(STOR)을 목록 조회와 같은 규칙으로 실행하는 실행기
 * @param notices 취소한 업로드가 서버에 남겼을 수 있는 파일을 알리는 OperationNoticeReporter — 취소는 결과로 돌려줄 수 없으므로 직접 알린다
 */
class FtpUploader internal constructor(
    private val session: FtpCommandSession,
    private val cmd: FtpCommandExecutor,
    private val dataCommands: DataCommandRunner,
    private val notices: OperationNoticeReporter
) {

    /**
     * [sources]를 차례로 원격에 스트리밍 업로드한다. 올리기 전에 모든 항목의 로컬 트리를 링크를 따라가지 않고 수집해, 진행률을 묶음 전체로 보고한다.
     * 일부 항목이 실패해도 끝까지 진행한 뒤 실패를 모아 알리며, 연결이 끊기면 남은 항목을 시도하지 않는다.
     * 링크(고른 항목 자신 포함)는 올리지 않고 실패로 알린다.
     * 취소되면 올리던 파일을 서버에서 지우기를 시도하고, 이미 다 올린 파일은 남긴다.
     * @param sources 올릴 로컬 항목과 저장할 원격 자리
     * @param onProgress 진행 상태를 수신하는 콜백
     * @return 전송 결과
     */
    suspend fun upload(sources: List<UploadSource>, onProgress: (ProgressState) -> Unit = {}): IoResult<Unit> =
        withContext(Dispatchers.IO) {
            val plans = sources.map { uploadPlanOf(it) }
            val files = plans.flatMap { it.files }
            val progress = ByteProgressTracker(files.sumOf { it.length() }, files.size, onProgress)
            forEachCollectingFailures(plans, nameOf = { it.localFile.name }) { plan ->
                when (plan) {
                    is UploadPlan.Link -> IoResult.ItemFailed(FailureReason.UnsupportedLink())
                    is UploadPlan.SingleFile -> uploadFile(plan.localFile, plan.ftpPath, progress)
                    is UploadPlan.Tree -> uploadDirectory(plan, progress)
                }
            }.onSuccess { progress.reportComplete() }
        }

    /**
     * [source]를 올리기 전에 종류를 정하고, 디렉터리면 로컬 트리를 링크를 따라가지 않고 수집한다.
     * @param source 올릴 로컬 항목
     * @return 올릴 계획
     */
    private fun uploadPlanOf(source: UploadSource): UploadPlan {
        val localFile = File(source.localPath)
        val ftpPath = extractFtpPath(source.remotePath)
        return when (localFile.entryKind) {
            LocalEntryKind.LINK -> UploadPlan.Link(localFile)
            LocalEntryKind.DIRECTORY -> UploadPlan.Tree(localFile, ftpPath, localFile.localTreeTopDown().toList())
            LocalEntryKind.FILE -> UploadPlan.SingleFile(localFile, ftpPath)
        }
    }

    /** 올리기 전에 종류를 정해 둔 업로드 항목 하나. */
    private sealed interface UploadPlan {
        /** 고른 로컬 항목. */
        val localFile: File

        /** 올릴 로컬 파일. 묶음 전체의 진행률 합계에 쓴다. */
        val files: List<File>

        /**
         * 올리지 않는 링크.
         * @param localFile 고른 링크
         */
        class Link(override val localFile: File) : UploadPlan {
            override val files: List<File> get() = emptyList()
        }

        /**
         * 로컬 파일 하나.
         * @param localFile 올릴 로컬 파일
         * @param ftpPath 저장될 원격 파일의 FTP 경로
         */
        class SingleFile(override val localFile: File, val ftpPath: String) : UploadPlan {
            override val files: List<File> get() = listOf(localFile)
        }

        /**
         * 링크를 따라가지 않고 수집한 로컬 디렉터리 트리.
         * @param localFile 올릴 로컬 디렉터리
         * @param ftpPath 저장될 원격 디렉터리의 FTP 경로
         * @param entries 디렉터리 자신부터 위에서 아래로 수집한 모든 항목(폴더·링크·나열하지 못한 폴더 포함)
         */
        class Tree(override val localFile: File, val ftpPath: String, val entries: List<LocalTreeEntry>) : UploadPlan {
            override val files: List<File> get() = entries.filter { it.kind == LocalTreeEntryKind.FILE && it.file.isFile }.map { it.file }
        }
    }

    /**
     * 로컬 파일 하나를 [ftpPath]로 업로드한다. 단일 파일·폴더 안쪽 파일이 공유하며, 진행률은 묶음 전체의 [progress]에 보고한다.
     * 파일 순번은 열기 전에 올린다 — 열지 못한 파일도 한 파일로 세어 다음 파일의 순번이 밀리지 않도록.
     */
    private suspend fun uploadFile(
        localFile: File,
        ftpPath: String,
        progress: ByteProgressTracker
    ): IoResult<Unit> {
        progress.startFile()
        val fileBytes = localFile.length()
        return uploadingFrom(localFile) { input ->
            session.withConnectedClient { client ->
                uploadOne(
                    client, ftpPath, input,
                    onChunk = { bytesRead -> progress.addBytes(bytesRead.toLong(), localFile.name, fileBytes) },
                    onAcked = { progress.onFileCompleted(localFile.name, fileBytes) }
                )
            }
        }
    }

    /**
     * 로컬 디렉터리 트리를 [plan]의 원격 폴더 아래로 업로드한다. 실패한 파일은 모아서 마지막에 알린다. 진행률은 묶음 전체의 [progress]에 보고한다.
     * 원격 폴더는 이미 있어도 되며, 만들 수도 없고 있지도 않으면 그 폴더의 실패로 집계된다([FtpCommandExecutor.ensureDirectory]).
     * 안을 나열하지 못한 로컬 폴더는 원격에 빈 폴더로 만들지 않고 실패로 집계한다(#422).
     */
    private suspend fun uploadDirectory(plan: UploadPlan.Tree, progress: ByteProgressTracker): IoResult<Unit> {
        val localDir = plan.localFile
        val ftpPath = plan.ftpPath
        // 디렉터리 자신의 실패는 빈 경로로 둔다 — 바깥 묶음이 항목 이름 아래로 옮긴다
        return forEachCollectingFailures(plan.entries, nameOf = { localDir.slashRelativePathOf(it.file) }) { treeEntry ->
            val entry = treeEntry.file
            val relativePath = localDir.slashRelativePathOf(entry)
            val entryFtpPath = if (relativePath.isEmpty()) ftpPath else "$ftpPath/$relativePath"
            when (treeEntry.kind) {
                LocalTreeEntryKind.LINK -> IoResult.ItemFailed(FailureReason.UnsupportedLink())
                LocalTreeEntryKind.UNLISTED_DIRECTORY -> IoResult.ItemFailed(FailureReason.FolderUnreadable())
                LocalTreeEntryKind.DIRECTORY -> cmd.ensureDirectory(entryFtpPath)
                LocalTreeEntryKind.FILE -> uploadFile(entry, entryFtpPath, progress)
            }
        }
    }

    /**
     * [localFile]을 열어 [upload]에 넘기고 끝나면 닫는다. 서버가 STOR를 받아들이면 같은 이름의 파일이 비워지므로,
     * 열 수 없는 파일은 STOR를 보내기 전에 이 항목의 실패로 끝내 서버 파일을 건드리지 않는다. 세션 경계 밖이므로 이 실패는 세션과 무관하다.
     * @param localFile 업로드할 로컬 파일
     * @param upload 열린 스트림으로 업로드하는 동작
     * @return [upload]의 결과, 또는 파일을 열지 못한 실패
     */
    private suspend fun uploadingFrom(localFile: File, upload: suspend (InputStream) -> IoResult<Unit>): IoResult<Unit> {
        val input = localIo(describe = { FailureReason.TransferFailed(it.message) }) { IoResult.Success(localFile.inputStream()) }.valueOr { return it }
        return try {
            upload(input)
        } finally {
            // 읽기만 한 스트림이라 닫기 실패는 전송 결과와 무관하다
            runCatching { input.close() }
        }
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
        // ([FtpListingReader.identifiedListing] 참고)
        val outcome = try {
            dataCommands.run(client, DataStreamRequest.Store(ftpPath)) { stream -> pump(input, stream, onChunk) }
        } catch (e: CancellationException) {
            discardCancelledUpload(client, ftpPath)
            throw e
        }
        return transferResult(client, outcome, onAcked)
    }

    /**
     * 취소로 멈춘 업로드가 서버에 남겼을 수 있는 받다 만 파일을 DELE로 지운다. 중단된 STOR의 파일을 남길지는 서버마다 다르므로(RFC 959에 정함 없음) 지우기를 시도한다.
     * 지우지 못하면 남았을 수 있다고 이유(서버의 거부 응답, 세션 종료)와 함께 알린다.
     * 취소는 결과로 돌려줄 수 없으므로 작업 메시지 알림함에 직접 넣는다.
     * @param client 잠금을 확보한 FTP 클라이언트
     * @param ftpPath 취소된 업로드의 원격 파일 FTP 경로
     */
    private suspend fun discardCancelledUpload(client: FtpCommandClient, ftpPath: String) {
        val cause = when (val cleanup = dataCommands.cleanUpAfterCancel(client) { client.deleteFile(ftpPath) }) {
            CancelCleanup.Done -> return
            is CancelCleanup.Refused -> LeftoverCause.Refused(cleanup.reply.trim())
            CancelCleanup.SessionEnded -> LeftoverCause.SessionEnded
        }
        notices.report(NoticeMessage.UploadLeftover(ftpPath, cause))
    }
}

/**
 * [FtpUploader.upload]로 올릴 로컬 항목 하나와 저장할 원격 자리.
 * @param localPath 올릴 로컬 파일 또는 디렉터리의 절대 경로
 * @param remotePath 저장될 원격 파일 또는 디렉터리의 절대 경로 (ftp://server/path 형식)
 */
class UploadSource(val localPath: String, val remotePath: String)
