package com.todokanai.composepracticenew.data.ftp

import com.todokanai.composepracticenew.data.progress.PreparationProgressReporter
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.valueOr
import javax.inject.Singleton

/**
 * 트리 수집이 들어가 있는 디렉터리 하나.
 * @param listed 그 디렉터리의 목록
 * @param identity 상위 목록의 항목이 알려준 그 디렉터리의 식별값. 모르거나 시작 디렉터리면 null
 */
private class DirectoryVisit(val listed: IdentifiedListing, val identity: String?) {
    /** 아직 수집하지 않은 항목. */
    val remaining: Iterator<ListedItem> = listed.items.iterator()
}

/**
 * 트리 수집 하나가 받은 목록의 메모리를 추정해 더한다. 받은 항목은 수집이 끝날 때까지 메모리에 남으므로, 받은 만큼이 수집이 쓰는 메모리다.
 *
 * 세지 않는 메모리가 있다 — 들어가 있는 디렉터리마다 그 목록의 식별값([IdentifiedListing.identities])이 남지만 더하지 않는다.
 * 주류 서버의 `unique`는 장치 번호·inode의 16진수(ProFTPD `%lXU%lX`, Pure-FTPd `%xg%llx`, 약 33자 이하)라 정상 트리에서는
 * 실제 메모리가 상한을 조금(경로 위 폴더에 항목이 아주 많으면 약 10MiB) 넘는 정도다. 그러나 RFC 3659의 `unique` 문법
 * (`token = 1*RCHAR`)에는 길이 상한이 없어, 수 KB 이상의 `unique`를 보내는 서버에서는 깊이마다 쌓여 상한에 이르기 전에 메모리가 바닥날 수 있다.
 * @param limitBytes 수집이 쓸 수 있는 메모리의 추정 상한(바이트)
 */
private class TreeMemoryBudget(private val limitBytes: Long) {
    /** 지금까지 받은 항목의 추정 메모리(바이트). */
    private var usedBytes = 0L

    /** 다음 목록을 받는 동안 쓸 수 있는 추정 메모리(바이트). [admit]이 상한을 넘기면 수집이 끝나므로 음수가 되지 않는다. */
    val remainingBytes: Long get() = limitBytes - usedBytes

    /**
     * 받은 목록의 추정 메모리를 더한다.
     * @param items 받은 목록의 항목과 해석하지 못한 줄
     * @return 더한 뒤에도 상한 안이면 true
     */
    fun admit(items: List<ListedItem>): Boolean {
        usedBytes += items.sumOf(::estimatedBytes)
        return usedBytes <= limitBytes
    }

    /**
     * 항목 하나가 차지하는 메모리의 상한 추정치. 고정 부분([ENTRY_OVERHEAD_BYTES])에 경로·이름(해석하지 못한 줄은 그 줄)을 문자당 2바이트(UTF-16)로 더한다 —
     * ASCII만인 문자열은 실제로 그 절반이므로, 어느 문자로 된 경로든 항목 자체의 실제 메모리가 추정치를 넘지 않는다(식별값은 세지 않는다 — [TreeMemoryBudget]).
     * 해석하지 못한 줄의 디렉터리 경로는 그 디렉터리 항목과 같은 문자열을 가리키므로 세지 않는다.
     * @param item 받은 항목
     * @return 추정 메모리(바이트)
     */
    private fun estimatedBytes(item: ListedItem): Long = ENTRY_OVERHEAD_BYTES + 2L * when (item) {
        is ListedItem.Entry -> item.entry.path.length + item.entry.name.length
        is ListedItem.Unreadable -> item.line.length
    }

    private companion object {
        /**
         * 항목 하나에서 경로·이름의 문자 데이터를 뺀 메모리(바이트): 항목·문자열·크기 객체의 헤더와 필드, 목록의 참조.
         * JVM에서 경로 79자인 ASCII 항목 10만 개가 항목당 약 269바이트였다(문자 데이터 약 100바이트를 빼면 약 165바이트).
         */
        const val ENTRY_OVERHEAD_BYTES = 170L
    }
}

