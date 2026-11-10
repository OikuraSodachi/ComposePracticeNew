package com.todokanai.composepracticenew.datasource

import java.io.File

/**
 * 아카이브 항목을 풀 루트 디렉터리. 추출과 충돌 판정이 같은 규칙으로 항목의 위치를 정하도록 한 곳에 둔다.
 * 루트의 정규 경로는 만들 때 한 번만 구한다 — Android는 정규화 결과를 캐시하지 않으므로 항목마다 구하면 매번 파일시스템을 다시 조회한다.
 * @param root 추출 대상 루트 디렉터리
 */
class ExtractionRoot(root: File) {

    /** 정규화한 루트 디렉터리. */
    private val canonicalRoot: File = root.canonicalFile

    /** 루트 하위 경로가 반드시 갖는 접두어(구분자로 끝남). 루트 자신은 하위로 보지 않는다. */
    private val childPrefix: String = canonicalRoot.path.let { if (it.endsWith(File.separator)) it else it + File.separator }

    /**
     * 아카이브 항목 [entryName]을 풀 위치를 정한다. 판정에 쓴 정규 경로를 그대로 돌려주므로, 추출은 검증한 바로 그 경로에 쓴다.
     * @param entryName 아카이브 안의 '/' 구분 항목 경로
     * @return 풀 위치(정규 경로). 루트 밖이나 루트 자신을 가리키면(Zip Slip 등) null
     */
    fun targetOf(entryName: String): File? = canonicalRoot.resolve(entryName).canonicalFile.takeIf { it.path.startsWith(childPrefix) }

    /**
     * 아카이브 항목 [entryName]을 풀면 루트 바로 아래에 생기는(또는 덮어쓰는) 이름을 구한다. 위치는 [targetOf]로 정한다.
     * @param entryName 아카이브 안의 '/' 구분 항목 경로
     * @return 루트 바로 아래 이름. 풀지 않는 항목이면 null
     */
    fun topLevelNameOf(entryName: String): String? =
        targetOf(entryName)?.let { canonicalRoot.toPath().relativize(it.toPath()).getName(0).toString() }
}
