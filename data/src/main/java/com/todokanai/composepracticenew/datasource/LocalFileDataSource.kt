package com.todokanai.composepracticenew.datasource

import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.fileexplorer.FileEntry
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import javax.inject.Inject
import javax.inject.Singleton

/**
 * java.io.File API로 로컬 디렉터리의 자식 목록을 읽어 FileEntry 리스트로 반환하는 데이터 소스.
 */
@Singleton
class LocalFileDataSource @Inject constructor() {

    /**
     * [path]의 자식 파일 목록을 읽어 FileEntry 리스트로 반환한다.
     * File.listFiles()는 디렉터리가 아니거나 읽을 수 없을 때 null을 돌려주므로, 이를 빈 폴더와 구분해 실패로 알린다.
     * @param path 읽을 디렉터리의 절대 경로
     * @return FileEntry 리스트. 비어 있으면 실제로 빈 디렉터리다. 경로가 없거나 디렉터리가 아니거나 읽을 권한이 없으면 실패
     */
    fun listFiles(path: String): IoResult<List<FileEntry>> {
        val files = File(path).listFiles() ?: return IoResult.ItemFailed(UNREADABLE_MESSAGE)
        return IoResult.Success(files.map(::entryOf))
    }

    /**
     * [path]가 읽기 가능한 디렉터리인지 확인한다.
     * @param path 확인할 디렉터리의 절대 경로
     * @return 읽기 가능하면 true, 그렇지 않으면 false
     */
    fun canRead(path: String): Boolean = File(path).listFiles() != null

    /**
     * 목록에 담을 [file]의 항목을 만든다. 종류·크기·수정 시각을 속성 한 번 읽기로 채운다(링크는 따라가 대상의 속성).
     * 크기는 일반 파일만 담는다 — File.length()는 디렉터리에서 정해지지 않은 값을, 읽지 못하면(사라진 파일, 끊긴 링크) 0을 돌려줘 실제 크기와 구분되지 않으므로 쓰지 않는다.
     * 속성을 읽지 못한 것은 목록 조회의 실패가 아니라 이 항목의 크기를 모르는 것이므로 크기는 null로 두고(크기가 필요한 쪽이 다시 재어 실패를 알린다),
     * 종류와 수정 시각은 java.io로 읽는다.
     * @param file 항목을 만들 파일
     * @return 목록 항목
     */
    private fun entryOf(file: File): FileEntry {
        val attrs = attributesOf(file)
        return FileEntry(
            name = file.name,
            path = file.absolutePath,
            isDirectory = attrs?.isDirectory ?: file.isDirectory,
            size = attrs?.takeIf { it.isRegularFile }?.size(),
            lastModified = attrs?.lastModifiedTime()?.toMillis() ?: file.lastModified()
        )
    }

    /**
     * [file]의 속성을 읽는다. 링크는 따라가 대상의 속성을 읽는다.
     * @param file 속성을 읽을 항목
     * @return 항목 속성. 읽지 못하면(사라진 파일, 끊긴 링크 등) null
     */
    private fun attributesOf(file: File): BasicFileAttributes? = try {
        Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
    } catch (e: IOException) {
        null
    }

    private companion object {
        /** 디렉터리 목록을 읽지 못했을 때의 실패 메시지. File.listFiles()는 원인을 알려주지 않으므로 가능한 원인을 함께 적는다. */
        const val UNREADABLE_MESSAGE = "디렉터리가 아니거나 읽을 권한이 없습니다"
    }
}