/**
 * 원격 디렉터리 아래의 트리를 모두 모은다. 폴더 다운로드([FtpTransferrer])와 재귀 삭제([FtpRemoteDeletion])가 쓴다.
 * 수집이 끝나는지를 목록의 정확성에 맡기지 않고 받은 목록의 메모리 상한으로 보장하며, 서버가 식별값을 알려주면 순환을 더 일찍 알린다.
 * @param listing 각 디렉터리의 목록을 받는 FtpListingReader
 * @param treeMemoryLimitBytes 트리 수집 하나가 받은 목록에 쓸 수 있는 메모리의 추정 상한(바이트, [collectRemoteTree]). 0보다 커야 한다
 */
@Singleton
class FtpTreeCollector internal constructor(
    private val listing: FtpListingReader,
    private val treeMemoryLimitBytes: Long
) {

    /** 트리 수집 메모리 상한을 [TREE_MEMORY_LIMIT_BYTES]로 둔 수집기를 만든다. Hilt가 사용하는 생성자다. */
    internal constructor(listing: FtpListingReader) : this(listing, TREE_MEMORY_LIMIT_BYTES)

    init {
        // 0 이하는 상한이 아니다 — 어떤 목록도 받을 수 없어 빈 시작 폴더 말고는 모두 실패한다
        require(treeMemoryLimitBytes > 0) { "treeMemoryLimitBytes must be positive: $treeMemoryLimitBytes" }
    }

    /**
     * remoteDirPath 하위의 모든 항목을 깊이 우선으로 평탄화한 목록을 반환한다. 디렉터리는 자신의 자식보다 앞에 온다.
     * 어느 하위 디렉터리라도 조회에 실패하거나 내려갈 수 없으면([descentRefusal]) 일부만 담긴 목록 대신 그 실패를 돌려준다.
     * 목록이 상위 디렉터리를 하위로 보여줘도(디렉터리로 보고된 링크, 서버 오류) 수집은 끝난다 — 서버가 무엇을 보여주든 받은 목록의 추정 메모리가
     * [treeMemoryLimitBytes]를 넘으면 멈추므로([TreeMemoryBudget]), 순환과 구분할 수 없는 큰 트리(아래 지원 범위)와 순환 모두 실패로 끝난다.
     * 방문한 디렉터리도 모두 받은 항목이므로 이 상한이 메모리와 함께 방문 수(목록 조회 횟수)도 막는다.
     * 그 위에서 서버가 식별값(MLSD `unique`)을 알려주면 순환을 더 일찍, 순환이라는 이유로 알린다([descentRefusal]).
     * 지원 범위: 받은 목록의 추정 메모리가 상한 안인 트리. 넘는 트리는 일부만 처리하지 않고 실패로 알린다.
     * 순환 감지는 같은 대상에 어느 경로로든 같은 식별값을 주는 서버(RFC 3659 §7.5.2)만 지원한다 — 경로마다 다른 값을 주는 서버의 순환은
     * 순환이 아닌 큰 트리와 구분할 수 없어 상한·경로 한도의 실패로 끝난다.
     * 깊이는 경로 한도까지 수천 단계가 될 수 있어, 재귀 대신 들어가 있는 디렉터리를 쌓아 두고 거쳐 온 식별값을 한 집합에서 넣고 뺀다 —
     * 스레드 스택 깊이에 기대지 않는다.
     * 알려진 문제(방치하기로 결정): 각 목록은 서버가 드러내는 항목뿐이라([FtpListingReader.identifiedListing]) 서버가 숨긴 항목은 트리에서 빠지지만, 수집은 성공으로 끝난다.
     * 그래서 폴더 다운로드는 숨긴 항목 없이 완료로 보고되고, 재귀 삭제는 보이는 항목을 지운 뒤 RMD에서 실패한다([FtpRemoteDeletion.removeDirectoryRecursive]).
     * 해석하지 못한 목록 줄은 버리지 않고 그 디렉터리의 항목 자리에 담아, 받는 쪽이 그 항목만 실패로 알리게 한다(#255).
     * 시간 상한을 두지 않으므로(순환과 큰 트리를 구분할 수 없다), 받은 항목마다 [preparation]에 알려 진행을 보이고 그 보고에서 취소를 받는다(#284).
     * @param remoteDirPath 수집할 원격 디렉터리 경로 (ftp://server/path 형식)
     * @param preparation 받은 항목을 알릴 준비 단계 진행 보고기. 취소되면 보고에서 CancellationException을 던진다
     * @return 하위 항목 전체 목록(해석하지 못한 줄 포함), 또는 조회 실패나 메모리 상한·순환·경로 한도에 의한 실패
     */
    internal suspend fun collectRemoteTree(remoteDirPath: String, preparation: PreparationProgressReporter): IoResult<List<ListedItem>> {
        val result = mutableListOf<ListedItem>()
        val budget = TreeMemoryBudget(treeMemoryLimitBytes)
        // 거쳐 온 디렉터리의 식별값 — 이미 있는 값이면 내려가지 않으므로 같은 값이 두 번 들어가지 않아, 그 디렉터리를 나올 때 빼도 된다
        val ancestorIdentities = HashSet<String>()
        // 들어가 있는 하위 디렉터리 중 식별값을 모르는 것의 수(시작 디렉터리 제외) — 순환은 지금 경로 위에서만 일어나므로,
        // 0보다 크면 지금 경로가 순환하는지 식별값으로 판별할 수 없다. 끝낸 형제 디렉터리는 순환이 아니었으므로 세지 않는다
        var unidentifiedOnPath = 0
        val pending = ArrayDeque<DirectoryVisit>()
        pending.addLast(enterDirectory(remoteDirPath, identity = null, budget, undecidable = false).valueOr { return it })
        while (pending.isNotEmpty()) {
            val visit = pending.last()
            if (!visit.remaining.hasNext()) {
                pending.removeLast()
                // 스택이 비었으면 방금 나온 것은 시작 디렉터리다
                if (visit.identity != null) ancestorIdentities.remove(visit.identity) else if (pending.isNotEmpty()) unidentifiedOnPath--
                continue
            }
            val item = visit.remaining.next()
            result.add(item)
            preparation.onEntryFound(item.progressName)
            val entry = (item as? ListedItem.Entry)?.entry?.takeIf { item.isDirectoryEntry } ?: continue
            val identity = visit.listed.identities[entry.path]
            if (identity == null) unidentifiedOnPath++
            descentRefusal(entry.path, identity, ancestorIdentities, undecidable = unidentifiedOnPath > 0)?.let { return it }
            pending.addLast(enterDirectory(entry.path, identity, budget, undecidable = unidentifiedOnPath > 0).valueOr { return it })
            identity?.let(ancestorIdentities::add)
        }
        return IoResult.Success(result)
    }

    /**
     * 트리 수집이 [dirPath]로 들어간다. 목록을 조회하고, 받은 목록을 수집의 메모리 상한에 더한다.
     * 목록을 받는 동안에도 남은 상한을 넘으면 받기를 멈춘다([ListingLimit]) — 끝까지 받은 뒤에만 세면 목록 하나가 상한을 넘겨도 막지 못한다.
     * @param dirPath 들어갈 원격 디렉터리 경로 (ftp://server/path 형식)
     * @param identity 상위 목록의 항목이 알려준 이 디렉터리의 식별값. 모르거나 시작 디렉터리면 null
     * @param budget 이 수집의 메모리 상한
     * @param undecidable 이 디렉터리까지의 경로가 순환하는지 식별값으로 판별할 수 없으면 true — 상한에 이른 실패에 그 설명을 붙인다
     * @return 들어간 디렉터리. 조회에 실패했거나 받은 목록이 상한을 넘기면 그 실패
     */
    private suspend fun enterDirectory(
        dirPath: String,
        identity: String?,
        budget: TreeMemoryBudget,
        undecidable: Boolean
    ): IoResult<DirectoryVisit> {
        val tooMany = IoResult.ItemFailed(FailureReason.TooManyEntries(extractFtpPath(dirPath), undecidable))
        val listed = listing.list(dirPath, withIdentities = true, ListingLimit(budget.remainingBytes, tooMany)).valueOr { return it }
        if (!budget.admit(listed.items)) return tooMany
        return IoResult.Success(DirectoryVisit(listed, identity))
    }

    /**
     * 트리 수집이 [path]에서 거쳐 온 디렉터리로 돌아왔다는 실패를 만든다.
     * @param path 거쳐 온 디렉터리와 같은 디렉터리의 경로 (ftp://server/path 형식)
     * @return 순환을 알리는 실패
     */
    private fun cycleRefusal(path: String): IoResult.ItemFailed =
        IoResult.ItemFailed(FailureReason.FolderCycle(extractFtpPath(path)))

    /**
     * 트리 수집이 하위 디렉터리 [path]로 내려가면 안 되는 이유를 실패로 만든다.
     * 서버가 식별값을 알려주면(MLSD `unique`, 서버가 이미 목록에 담는 경우) 거쳐 온 디렉터리와 같은 디렉터리인지로 순환을 판별한다 —
     * 수집이 끝나는 것은 메모리 상한([TreeMemoryBudget])이 보장하고, 이 판별은 순환을 상한보다 일찍, 순환이라는 이유로 알린다.
     * 경로가 [PATH_MAX_BYTES]에 이르면 식별값과 무관하게 멈춘다. 값은 Linux(Android·대부분의 FTP 서버)가 한 경로로 다룰 수 있는 길이라
     * 그보다 깊은 항목은 내려받아도 로컬에 만들 수 없고, 실제 트리를 자르는 일은 사실상 없다.
     * @param path 내려갈 하위 디렉터리 경로 (ftp://server/path 형식)
     * @param identity 서버가 알려준 [path]의 식별값. 없으면 null
     * @param ancestorIdentities [path]의 상위 디렉터리들의 식별값
     * @param undecidable [path]까지의 경로가 순환하는지 식별값으로 판별할 수 없으면 true — 경로 한도 실패에 그 설명을 붙인다
     * @return 내려가면 안 되면 그 이유를 담은 실패, 내려가도 되면 null
     */
    private fun descentRefusal(path: String, identity: String?, ancestorIdentities: Set<String>, undecidable: Boolean): IoResult.ItemFailed? = when {
        identity != null && identity in ancestorIdentities -> cycleRefusal(path)
        exceedsPathMax(path) -> IoResult.ItemFailed(FailureReason.PathTooLong(extractFtpPath(path), PATH_MAX_BYTES, undecidable))
        else -> null
    }

    /**
     * [path]의 FTP 경로가 경로 한도([PATH_MAX_BYTES])에 이르는지 판별한다. 한도는 Android 파일 이름 인코딩(UTF-8) 기준이다.
     * @param path 판별할 경로 (ftp://server/path 형식)
     * @return 한도에 이르면 true
     */
    private fun exceedsPathMax(path: String): Boolean = extractFtpPath(path).toByteArray(Charsets.UTF_8).size >= PATH_MAX_BYTES

    companion object {
        /** 트리 수집이 내려갈 수 있는 경로 길이의 한도(바이트, 끝의 NUL 포함). Linux(Android)의 PATH_MAX다. */
        private const val PATH_MAX_BYTES = 4096
        /**
         * 트리 수집 하나가 받은 목록에 쓸 수 있는 메모리의 추정 상한(바이트, [TreeMemoryBudget]). 받은 항목은 수집이 끝날 때까지 메모리에 남는다.
         * 흔한 앱 힙(약 256MB, 기기마다 다름)의 일부로, 경로 80자인 항목이면 약 9만 개, 경로가 [PATH_MAX_BYTES]에 이르는 항목이면 약 4천 개다.
         */
        private const val TREE_MEMORY_LIMIT_BYTES = 32L * 1024 * 1024
    }
}
