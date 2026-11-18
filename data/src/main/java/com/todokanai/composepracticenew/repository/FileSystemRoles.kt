package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.model.IoResult

/**
 * 경로 종류별 파일 시스템 역할의 공통 계약: 어느 경로를 맡는지 판별한다.
 * 역할마다 구현은 Hilt `@IntoSet`으로 등록되며 순서가 보장되지 않으므로, 같은 역할 집합 안의 [handles]는 서로 배타적이어야 한다.
 * 역할은 쓰는 곳마다 나뉜다(#442) — 한 역할만 바꾸거나(로컬 목록을 SAF로 등) 가짜로 만들 때 다른 역할을 함께 구현하지 않도록.
 */
interface PathHandlingFileSystem {
    /**
     * 이 파일 시스템이 path를 처리하는지 판별한다.
     * @param path 판별할 경로
     * @return 처리 가능하면 true
     */
    fun handles(path: String): Boolean
}

/** 경로의 목록을 조회하고 이동할 경로를 정하는 역할. FileExplorerRepositoryImpl이 스토리지 타입별 분기 없이 위임한다. */
interface NavigableFileSystem : PathHandlingFileSystem {
    /**
     * path 디렉터리의 파일 목록을 조회한다. 조회 실패는 빈 목록이 아니라 실패 결과로 돌려준다.
     * @param path 조회할 디렉터리 경로
     * @return 읽은 항목과 모두 읽었는지를 담은 결과. 항목이 비어 있으면 저장소가 드러내는 항목이 없는 디렉터리다([com.todokanai.composepracticenew.model.DirectoryListing.Loaded]의 알려진 한계 참고)
     */
    suspend fun listFiles(path: String): IoResult<ListedDirectory>

    /**
     * path로 이동할 때 실제로 navigateTo에 전달할 정규화 경로를 반환한다.
     * 이동 불가능한 경우 null을 반환한다.
     * 원격 파일 시스템은 접근 권한을 이유로 null을 반환하지 않고 권한 문제를 [listFiles] 결과로 알린다 —
     * 연결 직후 루트 이동이 거부되면 [FtpRootNavigator]가 배선 오류로 실패하기 때문이다.
     * @param path 이동할 경로
     * @return 정규화된 경로 또는 null
     */
    suspend fun resolveNavigationPath(path: String): String?
}

/** 경로의 부모와 표시 이름을 정하는 역할. FileSystemPathRules가 쓴다. */
interface PathRuleFileSystem : PathHandlingFileSystem {
    /**
     * path의 부모 경로를 반환한다. 루트이면 null을 반환한다.
     * @param path 부모를 조회할 경로
     * @return 부모 경로 또는 null
     */
    fun getParent(path: String): String?

    /**
     * path의 표시 이름을 반환한다.
     * @param path 이름을 구할 경로
     * @return 경로 표시줄에 보여 줄 이름
     */
    fun displayName(path: String): String
}

/** 이 파일 시스템이 받지 않는 이름 규칙을 판정하는 역할. FileSystemNameRules가 쓴다. */
interface NameRuleFileSystem : PathHandlingFileSystem {
    /**
     * [fullName]을 [location]에 쓸 때 이 파일 시스템이 받지 않는 이름 규칙을 구한다.
     * @param fullName 저장될 전체 이름(덧붙는 확장자 포함)
     * @param location 이름이 놓일 경로 — 새 항목이면 만들 폴더, 이름 변경이면 그 항목 자신. 같은 종류 안에서도 볼륨마다 제약이 다를 때 고르는 데 쓴다
     * @return 위반한 규칙의 집합. 비어 있으면 이 파일 시스템의 제약을 지킨다
     */
    fun nameViolationsOf(fullName: String, location: String): Set<FileNameError>
}

/** 두 이름을 이 파일 시스템이 같은 항목으로 다룰 수 있는지 비교할 값을 구하는 역할. FileSystemNameMatching이 쓴다. */
interface NameMatchFileSystem : PathHandlingFileSystem {
    /**
     * [location]에 놓인 [name]을 이 파일 시스템이 다른 이름과 같은 항목으로 다룰 수 있는지 비교할 값을 구한다.
     * @param name 비교할 항목 이름
     * @param location 이름이 놓인 폴더의 경로 — 같은 종류 안에서도 볼륨마다 규칙이 다를 때 고르는 데 쓴다
     * @return 비교 값. 두 이름의 값이 같으면 같은 항목으로 다뤄질 수 있다
     */
    fun nameMatchKeyOf(name: String, location: String): String
}
