package com.todokanai.composepracticenew.repository

/**
 * 주입된 PathAwareFileSystem 집합에서 경로를 처리하는 파일 시스템의 규칙으로 부모 경로와 표시 이름을 정하는 StoragePathRules 구현체.
 * 화면마다 다루는 스토리지는 넘기는 집합으로 정하며, 같은 화면의 FileExplorerRepositoryImpl과 같은 집합을 넘긴다.
 * @param fileSystems 이 규칙이 다루는 경로 종류별 PathAwareFileSystem 집합 — 각 handles()는 서로 배타적이다
 */
class FileSystemPathRules(fileSystems: Set<PathAwareFileSystem>) : StoragePathRules {

    /** 경로별 파일 시스템을 고르는 라우터. */
    private val router = FileSystemRouter(fileSystems)

    override fun getParentPath(path: String): String? = router.resolve(path)?.getParent(path)
    // 처리할 파일 시스템이 없는 경로는 경로 전체를 이름으로 보여 준다(목록 조회는 실패로 알린다)
    override fun displayNameOf(path: String): String = router.resolve(path)?.displayName(path) ?: path
}
