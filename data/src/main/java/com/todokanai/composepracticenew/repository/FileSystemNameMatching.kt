package com.todokanai.composepracticenew.repository

/**
 * 주입된 PathAwareFileSystem 집합에서 이름이 놓인 위치를 처리하는 파일 시스템의 비교 규칙으로 이름의 비교 값을 구하는 StorageNameMatching 구현체.
 * @param fileSystems 이 규칙이 다루는 경로 종류별 PathAwareFileSystem 집합 — 각 handles()는 서로 배타적이다
 */
class FileSystemNameMatching(fileSystems: Set<PathAwareFileSystem>) : StorageNameMatching {

    /** 경로별 파일 시스템을 고르는 라우터. */
    private val router = FileSystemRouter(fileSystems)

    // 충돌 판정은 탐색이 조회한 목록의 폴더에서만 물으므로, 처리할 파일 시스템이 없는 위치는 배선 오류다
    override fun matchKeyOf(name: String, location: String): String =
        checkNotNull(router.find(location)) { "No file system handles location: $location" }.nameMatchKeyOf(name, location)
}
