package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FolderEntryNames
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ItemAttributes
import com.todokanai.composepracticenew.model.SizeMeasurement
import com.todokanai.composepracticenew.repository.FileInfoRepository
import kotlinx.coroutines.CompletableDeferred

/**
 * 폴더 안 항목 이름만 정해 둔 값으로 돌려주는 테스트용 FileInfoRepository. 업로드 전 겹침 확인을 거치는 테스트가 쓰며, 다른 정보 읽기는 쓰지 않는다.
 * @param entries [folderEntryNames]가 돌려줄 폴더별 항목 이름
 */
internal class FolderEntriesInfoRepository(var entries: List<FolderEntryNames> = emptyList()) : FileInfoRepository {

    /** null이 아니면 [folderEntryNames]가 이 값이 완료될 때까지 멈춘다 — 확인이 진행 중인 동안의 동작을 테스트가 재현한다. */
    var hold: CompletableDeferred<Unit>? = null


    override fun locationOf(path: String): String? = error("쓰지 않음")

    override suspend fun readAttributes(path: String): IoResult<ItemAttributes> = error("쓰지 않음")

    override suspend fun measureSize(paths: List<String>): IoResult<SizeMeasurement> = error("쓰지 않음")

    override suspend fun countChildren(path: String): IoResult<Int> = error("쓰지 않음")

    override suspend fun folderEntryNames(paths: List<String>): List<FolderEntryNames> {
        hold?.await()
        return entries
    }
}
