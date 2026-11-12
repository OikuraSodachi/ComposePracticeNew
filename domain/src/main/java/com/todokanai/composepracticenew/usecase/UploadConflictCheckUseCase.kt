package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.NameCollision
import com.todokanai.composepracticenew.model.UploadConflicts
import com.todokanai.composepracticenew.repository.FileInfoRepository
import com.todokanai.composepracticenew.repository.StorageNameMatching

/**
 * 업로드 전에 사용자에게 확인할 겹침을 모으는 UseCase.
 * 목적지에 이미 있는 이름과의 겹침은 [ConflictCheckUseCase]가 확인된 목록으로 판정하고, 함께 올리는 항목끼리의 겹침은 올릴 이름(폴더 안쪽 포함)을
 * 목적지 저장소의 비교 규칙으로 묶어 찾는다 — 원본에서는 다른 이름이어도 목적지에서 같은 항목이 되면 뒤에 올린 항목이 앞의 항목을 덮어쓰므로.
 * 비교 규칙은 같은 항목이 될 수 있는 이름을 묶을 뿐이며, 목적지가 실제로 두 이름을 합치는지는 판정하지 않는다(지원 범위 밖).
 * @param conflictCheck 목적지의 확인된 목록으로 이미 있는 이름과의 겹침을 판정하는 ConflictCheckUseCase
 * @param sourceInfo 올릴 폴더 안의 항목 이름을 읽는, 업로드 원본 저장소의 FileInfoRepository
 * @param nameMatching 목적지 저장소가 같은 항목으로 다룰 수 있는 이름을 가려내는 StorageNameMatching
 */
class UploadConflictCheckUseCase(
    private val conflictCheck: ConflictCheckUseCase,
    private val sourceInfo: FileInfoRepository,
    private val nameMatching: StorageNameMatching,
) {

    /**
     * [items]를 [destination]의 현재 폴더에 올릴 때 확인할 겹침을 모은다.
     * 폴더 안쪽 이름도 목적지 폴더의 규칙으로 비교한다 — 올린 폴더는 목적지 폴더와 같은 저장소 안에 놓인다.
     * @param items 업로드 대상 항목
     * @param destination 목적지 폴더를 탐색 중인 FileNavigatorUseCase
     * @param nameOf 항목의 이름을 꺼내는 함수
     * @param pathOf 항목의 원본 경로를 꺼내는 함수
     * @return 판정한 목적지 경로와 겹침. 목적지 목록을 확인하지 못했으면 거부를 알리고 null
     */
    suspend fun <T> findConflicts(
        items: List<T>,
        destination: FileNavigatorUseCase,
        nameOf: (T) -> String,
        pathOf: (T) -> String,
    ): UploadConflicts<T>? {
        val checked = conflictCheck.findCopyMoveConflicts(items, destination, nameOf) ?: return null
        val matchKeyOf = { name: String -> nameMatching.matchKeyOf(name, checked.destinationPath) }
        return UploadConflicts(checked.destinationPath, checked.conflicts, collisionsAmong(items.map(nameOf), items.map(pathOf), matchKeyOf))
    }

    /**
     * 함께 올리는 이름 중 목적지에서 같은 항목이 될 수 있는 묶음을 찾는다. 처음 선택한 항목끼리를 먼저, 폴더 안쪽을 그다음에 둔다.
     * @param names 처음 선택한 항목의 이름
     * @param paths 처음 선택한 항목의 원본 경로
     * @param matchKeyOf 이름의 비교 값을 구하는 함수(목적지 저장소의 규칙)
     * @return 겹칠 수 있는 이름 묶음
     */
    private suspend fun collisionsAmong(names: List<String>, paths: List<String>, matchKeyOf: (String) -> String): List<NameCollision> {
        val topLevel = ConflictRules.sameItemNameGroups(names, matchKeyOf).map { NameCollision(folderLabel = null, names = it) }
        val inner = sourceInfo.folderEntryNames(paths).flatMap { folder ->
            ConflictRules.sameItemNameGroups(folder.names, matchKeyOf).map { NameCollision(folder.folderLabel, it) }
        }
        return topLevel + inner
    }
}
