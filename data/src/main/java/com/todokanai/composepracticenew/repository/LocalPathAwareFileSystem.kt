package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.datasource.LocalFileDataSource
import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.fileexplorer.FileEntry
import com.todokanai.fileexplorer.defaultDisplayName
import com.todokanai.fileexplorer.defaultParentPath
import javax.inject.Inject

/**
 * URI 스킴이 없는 로컬 파일 시스템 경로를 처리하는 PathAwareFileSystem 구현체.
 * 다른 스토리지의 존재를 알지 않고 자기 경로 형식만으로 판정하므로 등록 순서와 무관하다.
 * @param localFiles 로컬 디렉터리 읽기를 담당하는 LocalFileDataSource
 */
class LocalPathAwareFileSystem @Inject constructor(
    private val localFiles: LocalFileDataSource
) : PathAwareFileSystem {
    override fun handles(path: String): Boolean = !path.contains(SCHEME_SEPARATOR)
    override suspend fun listFiles(path: String): IoResult<List<FileEntry>> = localFiles.listFiles(path)
    override fun getParent(path: String): String? = defaultParentPath(path)
    override fun displayName(path: String): String = defaultDisplayName(path)
    // 볼륨의 파일 시스템(FAT 등)을 구분하지 않으므로 모든 로컬 경로에 주요 파일시스템의 길이 한도만 적용한다
    override fun nameViolationsOf(fullName: String, location: String): Set<FileNameError> = nameByteLimitViolations(fullName)
    override suspend fun resolveNavigationPath(path: String): String? =
        if (localFiles.canRead(path)) path else null

    private companion object {
        /** URI 스킴 구분자. 로컬 절대 경로에는 나타나지 않는다. */
        const val SCHEME_SEPARATOR = "://"
    }
}
