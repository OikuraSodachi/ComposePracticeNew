package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.ArchiveFolder
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.repository.ArchiveContentRepository
import com.todokanai.composepracticenew.repository.ArchivePasswordProvider

/**
 * [viewable]에 든 경로만 내용을 볼 수 있는 압축 파일로 보는 ArchiveContentRepository. 목록은 [contents]로 정하고, 읽은 경로를 [reads]에 남긴다.
 */
internal class ViewableArchives : ArchiveContentRepository {
    /** 내용을 볼 수 있는 압축 파일 경로. */
    val viewable = mutableSetOf<String>()

    /** [contentsOf]가 돌려줄 결과. 비밀번호 통로를 받아 물을 수 있다. */
    var contents: suspend (ArchivePasswordProvider) -> IoResult<ArchiveFolder> = { IoResult.Success(ArchiveFolder(emptyList())) }

    /** 읽은 압축 파일 경로(순서대로). */
    val reads = mutableListOf<String>()

    override fun canView(path: String): Boolean = path in viewable

    override suspend fun contentsOf(archivePath: String, askPassword: ArchivePasswordProvider): IoResult<ArchiveFolder> {
        reads += archivePath
        return contents(askPassword)
    }
}
