package com.todokanai.composepracticenew.datasource

import com.todokanai.fileexplorer.FileEntry
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * java.io.File API로 로컬 디렉터리의 자식 목록을 읽어 FileEntry 리스트로 반환하는 데이터 소스.
 */
@Singleton
class LocalFileDataSource @Inject constructor() {

    /**
     * [path]의 자식 파일 목록을 읽어 FileEntry 리스트로 반환한다.
     * @param path 읽을 디렉터리의 절대 경로
     * @return FileEntry 리스트; 경로가 없거나 읽기 실패 시 emptyList()
     */
    fun listFiles(path: String): List<FileEntry> =
        File(path).listFiles()?.map { file ->
            FileEntry(
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                size = file.length(),
                lastModified = file.lastModified()
            )
        } ?: emptyList()
}
