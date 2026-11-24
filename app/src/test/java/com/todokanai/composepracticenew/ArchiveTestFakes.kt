package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.ArchiveContents
import com.todokanai.composepracticenew.model.ArchiveEntryKey
import com.todokanai.composepracticenew.model.ArchiveEntrySelection
import com.todokanai.composepracticenew.model.ArchiveFolder
import com.todokanai.composepracticenew.model.ArchiveListingId
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ProgressPhase
import com.todokanai.composepracticenew.repository.ArchiveContentRepository
import com.todokanai.composepracticenew.repository.ArchiveEntryCopyRepository
import com.todokanai.composepracticenew.repository.ArchivePasswordProvider
import com.todokanai.composepracticenew.repository.WriteConflictResolver

/** [ViewableArchives]가 읽은 목록마다 붙이는 식별값. */
internal val TEST_LISTING = ArchiveListingId("test-listing")

/** 압축 파일 항목 복사 모드를 만드는 데 쓰는 고른 항목. */
internal val archiveSelection = ArchiveEntrySelection("/storage/a.zip", TEST_LISTING, emptyList(), setOf(ArchiveEntryKey.Item(0)))

/**
 * 압축 파일 항목 복사 요청을 기록하고 바로 성공하는 ArchiveEntryCopyRepository.
 */
internal class RecordingArchiveCopyRepository : ArchiveEntryCopyRepository {
    /** 받은 요청(고른 항목, 목적지)을 순서대로. */
    val calls = mutableListOf<Pair<ArchiveEntrySelection, String>>()

    override suspend fun extractEntries(
        selection: ArchiveEntrySelection,
        destPath: String,
        resolveConflict: WriteConflictResolver,
        askPassword: ArchivePasswordProvider,
        onProgress: (ProgressPhase) -> Unit
    ): IoResult<Unit> {
        calls += selection to destPath
        return IoResult.Done
    }
}

/**
 * [viewable]에 든 경로만 내용을 볼 수 있는 압축 파일로 보는 ArchiveContentRepository. 목록은 [contents]로 정하고(식별값은 [TEST_LISTING]), 읽은 경로를 [reads]에 남긴다.
 */
internal class ViewableArchives : ArchiveContentRepository {
    /** 내용을 볼 수 있는 압축 파일 경로. */
    val viewable = mutableSetOf<String>()

    /** [contentsOf]가 돌려줄 루트 폴더 결과. 비밀번호 통로를 받아 물을 수 있다. */
    var contents: suspend (ArchivePasswordProvider) -> IoResult<ArchiveFolder> = { IoResult.Success(ArchiveFolder(emptyList())) }

    /** 읽은 압축 파일 경로(순서대로). */
    val reads = mutableListOf<String>()

    override fun canView(path: String): Boolean = path in viewable

    override suspend fun contentsOf(archivePath: String, askPassword: ArchivePasswordProvider): IoResult<ArchiveContents> {
        reads += archivePath
        return when (val result = contents(askPassword)) {
            is IoResult.Success -> IoResult.Success(ArchiveContents(result.value, TEST_LISTING))
            is IoResult.Failure -> result
        }
    }
}
