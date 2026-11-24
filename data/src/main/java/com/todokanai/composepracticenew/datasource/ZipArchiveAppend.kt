package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.data.progress.ByteProgressTracker
import com.todokanai.composepracticenew.data.progress.IndeterminateProgressReporter
import com.todokanai.composepracticenew.datasource.LocalFileChecks.checkDiskSpace
import com.todokanai.composepracticenew.model.ConflictChoice
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IndeterminatePhase
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressPhase
import com.todokanai.composepracticenew.model.WriteConflict
import com.todokanai.composepracticenew.model.WriteConflictKind
import com.todokanai.composepracticenew.model.valueOr
import com.todokanai.composepracticenew.repository.ArchivePasswordProvider
import com.todokanai.composepracticenew.repository.WriteConflictResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.FileHeader
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import javax.inject.Inject

/**
 * 로컬 파일·폴더를 기존 ZIP 압축 파일 안의 폴더에 넣는다. 다른 형식(RAR·ISO는 쓰기 라이브러리가 없고, 7z는 지원 범위 밖)과 분할 ZIP(zip4j와 규격이 수정을 받지 않는다)은 거부한다.
 * 원본은 건드리지 않는다: 같은 폴더의 임시 사본에 넣고, 사본을 장치에 기록한 뒤 원본 자리로 한 번에 교체한다([replaceFileAtomically]) —
 * 실패·취소하면 임시 사본만 지워 원본이 그대로 남고, 교체 도중 전원이 끊겨도 원본이나 다 쓴 사본 중 하나가 남는다.
 * 같은 이름의 항목은 쓰기 전에 모두 묻고, 덮어쓸 기존 항목은 사본에서 한 번에 지운 뒤 새 항목을 넣는다.
 * 사본을 만든 뒤 교체하기 전에 다른 앱이 원본을 바꾸면 그 변경은 교체로 사라진다(지원 범위 밖 — 파일 시스템에 잠금이 없다).
 * 기존 항목 지우기(zip4j가 사본 전체를 다시 쓴다)는 진행을 보고하지 않아, 그동안 진행 표시가 멈추고 취소는 지우기가 끝난 뒤 듣는다.
 * @param freeSpaceOf 폴더가 놓인 파티션의 여유 공간을 구하는 함수. 테스트에서 공간 부족을 재현하도록 넘긴다
 * @param syncToDisk 다 쓴 사본을 교체 전에 장치에 기록하는 함수. 테스트에서 넘긴다
 * @param leftovers 끝까지 쓰지 못한 임시 사본을 지우고, 지우지 못하면 알리는 LeftoverDiscarder
 */
