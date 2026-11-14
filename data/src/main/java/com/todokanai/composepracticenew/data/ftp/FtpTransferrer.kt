package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.data.progress.PreparationProgressReporter
import com.todokanai.composepracticenew.data.progress.TransferTotal
import com.todokanai.composepracticenew.datasource.LocalEntryKind
import com.todokanai.composepracticenew.datasource.LocalTreeEntry
import com.todokanai.composepracticenew.datasource.UNSUPPORTED_LINK_REASON
import com.todokanai.composepracticenew.datasource.WriteSlot
import com.todokanai.composepracticenew.datasource.entryKind
import com.todokanai.composepracticenew.datasource.localIo
import com.todokanai.composepracticenew.datasource.localTreeTopDown
import com.todokanai.composepracticenew.datasource.resolveExisting
import com.todokanai.composepracticenew.datasource.slashRelativePathOf
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.OperationNoticeKind
import com.todokanai.composepracticenew.model.map
import com.todokanai.composepracticenew.model.ProgressState
import com.todokanai.composepracticenew.model.onSuccess
import com.todokanai.composepracticenew.model.valueOr
import com.todokanai.composepracticenew.operation.forEachCollectingFailures
import com.todokanai.composepracticenew.repository.OperationNoticeReporter
import com.todokanai.composepracticenew.repository.WriteConflictResolver
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
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
     * [sources]를 차례로 로컬에 스트리밍 다운로드한다. 받기 전에 모든 항목의 크기(디렉터리는 원격 트리)를 먼저 구해, 진행률을 묶음 전체로 보고한다.
     * 일부 항목이 실패해도 끝까지 진행한 뒤 실패를 모아 알리며, 연결이 끊기면 남은 항목을 시도하지 않는다.
     * 받다 실패한 로컬 파일은 남겨 두고, 취소된 경우에만 지운다.
     * 로컬 파일·폴더 자리는 세션 잠금 밖에서 "없을 때만 만들기"로 먼저 확보하고, 이미 있으면 [resolveConflict]에 묻는다 — 답을 기다리는 동안 세션을 붙잡지 않도록.
     * @param sources 받을 원격 항목과 저장할 로컬 자리
     * @param resolveConflict 같은 이름의 로컬 항목을 만났을 때 처리 방법을 묻는 통로
     * @param onProgress 진행 상태를 수신하는 콜백
     * @return 전송 결과
     */
    suspend fun download(
        sources: List<DownloadSource>,
        resolveConflict: WriteConflictResolver,
        onProgress: (ProgressState) -> Unit = {}
    ): IoResult<Unit> =
        withContext(Dispatchers.IO) {
            val preparation = PreparationProgressReporter(onProgress)
            val plans = sources.map { source -> source to planOf(source, preparation).also { if (it is IoResult.ConnectionLost) return@withContext it } }
            val sizes = plans.flatMap { (_, plan) -> (plan as? IoResult.Success)?.value?.fileSizes.orEmpty() }
            val progress = ByteProgressTracker(transferTotalOf(sizes), sizes.size, onProgress)
            forEachCollectingFailures(plans, "다운로드 실패", nameOf = { (source, _) -> File(source.localPath).name }) { (_, plan) ->
                when (val planned = plan.valueOr { return@forEachCollectingFailures it }) {
                    is DownloadPlan.SingleFile -> downloadFile(planned, resolveConflict, progress)
                    is DownloadPlan.Tree -> downloadDirectory(planned, resolveConflict, progress)
                }
            }.onSuccess { progress.reportComplete() }
        }

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
            forEachCollectingFailures(plans, "업로드 실패", nameOf = { it.localFile.name }) { plan ->
                when (plan) {
                    is UploadPlan.Link -> IoResult.ItemFailed(UNSUPPORTED_LINK_REASON)
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

    /**
     * [source]를 받기 전에 크기를 구한다. 파일은 MLST로, 디렉터리는 원격 트리를 재귀 수집해 구한다.
     * 찾은 항목을 [preparation]에 알린다 — 수집이 오래 걸려도 진행이 보이고 취소가 듣도록(#284).
     * @param source 받을 원격 항목
     * @param preparation 이 다운로드의 준비 단계 진행 보고기
     * @return 받을 계획, 또는 크기·트리를 구하지 못한 실패
     */
    private suspend fun planOf(source: DownloadSource, preparation: PreparationProgressReporter): IoResult<DownloadPlan> =
        if (source.isDirectory) {
            cmd.collectRemoteTree(source.remotePath, preparation).map { DownloadPlan.Tree(source.remotePath, File(source.localPath), it) }
        } else {
            preparation.onEntryFound(File(source.localPath).name)
            val ftpPath = extractFtpPath(source.remotePath)
            session.withConnectedClient { client -> IoResult.Success(DownloadPlan.SingleFile(ftpPath, File(source.localPath), remoteFileSize(client, ftpPath))) }
        }

    /**
     * 원격 파일 하나를 [plan]의 로컬 파일로 다운로드한다. 진행률은 묶음 전체의 [progress]에 보고한다.
     */
    private suspend fun downloadFile(plan: DownloadPlan.SingleFile, resolveConflict: WriteConflictResolver, progress: ByteProgressTracker): IoResult<Unit> {
        val localFile = plan.localFile
        localFile.parentFile?.mkdirs()
        // 건너뛰면 받을 것이 없으므로 완료다. 자리에 링크가 있으면 받지 않고 실패한다
        val claimed = when (val slot = claimLocalFile(localFile, resolveConflict).valueOr { return it }) {
            is WriteSlot.Ready -> slot.value
            WriteSlot.Skipped -> return IoResult.Done.also { progress.passOver(localFile.name, plan.size) }
            WriteSlot.Link -> return IoResult.ItemFailed(UNSUPPORTED_LINK_REASON).also { progress.passOver(localFile.name, plan.size) }
        }
        progress.startFile()
        return receivingInto(claimed) { onOpened ->
            session.withConnectedClient { client ->
                downloadOne(
                    client, plan.ftpPath, localFile, onOpened,
                    onChunk = { bytesRead -> progress.addBytes(bytesRead.toLong(), localFile.name, plan.size) },
                    onAcked = { progress.onFileCompleted(localFile.name, plan.size) }
                )
            }
        }
    }

    /**
     * 원격 디렉터리 트리를 [plan]의 로컬 폴더 아래로 다운로드한다. 실패한 파일은 모아서 마지막에 알린다. 진행률은 묶음 전체의 [progress]에 보고한다.
     * 알려진 문제(방치하기로 결정): 서버가 목록에서 숨긴 항목은 트리에 없어 받지 않으며, 누락을 알리지 않고 완료로 보고한다([FtpCommandExecutor.collectRemoteTree] 참고).
     * 해석하지 못한 목록 줄은 무엇인지 몰라 받지 못한 항목으로 알린다(#255).
     */
    private suspend fun downloadDirectory(plan: DownloadPlan.Tree, resolveConflict: WriteConflictResolver, progress: ByteProgressTracker): IoResult<Unit> {
        val remotePath = plan.remotePath
        val localRoot = plan.localRoot
        localRoot.parentFile?.mkdirs()
        // 받을 폴더 자리에 같은 이름이 있어 건너뛰면 받을 것이 없으므로 완료다. 링크가 있으면 받지 않고 실패한다
        when (val slot = claimLocalDirectory(localRoot, resolveConflict).valueOr { return it }) {
            is WriteSlot.Ready -> Unit
            WriteSlot.Skipped, WriteSlot.Link -> {
                plan.fileEntries.forEach { progress.passOver(it.name, it.size) }
                return if (slot == WriteSlot.Link) IoResult.ItemFailed(UNSUPPORTED_LINK_REASON) else IoResult.Done
            }
        }
        // 건너뛰기로 했거나 링크가 있는 폴더. 그 아래 항목은 묻지 않고 건너뛴다 — 링크 아래에 만들면 링크가 가리키는 곳에 쓰므로
        val skippedDirs = mutableListOf<File>()
        return forEachCollectingFailures(plan.items, "다운로드 실패", nameOf = { it.failureName(remotePath) }) { item ->
            val localTarget = File(localRoot, relativeRemotePath(item.locationPath, remotePath))
            val entry = when (item) {
                is ListedItem.Entry -> item.entry
                // 건너뛰기로 한 폴더 안이면 받을 것이 아니었으므로 완료다
                is ListedItem.Unreadable -> return@forEachCollectingFailures if (skippedDirs.any { localTarget.startsWith(it) }) IoResult.Done
                    else IoResult.ItemFailed(FtpListingFormat.unreadableLineReason(item.line))
            }
            when {
                skippedDirs.any { localTarget.startsWith(it) } -> IoResult.Done.also { if (!entry.isDirectory) progress.passOver(entry.name, entry.size) }
                entry.isDirectory -> when (claimLocalDirectory(localTarget, resolveConflict).valueOr { return@forEachCollectingFailures it }) {
                    is WriteSlot.Ready -> IoResult.Done
                    WriteSlot.Skipped -> IoResult.Done.also { skippedDirs += localTarget }
                    WriteSlot.Link -> IoResult.ItemFailed(UNSUPPORTED_LINK_REASON).also { skippedDirs += localTarget }
                }
                else -> {
                    localTarget.parentFile?.mkdirs()
                    val claimed = when (val slot = claimLocalFile(localTarget, resolveConflict).valueOr { return@forEachCollectingFailures it }) {
                        is WriteSlot.Ready -> slot.value
                        WriteSlot.Skipped -> return@forEachCollectingFailures IoResult.Done.also { progress.passOver(entry.name, entry.size) }
                        WriteSlot.Link -> return@forEachCollectingFailures IoResult.ItemFailed(UNSUPPORTED_LINK_REASON).also { progress.passOver(entry.name, entry.size) }
                    }
                    progress.startFile()
                    val entryFtpPath = extractFtpPath(entry.path)
                    receivingInto(claimed) { onOpened ->
                        session.withConnectedClient { client ->
                            downloadOne(
                                client, entryFtpPath, localTarget, onOpened,
                                onChunk = { bytesRead -> progress.addBytes(bytesRead.toLong(), entry.name, entry.size) },
                                onAcked = { progress.onFileCompleted(entry.name, entry.size) }
                            )
                        }
                    }
                }
            }
        }
    }

    /** 받지 않고 건너뛴 파일 [name](크기 [size], 모르면 null)을 처리한 것으로 진행률에 넘긴다. */
    private fun ByteProgressTracker.passOver(name: String, size: Long?) {
        skipFiles(1)
        size?.let { addBytes(it, name, fileBytes = null) }
    }

    /**
     * 받을 로컬 폴더 [dir] 자리를 확보한다. 같은 이름의 항목이 있으면 [resolveConflict]에 묻는다(폴더는 합치기, 파일이면 건너뛰기만). 링크면 묻지 않는다.
     * @return 그 폴더에 받을 수 있으면 [WriteSlot.Ready], 건너뛰기로 했으면 [WriteSlot.Skipped], 링크가 있으면 [WriteSlot.Link], 또는 폴더를 만들지 못한 실패
     */
    private suspend fun claimLocalDirectory(dir: File, resolveConflict: WriteConflictResolver): IoResult<WriteSlot<Unit>> {
        val created = localIo(describe = { it.message ?: TRANSFER_FAILED_MESSAGE }) { IoResult.Success(createdNew { Files.createDirectory(dir.toPath()) }) }
            .valueOr { return it }
        if (created) return IoResult.Success(WriteSlot.Ready(Unit))
        return IoResult.Success(resolveExisting(dir, sourceIsDirectory = true) { resolveConflict.resolve(it) })
    }

    /**
     * 받을 로컬 파일 [file] 자리를 확보한다. 없으면 빈 파일을 만들어 이름을 잡아 두고, 있으면 [resolveConflict]에 묻는다. 링크면 묻지 않는다.
     * 세션 잠금 밖에서 부른다 — 답을 기다리는 동안 다른 원격 작업이 세션을 쓸 수 있도록.
     * 덮어쓰기로 한 기존 파일은 여기서 비우지 않는다 — 서버가 전송을 받아들인 뒤에 연다(거부되면 기존 파일을 비우지 않도록).
     * @return 받을 자리를 담은 [WriteSlot.Ready], 건너뛰기로 했으면 [WriteSlot.Skipped], 링크가 있으면 [WriteSlot.Link], 또는 자리를 만들지 못한 실패
     */
    private suspend fun claimLocalFile(file: File, resolveConflict: WriteConflictResolver): IoResult<WriteSlot<ClaimedFile>> {
        val created = localIo(describe = { it.message ?: TRANSFER_FAILED_MESSAGE }) { IoResult.Success(createdNew { Files.createFile(file.toPath()) }) }
            .valueOr { return it }
        if (created) return IoResult.Success(WriteSlot.Ready(ClaimedFile(file, createdHere = true)))
        return IoResult.Success(
            when (resolveExisting(file, sourceIsDirectory = false) { resolveConflict.resolve(it) }) {
                is WriteSlot.Ready -> WriteSlot.Ready(ClaimedFile(file, createdHere = false))
                WriteSlot.Skipped -> WriteSlot.Skipped
                WriteSlot.Link -> WriteSlot.Link
            }
        )
    }

    /**
     * "없을 때만 만들기"인 [create]를 실행한다.
     * @return 새로 만들었으면 true. 같은 이름의 항목이 이미 있으면 false
     * @throws java.io.IOException 그 밖의 이유로 만들지 못했을 때
     */
    private inline fun createdNew(create: () -> Unit): Boolean = try {
        create()
        true
    } catch (e: FileAlreadyExistsException) {
        false
    }

    /**
     * [claimed] 자리로 받는 [receive]를 실행한다. 이 다운로드가 만든 빈 파일은 받기 시작하지 못하고 끝나면(거부·연결 끊김·취소) 지운다 —
     * 받지 않은 빈 파일이 받은 파일처럼 남지 않도록.
     * @param receive 받는 동작. 로컬 파일을 열 때 받은 함수를 부른다
     * @return [receive]의 결과
     */
    private suspend fun receivingInto(claimed: ClaimedFile, receive: suspend (onOpened: () -> Unit) -> IoResult<Unit>): IoResult<Unit> {
        var opened = false
        try {
            return receive { opened = true }
        } finally {
            if (claimed.createdHere && !opened) runCatching { claimed.file.delete() }
        }
    }

    /**
     * 다운로드가 받기로 확보한 로컬 파일 자리.
     * @param file 받을 로컬 파일
     * @param createdHere 빈 파일을 이 다운로드가 만들었으면 true. 덮어쓰기로 한 기존 파일이면 false
     */
    private class ClaimedFile(val file: File, val createdHere: Boolean)

    /** 받기 전에 크기를 구해 둔 다운로드 항목 하나. */
    private sealed interface DownloadPlan {
        /** 받을 파일마다의 크기(모르면 null). 묶음 전체의 진행률 합계에 쓴다. */
        val fileSizes: List<Long?>

        /**
         * 원격 파일 하나.
         * @param ftpPath 받을 원격 파일의 FTP 경로
         * @param localFile 저장할 로컬 파일
         * @param size 파일 크기. 모르면 null
         */
        class SingleFile(val ftpPath: String, val localFile: File, val size: Long?) : DownloadPlan {
            override val fileSizes: List<Long?> get() = listOf(size)
        }

        /**
         * 재귀 수집한 원격 디렉터리 트리.
         * @param remotePath ftp://server/path 형식의 원격 디렉터리 경로
         * @param localRoot 트리를 받을 로컬 폴더
         * @param items 트리 아래의 모든 항목(폴더·해석하지 못한 목록 줄 포함)
         */
        class Tree(val remotePath: String, val localRoot: File, val items: List<ListedItem>) : DownloadPlan {
            override val fileSizes: List<Long?> get() = fileEntries.map { it.size }

            /** 트리 아래의 파일. */
            val fileEntries: List<FileEntry> get() = items.mapNotNull { (it as? ListedItem.Entry)?.entry?.takeUnless { entry -> entry.isDirectory } }
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
         * @param entries 디렉터리 자신부터 위에서 아래로 수집한 모든 항목(폴더·링크 포함)
         */
        class Tree(override val localFile: File, val ftpPath: String, val entries: List<LocalTreeEntry>) : UploadPlan {
            override val files: List<File> get() = entries.filter { it.kind == LocalEntryKind.FILE && it.file.isFile }.map { it.file }
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
     */
    private suspend fun uploadDirectory(plan: UploadPlan.Tree, progress: ByteProgressTracker): IoResult<Unit> {
        val localDir = plan.localFile
        val ftpPath = plan.ftpPath
        // 디렉터리 자신의 실패는 빈 경로로 둔다 — 바깥 묶음이 항목 이름 아래로 옮긴다
        return forEachCollectingFailures(plan.entries, "업로드 실패", nameOf = { localDir.slashRelativePathOf(it.file) }) { treeEntry ->
            val entry = treeEntry.file
            val relativePath = localDir.slashRelativePathOf(entry)
            val entryFtpPath = if (relativePath.isEmpty()) ftpPath else "$ftpPath/$relativePath"
            when (treeEntry.kind) {
                LocalEntryKind.LINK -> IoResult.ItemFailed(UNSUPPORTED_LINK_REASON)
                LocalEntryKind.DIRECTORY -> cmd.ensureDirectory(entryFtpPath)
                LocalEntryKind.FILE -> uploadFile(entry, entryFtpPath, progress)
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
     * @param localFile 저장할 로컬 파일([claimLocalFile]로 확보한 자리) — 취소 시 삭제된다
     * @param onOpened 서버가 전송을 받아들여 로컬 파일을 열 때 호출되는 콜백
     * @param onChunk 버퍼마다 읽은 바이트 수를 받는 콜백
     * @param onAcked 서버가 전송 완료를 확인한 뒤 호출되는 콜백
     * @return 전송 결과
     */
    private suspend fun downloadOne(
        client: FtpCommandClient,
        ftpPath: String,
        localFile: File,
        onOpened: () -> Unit,
        onChunk: (Int) -> Unit,
        onAcked: suspend () -> Unit
    ): IoResult<Unit> {
        val outcome = dataCommands.run(
            client,
            open = { client.retrieveFileStream(ftpPath) },
            onCancel = { runCatching { localFile.delete() } }
        ) { stream ->
            onOpened()
            localFile.outputStream().use { output -> pump(stream, output, onChunk) }
        }
        return transferResult(client, outcome, onAcked)
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
     * @param client 응답을 받은 FTP 클라이언트
     * @param outcome 전송 명령의 결과
     * @param onAcked 서버가 전송 완료를 확인한 뒤 호출되는 콜백
     * @return 전송 결과
     */
    private suspend fun transferResult(
        client: FtpCommandClient,
        outcome: DataCommandOutcome,
        onAcked: suspend () -> Unit
    ): IoResult<Unit> = when (outcome) {
        is DataCommandOutcome.Refused -> FtpReplyRules.dataCommandRefusal(outcome.command, client.getReplyString())
        is DataCommandOutcome.DataFailed -> IoResult.ItemFailed(outcome.cause.message ?: TRANSFER_FAILED_MESSAGE)
        is DataCommandOutcome.NotConnected -> IoResult.ItemFailed(dataConnectionFailure(outcome.cause))
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
     * 받는 바이트가 합계를 넘어 진행률이 거짓이 되므로, 아는 크기의 합은 따로 둔다. 0바이트 파일도 크기를 아는 파일이다.
     * @param fileSizes 받을 파일마다의 크기. 모르면 null
     * @return 모두 알면 크기 합, 아니면 아는 크기의 합과 모르는 파일 수
     */
    private fun transferTotalOf(fileSizes: List<Long?>): TransferTotal {
        val knownSizes = fileSizes.filterNotNull()
        return TransferTotal.of(knownSizes.sum(), fileSizes.size - knownSizes.size)
    }

    companion object {
        private const val BUFFER_SIZE = 128 * 1024
        /** 원인을 알 수 없는 전송 실패의 메시지. */
        private const val TRANSFER_FAILED_MESSAGE = "전송 실패"
    }
}

/**
 * [FtpTransferrer.download]로 받을 원격 항목 하나와 저장할 로컬 자리.
 * @param remotePath ftp://server/path 형식의 원격 절대 경로
 * @param localPath 저장할 로컬 파일 또는 디렉터리의 절대 경로
 * @param isDirectory remotePath가 디렉터리인 경우 true
 */
class DownloadSource(val remotePath: String, val localPath: String, val isDirectory: Boolean)

/**
 * [FtpTransferrer.upload]로 올릴 로컬 항목 하나와 저장할 원격 자리.
 * @param localPath 올릴 로컬 파일 또는 디렉터리의 절대 경로
 * @param remotePath 저장될 원격 파일 또는 디렉터리의 절대 경로 (ftp://server/path 형식)
 */
class UploadSource(val localPath: String, val remotePath: String)
