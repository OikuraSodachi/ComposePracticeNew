package com.todokanai.composepracticenew.repository

/**
 * 주입된 PathRuleFileSystem 집합에서 경로를 처리하는 파일 시스템의 규칙으로 부모 경로와 표시 이름을 정하는 StoragePathRules 구현체.
 * 화면마다 다루는 스토리지는 넘기는 집합으로 정하며, 같은 화면의 다른 역할 집합과 같은 구현을 담은 집합을 넘긴다(RepositoryModule이 함께 정한다).
 * @param fileSystems 이 규칙이 다루는 경로 종류별 PathRuleFileSystem 집합 — 각 handles()는 서로 배타적이다
 */
class FileSystemPathRules(fileSystems: Set<PathRuleFileSystem>) : StoragePathRules {

    /** 경로별 파일 시스템을 고르는 라우터. */
    private val router = FileSystemRouter(fileSystems)

    // 처리할 파일 시스템이 없는 경로는 배선 오류라 라우터가 실패시킨다 — "부모 없음"이나 경로 그대로의 이름으로 덮지 않도록
    override fun getParentPath(path: String): String? = router.resolve(path).getParent(path)
    override fun displayNameOf(path: String): String = router.resolve(path).displayName(path)
}