class ZipArchiveAppend internal constructor(
    private val freeSpaceOf: (File) -> Long,
    private val syncToDisk: (File) -> Unit,
    private val leftovers: LeftoverDiscarder,
) {

    /** 실제 파일 시스템을 쓰는 추가 부품을 만든다. Hilt가 사용하는 생성자다. */
    @Inject constructor(leftovers: LeftoverDiscarder) : this(File::getFreeSpace, ::forceToDisk, leftovers)

    /**
     * [archivePath]가 항목을 넣을 수 있는 ZIP 이름인지 판정한다: 단일 `.zip`(대소문자 무관). 번호 분할(`.zip.001`)·PKWARE 분할의 다른 볼륨(`.z01`)은 아니다.
     * PKWARE 분할의 내용 목록 파일은 이름이 단일 ZIP과 같아 여기서 가리지 못하고 [addFiles]가 열 때 거부한다.
     * @param archivePath 압축 파일 경로
     * @return 넣을 수 있는 이름이면 true
     */
    fun canAddTo(archivePath: String): Boolean =
        (zipNameRoleOf(File(archivePath).name) as? ArchiveNameRole.Entry)?.set?.layout == VolumeLayout.SELF_LOCATING

    /**
     * [sources]를 [archivePath] 안의 [folderNames] 폴더에 넣는다. 규칙은 [com.todokanai.composepracticenew.repository.ArchiveAddRepository.addFiles]와 같다.
     * @param sources 넣을 로컬 파일·폴더 경로
     * @param archivePath 항목을 넣을 ZIP 경로
     * @param folderNames 압축 파일 루트부터 넣을 폴더까지의 폴더 이름들
     * @param resolveConflict 같은 이름의 항목을 만났을 때 처리 방법을 묻는 통로
     * @param askPassword 암호화된 압축 파일의 비밀번호를 묻는 통로
     * @param onProgress 진행 상태를 수신하는 콜백
     * @return 결과
     */
    suspend fun addFiles(
        sources: Set<String>,
        archivePath: String,
        folderNames: List<String>,
        resolveConflict: WriteConflictResolver,
        askPassword: ArchivePasswordProvider,
        onProgress: (ProgressPhase) -> Unit
    ): IoResult<Unit> = withContext(Dispatchers.IO) {
        // 압축 해제와 같은 이유로 작업의 Job을 부모로 답을 기다린다 — 작업 취소가 기다림을 끝낸다
        val job = coroutineContext.job
        val ask: (WriteConflict) -> ConflictChoice = { conflict -> runBlocking(job) { resolveConflict.resolve(conflict) } }
        localIo {
            val archive = File(archivePath)
            if (!canAddTo(archivePath)) return@localIo IoResult.ItemFailed(FailureReason.ArchiveNotAppendable(archive.name))
            val collected = collectArchiveSources(sources)
            if (containsArchive(collected.entries, archive)) return@localIo IoResult.ItemFailed(FailureReason.ArchiveAddedIntoItself(archive.name))
            if (collected.entries.isEmpty()) {
                return@localIo if (collected.skipped.isEmpty()) IoResult.ItemFailed(FailureReason.NothingToCompress) else IoResult.PartiallyFailed(collected.skipped)
            }
            val checking = IndeterminateProgressReporter(onProgress)
            val passwords = askingPasswordSession(archive.name, askPassword, job) { checking.report { IndeterminatePhase.CheckingPassword(archive.name) } }
            val (plan, password) = openOriginal(archive).use { original ->
                // 비밀번호는 쓰기 전에 기존 항목으로 확인한다 — 항목마다 다른 비밀번호는 지원 범위 밖이라, 틀린 비밀번호로 새 항목을 암호화하지 않도록
                val verified = zipPasswordCheckEntryOf(original.fileHeaders)?.let { entry ->
                    passwords.verifiedPassword { candidate -> checkZipPassword(original, entry, candidate, passwords.reportChecking) }
                }
                planOf(collected.entries, original.fileHeaders, prefixOf(folderNames), archive.name, ask) to verified
            }
            if (plan.writes.isEmpty()) {
                return@localIo if (collected.skipped.isEmpty()) IoResult.Done else IoResult.PartiallyFailed(collected.skipped)
            }
            writeAndReplace(archive, plan, password, onProgress).valueOr { return@localIo it }
            if (collected.skipped.isEmpty()) IoResult.Done else IoResult.PartiallyFailed(collected.skipped)
        }
    }

    /**
     * 넣을 항목에 [archive] 자신이 있는지 판정한다. 같은 파일인지는 파일 시스템이 정한다(경로 표기가 달라도, 상위 폴더가 링크여도 가린다).
     * @param entries 넣을 항목
     * @param archive 항목을 넣을 압축 파일
     * @return [archive] 자신이 있으면 true
     */
    private fun containsArchive(entries: List<ArchiveSource>, archive: File): Boolean {
        if (!archive.exists()) return false
        val size = archive.length()
        return entries.any { it is ArchiveSource.Regular && it.bytes == size && Files.isSameFile(it.file.toPath(), archive.toPath()) }
    }

    /**
     * 원본 ZIP을 읽기용으로 연다. zip4j는 없는 파일과 0바이트 파일을 새로 만들 빈 아카이브로 여므로 먼저 확인한다. 분할 ZIP은 거부한다.
     * @param archive 원본 ZIP
     * @return 이름을 UTF-8로 읽도록 연 ZipFile
     * @throws NoSuchFileException [archive]가 없을 때
     * @throws LocalFailureException 파일이 아니거나 0바이트일 때([FailureReason.UnreadableArchive]), 분할 ZIP일 때([FailureReason.ArchiveNotAppendable])
     */
    private fun openOriginal(archive: File): ZipFile {
        if (!archive.exists()) throw NoSuchFileException(archive.path)
        if (!archive.isFile || archive.length() == 0L) throw LocalFailureException(FailureReason.UnreadableArchive(archive.name))
        val zf = ZipFile(archive).apply { charset = Charsets.UTF_8 }
        val split = try {
            zf.isSplitArchive
        } catch (e: IOException) {
            zf.close()
            throw e
        }
        if (split) {
            zf.close()
            throw LocalFailureException(FailureReason.ArchiveNotAppendable(archive.name))
        }
        return zf
    }

    /**
     * 넣을 폴더의 항목 이름 앞부분을 만든다.
     * @param folderNames 압축 파일 루트부터의 폴더 이름들
     * @return 루트면 빈 문자열, 아니면 `a/b/`
     */
    private fun prefixOf(folderNames: List<String>): String = folderNames.joinToString("") { "$it/" }

    /**
     * 넣을 항목마다 쓸지, 덮어쓸지, 건너뛸지를 정한다. 겹치면 [ask]에 묻는다: 폴더끼리는 묻지 않고 합치고, 파일끼리는 묻고, 종류가 엇갈리면 건너뛰기만 할 수 있다.
     * 건너뛴 폴더의 안쪽 항목은 묻지 않고 함께 건너뛴다. 정한 항목도 이름 목록에 더해, 넣는 항목끼리 이름이 겹쳐도 같은 길로 묻는다.
     * @param entries 넣을 항목. 폴더 항목은 그 하위 항목보다 앞에 온다
     * @param headers 원본의 항목
     * @param prefix 넣을 폴더의 항목 이름 앞부분
     * @param archiveName 질문에 보일 압축 파일 이름
     * @param ask 겹침의 처리 방법을 묻는 함수
     * @return 쓸 항목과 지울 기존 항목
     */
    private fun planOf(entries: List<ArchiveSource>, headers: List<FileHeader>, prefix: String, archiveName: String, ask: (WriteConflict) -> ConflictChoice): AppendPlan {
        val names = ExistingNames.of(headers)
        val writes = mutableListOf<PlannedWrite>()
        val removals = mutableListOf<String>()
        val skippedFolders = mutableListOf<String>()
        entries.forEach { entry ->
            if (skippedFolders.any { entry.name.startsWith("$it/") }) return@forEach
            val target = prefix + entry.name
            val existing = names.kindOf(target)
            when (existing) {
                null -> writes += PlannedWrite(target, entry)
                ExistingKind.FOLDER -> when (entry) {
                    // 폴더끼리는 합친다 — 이미 있는 폴더 항목을 다시 쓰지 않는다
                    is ArchiveSource.Folder -> Unit
                    is ArchiveSource.Regular -> ask(conflictOf(target, archiveName, WriteConflictKind.TYPE_MISMATCH))
                }
                ExistingKind.FILE -> when (entry) {
                    is ArchiveSource.Folder -> {
                        ask(conflictOf(target, archiveName, WriteConflictKind.TYPE_MISMATCH))
                        skippedFolders += entry.name
                    }
                    is ArchiveSource.Regular -> if (ask(conflictOf(target, archiveName, WriteConflictKind.FILE)) == ConflictChoice.PROCEED) {
                        names.fileHeaderNameOf(target)?.let { removals += it }
                        writes += PlannedWrite(target, entry)
                    }
                }
            }
            if (existing == null) names.add(target, entry is ArchiveSource.Folder)
        }
        return AppendPlan(writes, removals)
    }

    /**
     * 겹친 자리의 질문을 만든다.
     * @param target 겹친 항목의 압축 파일 안 경로
     * @param archiveName 압축 파일 이름
     * @param kind 겹침 종류
     * @return 질문. 폴더 경로는 압축 파일 이름부터 보인다
     */
    private fun conflictOf(target: String, archiveName: String, kind: WriteConflictKind): WriteConflict {
        val parent = target.substringBeforeLast('/', missingDelimiterValue = "")
        return WriteConflict(target.substringAfterLast('/'), if (parent.isEmpty()) archiveName else "$archiveName/$parent", kind)
    }

    /**
     * 같은 폴더의 임시 사본에 [plan]대로 쓰고, 장치에 기록한 뒤 [archive] 자리로 교체한다. 끝까지 쓰지 못하면(취소·입출력 실패) 사본만 지운다.
     * 완료 보고(취소를 확인한다)는 교체 전에 한다 — 교체한 뒤의 취소가 바뀐 압축 파일을 취소된 작업으로 알리지 않도록.
     * @param archive 원본 ZIP
     * @param plan 쓸 항목과 지울 기존 항목
     * @param password 새 파일 항목을 AES-256으로 암호화할 비밀번호. null이면 암호화하지 않는다
     * @param onProgress 진행 상태를 수신하는 콜백
     * @return 결과. 공간이 부족하면 아무것도 쓰지 않고 실패한다
     */
    private fun writeAndReplace(archive: File, plan: AppendPlan, password: String?, onProgress: (ProgressPhase) -> Unit): IoResult<Unit> {
        val files = plan.writes.mapNotNull { it.source as? ArchiveSource.Regular }
        val addedBytes = files.sumOf { it.bytes }
        // 사본 하나에 기존 항목을 지우면 zip4j가 사본을 한 번 더 다시 쓴다
        val copies = if (plan.removals.isEmpty()) 1 else 2
        val dir = archive.absoluteFile.parentFile
        checkDiskSpace(archive.length() * copies + addedBytes, freeSpaceOf(dir))?.let { return IoResult.ItemFailed(it) }
        // 사본을 만드는 것도 파일 하나로 센다 — 큰 압축 파일을 복사하는 동안에도 진행이 보이고 취소가 듣도록
        val tracker = ByteProgressTracker(archive.length() + addedBytes, files.size + 1, onProgress)
        tracker.reportStart()
        val copy = File.createTempFile(".${archive.name}.", ".tmp", dir)
        return leftovers.deletingUnlessCompleted(copy) {
            copyWithProgress(archive, copy, tracker)
            ZipFile(copy, password?.toCharArray()).use { zf ->
                zf.charset = Charsets.UTF_8
                if (plan.removals.isNotEmpty()) unwrappingZipTask { zf.removeFiles(plan.removals) }
                plan.writes.forEach { write(zf, it, password != null, tracker) }
            }
            syncToDisk(copy)
            tracker.reportComplete()
            replaceFileAtomically(copy, archive)
        }
    }

    /**
     * [archive]를 [copy]에 진행을 보고하며 복사한다.
     * @param archive 원본
     * @param copy 이 작업이 만든 빈 임시 파일
     * @param tracker 누적 진행률 보고기
     */
    private fun copyWithProgress(archive: File, copy: File, tracker: ByteProgressTracker) {
        val size = archive.length()
        tracker.startFile()
        archive.inputStream().use { input -> copy.outputStream().use { output -> tracker.pump(input, output, archive.name, size) } }
        tracker.onFileCompleted(archive.name, size)
    }

    /**
     * 정한 항목 하나를 [zf]에 넣는다. 폴더는 내용 없는 `이름/` 항목으로, 파일은 내용을 읽으며 진행을 보고한다.
     * @param zf 쓰는 중인 사본
     * @param write 넣을 항목
     * @param encrypt 파일 항목을 AES-256으로 암호화하는지
     * @param tracker 누적 진행률 보고기
     */
    private fun write(zf: ZipFile, write: PlannedWrite, encrypt: Boolean, tracker: ByteProgressTracker) {
        when (val source = write.source) {
            is ArchiveSource.Folder -> unwrappingZipTask {
                zf.addStream(InputStream.nullInputStream(), ZipParameters().apply { fileNameInZip = "${write.target}/" })
            }
            is ArchiveSource.Regular -> {
                tracker.startFile()
                ReportingInputStream(source.file.inputStream(), tracker, source.file.name, source.bytes).use { input ->
                    unwrappingZipTask { zf.addStream(input, fileParameters(write.target, encrypt)) }
                }
                tracker.onFileCompleted(source.file.name, source.bytes)
            }
        }
    }

    /**
     * 파일 항목의 쓰기 설정을 만든다. 압축([ZipArchiveCompressor])과 같은 암호화를 쓴다.
     * @param name 압축 파일 안의 '/' 구분 경로
     * @param encrypt AES-256으로 암호화하는지
     * @return 항목 이름과 암호화 설정을 담은 ZipParameters
     */
    private fun fileParameters(name: String, encrypt: Boolean): ZipParameters = ZipParameters().apply {
        fileNameInZip = name
        if (encrypt) {
            isEncryptFiles = true
            encryptionMethod = EncryptionMethod.AES
            aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
        }
    }

    /**
     * zip4j 작업을 실행하고, zip4j가 감싼 예외를 원래 예외로 되돌린다 — zip4j는 작업 안에서 난 예외(진행 보고가 던진 취소, 구현 오류, 입출력 실패)를
     * 모두 [ZipException]으로 감싸, 그대로 두면 취소가 입출력 실패로 바뀐다.
     * @param block 실행할 zip4j 작업
     * @return [block]의 결과
     */
    private inline fun <T> unwrappingZipTask(block: () -> T): T =
        try {
            block()
        } catch (e: ZipException) {
            val cause = e.cause
            if (e.javaClass == ZipException::class.java && (cause is RuntimeException || cause is IOException)) throw cause
            throw e
        }

    /** 원본에 이미 있는 항목 종류. */
    private enum class ExistingKind { FILE, FOLDER }

    /**
     * 원본의 항목 이름. 폴더는 폴더 항목(`a/`)뿐 아니라 다른 항목의 경로에만 나타나는 폴더도 담는다 — 폴더 항목 없이 만든 ZIP도 많기 때문이다.
     * @param files 파일 항목의 이름(앞뒤 그대로)과 그 항목 이름
     * @param folders 폴더 경로(끝의 '/' 없이)
     */
    private class ExistingNames(private val files: MutableMap<String, String>, private val folders: MutableSet<String>) {

        /** [target] 자리에 있는 항목의 종류. 없으면 null. */
        fun kindOf(target: String): ExistingKind? = when {
            target in files -> ExistingKind.FILE
            target in folders -> ExistingKind.FOLDER
            else -> null
        }

        /** [target] 자리 파일 항목의 원래 이름(지울 때 쓴다). */
        fun fileHeaderNameOf(target: String): String? = files[target]

        /** 넣기로 정한 항목을 더한다. 그 경로의 상위 폴더도 함께 더한다. */
        fun add(target: String, isFolder: Boolean) {
            if (isFolder) folders += target else files[target] = target
            folders += parentsOf(target)
        }

        companion object {
            /** [headers]의 이름을 모은다. */
            fun of(headers: List<FileHeader>): ExistingNames {
                val names = ExistingNames(HashMap(), HashSet())
                headers.forEach { header ->
                    val path = header.fileName.trimEnd('/')
                    if (header.isDirectory) names.folders += path else names.files[path] = header.fileName
                    names.folders += parentsOf(path)
                }
                return names
            }

            /** [path]의 상위 폴더 경로들. `a/b/c`면 `a`, `a/b`. */
            private fun parentsOf(path: String): List<String> {
                val segments = path.split('/')
                return (1 until segments.size).map { segments.subList(0, it).joinToString("/") }
            }
        }
    }

    /**
     * 사본에 넣을 항목 하나.
     * @param target 압축 파일 안의 '/' 구분 경로(폴더도 끝에 '/'를 붙이지 않는다)
     * @param source 내용을 읽을 로컬 항목
     */
    private data class PlannedWrite(val target: String, val source: ArchiveSource)

    /**
     * 사본에 할 일.
     * @param writes 넣을 항목. 폴더 항목은 그 하위 항목보다 앞에 온다
     * @param removals 새 항목으로 바꾸려고 지울 기존 파일 항목의 이름
     */
    private data class AppendPlan(val writes: List<PlannedWrite>, val removals: List<String>)

    /**
     * 읽은 바이트마다 [tracker]에 진행을 보고하는 입력. zip4j가 내용을 끌어 읽는 동안 진행을 보고하고 취소를 확인하려고 쓴다.
     * @param input 원본 입력
     * @param tracker 누적 진행률 보고기
     * @param fileName 보고에 보일 파일 이름
     * @param fileBytes 파일의 전체 크기
     */
    private class ReportingInputStream(
        input: InputStream,
        private val tracker: ByteProgressTracker,
        private val fileName: String,
        private val fileBytes: Long,
    ) : FilterInputStream(input) {

        override fun read(): Int = super.read().also { if (it != -1) tracker.addBytes(1, fileName, fileBytes) }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) tracker.addBytes(it.toLong(), fileName, fileBytes) }
    }
}
