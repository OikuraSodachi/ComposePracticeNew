package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.ArchiveEntryKey
import com.todokanai.composepracticenew.model.ArchiveListingId
import java.io.DataOutputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest

/**
 * 추출할 항목과 그 항목을 풀 경로. 항목 번호는 [ArchiveExtractor.entries]가 읽는 순서다.
 * 풀 경로는 [ArchiveEntryWriter]에 넘기는 경로라, 위치 검증(루트 밖·루트 자신)은 지금처럼 쓰기 도우미가 한다.
 */
sealed interface ExtractionScope {

    /**
     * [index] 항목을 풀 경로를 정한다.
     * @param index 항목 번호
     * @param entryPath 아카이브 안의 '/' 구분 항목 경로
     * @return 추출 루트 기준의 '/' 구분 경로. 풀지 않는 항목이면 null
     */
    fun pathOf(index: Int, entryPath: String): String?

    /** 모든 항목을 아카이브 안의 경로 그대로 푼다(압축 해제). */
    data object Whole : ExtractionScope {
        override fun pathOf(index: Int, entryPath: String): String = entryPath
    }

    /**
     * 고른 항목만 정해 둔 경로로 푼다.
     * @param paths 항목 번호별 풀 경로
     */
    class Selected(private val paths: Map<Int, String>) : ExtractionScope {

        /** 풀 항목 번호(오름차순). 항목 번호로 골라 푸는 라이브러리에 넘긴다. */
        val indices: IntArray get() = paths.keys.sorted().toIntArray()

        override fun pathOf(index: Int, entryPath: String): String? = paths[index]
    }
}

/**
 * 내용 보기의 [folderNames] 폴더에서 [keys]로 고른 항목을 [entries]에서 찾아, 그 폴더 기준의 상대 경로를 정한다.
 * 위치는 내용 보기의 트리([archiveTreeOf])와 같은 판정([extractionSegmentsOf])으로 정해, 화면에서 고른 항목과 푸는 항목이 어긋나지 않는다:
 * 폴더가 아닌 항목은 순번 키로, 폴더는 이름 키로 그 아래 항목(폴더 항목 자신 포함) 전체를 고른다.
 * 루트의 풀 수 없는 경로 항목을 고르면 원래 경로를 그대로 돌려준다 — 쓰기 도우미가 압축 해제와 같이 풀 수 없는 경로로 실패에 넣도록.
 * @param entries [ArchiveExtractor.entries]가 읽은 항목 목록
 * @param folderNames 고른 폴더의 이름들(루트부터)
 * @param keys 그 폴더에서 고른 항목의 키
 * @return 항목 번호별 풀 경로(고른 폴더 기준)
 */
internal fun selectedEntryPathsOf(entries: List<ArchiveEntryInfo>, folderNames: List<String>, keys: Set<ArchiveEntryKey>): Map<Int, String> {
    val paths = HashMap<Int, String>()
    entries.forEachIndexed { index, entry ->
        val segments = extractionSegmentsOf(entry.path)
        if (segments == null) {
            if (folderNames.isEmpty() && ArchiveEntryKey.Item(index) in keys) paths[index] = entry.path
            return@forEachIndexed
        }
        val relative = relativeSegmentsOf(segments, folderNames) ?: return@forEachIndexed
        if (isSelected(index, entry.kind, relative, keys)) paths[index] = relative.joinToString("/")
    }
    return paths
}

/**
 * [segments]가 [folderNames] 폴더 안쪽이면 그 폴더 기준의 세그먼트를 돌려준다.
 * @param segments 루트부터의 항목 세그먼트
 * @param folderNames 폴더 이름들(루트부터)
 * @return 폴더 기준 세그먼트(비어 있지 않음). 폴더 안쪽이 아니면 null
 */
private fun relativeSegmentsOf(segments: List<String>, folderNames: List<String>): List<String>? =
    segments.takeIf { it.size > folderNames.size && it.subList(0, folderNames.size) == folderNames }?.drop(folderNames.size)

/**
 * 폴더 기준 위치가 [relative]인 항목이 [keys]로 고른 것인지 판별한다. 내용 보기 트리의 키 규칙을 따른다 —
 * 폴더 바로 아래의 폴더가 아닌 항목은 순번 키, 폴더 항목과 하위 폴더 안쪽 항목은 맨 위 폴더의 이름 키를 갖는다.
 * @param index 항목 번호
 * @param kind 항목 종류
 * @param relative 폴더 기준 세그먼트
 * @param keys 고른 항목의 키
 * @return 고른 항목이면 true
 */
private fun isSelected(index: Int, kind: ArchiveEntryKind, relative: List<String>, keys: Set<ArchiveEntryKey>): Boolean =
    if (relative.size == 1 && kind != ArchiveEntryKind.FOLDER) ArchiveEntryKey.Item(index) in keys
    else ArchiveEntryKey.Folder(relative.first()) in keys

/**
 * 항목 목록의 식별값을 계산한다. 항목마다 경로·종류·크기·수정 시각·암호화 여부를 순서대로 SHA-256에 넣는다 —
 * 목록이 하나라도 다르면 순번 키가 다른 항목을 가리킬 수 있으므로, 키로 고른 항목을 풀 때 같은 목록인지 이 값으로 가린다.
 * 경로는 길이를 앞에 붙여 넣어 경계가 모호하지 않게 한다.
 * @param entries [ArchiveExtractor.entries]가 읽은 항목 목록
 * @return 목록 식별값
 */
internal fun listingIdOf(entries: List<ArchiveEntryInfo>): ArchiveListingId {
    val digest = MessageDigest.getInstance("SHA-256")
    // 목록 전체를 메모리에 모으지 않고 바로 해시에 흘려 넣는다
    DataOutputStream(DigestOutputStream(DiscardingOutputStream, digest)).use { out ->
        out.writeInt(entries.size)
        for (entry in entries) {
            val path = entry.path.toByteArray(Charsets.UTF_8)
            out.writeInt(path.size)
            out.write(path)
            out.writeInt(entry.kind.ordinal)
            out.writeLong(entry.size ?: -1L)
            out.writeLong(entry.lastModifiedMillis ?: Long.MIN_VALUE)
            out.writeBoolean(entry.isEncrypted)
        }
    }
    return ArchiveListingId(digest.digest().joinToString("") { "%02x".format(it) })
}

/** 받은 바이트를 버리는 출력. 해시 계산에서 바이트를 해시에만 흘려 넣는 데 쓴다. */
private object DiscardingOutputStream : OutputStream() {
    override fun write(b: Int) {}
    override fun write(b: ByteArray, off: Int, len: Int) {}
}
