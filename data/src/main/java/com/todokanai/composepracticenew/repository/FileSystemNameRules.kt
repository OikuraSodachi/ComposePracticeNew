package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.FileNameError

/**
 * 주입된 PathAwareFileSystem 집합에서 이름이 놓일 위치를 처리하는 파일 시스템의 제약으로 이름을 판정하는 StorageNameRules 구현체.
 * 화면마다 다루는 스토리지는 넘기는 집합으로 정하며, 같은 화면의 FileSystemPathRules와 같은 집합을 넘긴다.
 * @param fileSystems 이 규칙이 다루는 경로 종류별 PathAwareFileSystem 집합 — 각 handles()는 서로 배타적이다
 */
class FileSystemNameRules(fileSystems: Set<PathAwareFileSystem>) : StorageNameRules {

    /** 경로별 파일 시스템을 고르는 라우터. */
    private val router = FileSystemRouter(fileSystems)

    // 처리할 파일 시스템이 없는 위치는 판정할 제약이 없다 — 그 위치의 작업은 저장소가 실패로 알린다
    override fun violationsOf(fullName: String, location: String): Set<FileNameError> =
        router.resolve(location)?.nameViolationsOf(fullName, location) ?: emptySet()
}
