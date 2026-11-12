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
     * 아카이브 항목 [entryName]을 풀 위치를 정한다. 판정에 쓴 정규 경로를 그대로 돌려주므로, 추출은 검증한 바로 그 경로에 쓴다.
     * @param entryName 아카이브 안의 '/' 구분 항목 경로
     * @return 풀 위치(정규 경로). 루트 밖이나 루트 자신을 가리키면(Zip Slip 등) null
     */
    fun targetOf(entryName: String): File? = directory.resolve(entryName).canonicalFile.takeIf { it.path.startsWith(childPrefix) }
}
