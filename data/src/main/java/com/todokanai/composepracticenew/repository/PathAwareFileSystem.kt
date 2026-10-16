package com.todokanai.composepracticenew.repository

import com.todokanai.fileexplorer.FileEntry

/**
 * 경로 종류에 따라 파일 목록 조회·부모 계산·이동을 처리하는 파일 시스템 추상화.
 * FileExplorerRepositoryImpl이 스토리지 타입별 분기 없이 위임하기 위해 사용한다.
 */
interface PathAwareFileSystem {
    /**
     * 이 파일 시스템이 path를 처리하는지 판별한다.
     * @param path 판별할 경로
     * @return 처리 가능하면 true
     */
    fun handles(path: String): Boolean

    /**
     * path 디렉터리의 파일 목록을 반환한다.
     * @param path 조회할 디렉터리 경로
     * @return FileEntry 리스트
     */
    suspend fun listFiles(path: String): List<FileEntry>

    /**
     * path의 부모 경로를 반환한다. 루트이면 null을 반환한다.
     * @param path 부모를 조회할 경로
     * @return 부모 경로 또는 null
     */
    fun getParent(path: String): String?

    /**
     * path로 이동할 때 실제로 navigateTo에 전달할 정규화 경로를 반환한다.
     * 이동 불가능한 경우 null을 반환한다.
     * @param path 이동할 경로
     * @return 정규화된 경로 또는 null
     */
    suspend fun resolveNavigationPath(path: String): String?
}
