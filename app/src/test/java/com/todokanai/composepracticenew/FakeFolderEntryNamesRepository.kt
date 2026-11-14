package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.FolderEntryNames
import com.todokanai.composepracticenew.repository.FolderEntryNamesRepository
import kotlinx.coroutines.CompletableDeferred

/**
 * 폴더 안 항목 이름을 정해 둔 값으로 돌려주는 테스트용 FolderEntryNamesRepository. 업로드 전 겹침 확인을 거치는 테스트가 쓴다.
 * @param entries [folderEntryNames]가 돌려줄 폴더별 항목 이름
 */
internal class FakeFolderEntryNamesRepository(var entries: List<FolderEntryNames> = emptyList()) : FolderEntryNamesRepository {

    /** null이 아니면 [folderEntryNames]가 이 값이 완료될 때까지 멈춘다 — 확인이 진행 중인 동안의 동작을 테스트가 재현한다. */
    var hold: CompletableDeferred<Unit>? = null

    override suspend fun folderEntryNames(paths: List<String>): List<FolderEntryNames> {
        hold?.await()
        return entries
    }
}
