package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.ArchiveContents
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.repository.ArchiveContentRepository
import com.todokanai.composepracticenew.repository.ArchivePasswordProvider

/**
 * 로컬 압축 파일의 내용 보기를 맡는다.
 * @param repository 압축 파일의 항목 목록을 읽는 ArchiveContentRepository
 */
class ArchiveViewUseCase(private val repository: ArchiveContentRepository) {

    /**
     * [item]을 누를 때 내용 보기로 열지 판정한다.
     * @param item 로컬 목록의 항목
     * @return 폴더가 아니고 내용을 볼 수 있는 압축 파일(단일 아카이브·시작 볼륨)이면 true
     */
    fun canView(item: FileHolderItem): Boolean = !item.isDirectory && repository.canView(item.path)

    /**
     * [archivePath]의 항목 목록을 폴더 트리로 읽는다.
     * @param archivePath 압축 파일 경로
     * @param askPassword 항목 목록까지 암호화한 압축 파일의 비밀번호를 묻는 통로
     * @return 루트 폴더와 그 목록의 식별값, 읽지 못하면 실패
     */
    suspend fun contentsOf(archivePath: String, askPassword: ArchivePasswordProvider): IoResult<ArchiveContents> =
        repository.contentsOf(archivePath, askPassword)
}
