package com.todokanai.composepracticenew.datasource

import java.io.File

/**
 * 아카이브 항목을 풀 루트 디렉터리. 항목의 위치 검증(Zip Slip 차단)을 한 곳에 둔다.
 * 루트의 정규 경로는 만들 때 한 번만 구한다 — Android는 정규화 결과를 캐시하지 않으므로 항목마다 구하면 매번 파일시스템을 다시 조회한다.
 * @param root 추출 대상 루트 디렉터리
 */
class ExtractionRoot(root: File) {

    /** 정규화한 루트 디렉터리. [targetOf]가 돌려주는 위치의 상위를 따라 올라가면 이 값과 같아진다. */
    val directory: File = root.canonicalFile

    /** 루트 하위 경로가 반드시 갖는 접두어(구분자로 끝남). 루트 자신은 하위로 보지 않는다. */
    private val childPrefix: String = directory.path.let { if (it.endsWith(File.separator)) it else it + File.separator }

    /**
     * 아카이브 항목 [entryName]을 풀 위치를 정한다. 판정에 쓴 경로를 그대로 돌려주므로, 추출은 검증한 바로 그 경로에 쓴다.
     * `.`·`..`는 글자로만 정리하고 링크는 풀지 않는다 — 링크를 풀면 루트 안의 링크가 가리키는 다른 자리를 돌려주어,
     * 쓰는 쪽이 그 자리의 링크를 알아보지 못하고 링크가 가리키는 파일에 쓴다(#338). 루트 아래 자리의 링크는 쓰는 쪽([ArchiveEntryWriter])이 건너뛴다.
     * 풀 수 있는지는 항목 경로만으로 정한다([extractionSegmentsOf]) — 루트에 붙인 뒤 정리하면 `../<루트 이름>/x`가 루트 이름에 따라 풀리거나 건너뛰고,
     * 루트를 모르는 내용 보기가 같은 판정을 할 수 없다.
     * @param entryName 아카이브 안의 '/' 구분 항목 경로
     * @return 풀 위치. 루트 밖이나 루트 자신을 가리키면(Zip Slip 등) null
     */
    fun targetOf(entryName: String): File? {
        val segments = extractionSegmentsOf(entryName) ?: return null
        return directory.resolve(segments.joinToString("/")).normalize().takeIf { it.path.startsWith(childPrefix) }
    }
}

/**
 * 아카이브 항목 [path]를 루트부터의 세그먼트로 정리한다. `.`·`..`는 글자로만 정리하고 링크는 보지 않는다.
 * 압축 해제([ExtractionRoot.targetOf])와 내용 보기([archiveTreeOf])가 이 판정을 함께 쓴다.
 * @param path 아카이브 안의 '/' 구분 항목 경로
 * @return 루트부터의 세그먼트. 절대 경로이거나, `..`로 루트 위로 올라가거나(다시 들어와도), 루트 자신을 가리키면 null
 */
internal fun extractionSegmentsOf(path: String): List<String>? {
    if (path.startsWith('/')) return null
    val segments = ArrayDeque<String>()
    for (segment in path.split('/')) {
        when (segment) {
            "", "." -> continue
            ".." -> segments.removeLastOrNull() ?: return null
            else -> segments.addLast(segment)
        }
    }
    return segments.toList().ifEmpty { null }
}
