package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.DestinationConflicts
import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.RenameConflict
import com.todokanai.composepracticenew.operation.FileOperationOrchestrator

/**
 * 목적지 폴더의 확인된 목록으로 이름 충돌을 판정하는 UseCase. 목록을 확인하지 못했으면(조회 중·실패·이전 요청의 목록) 판정하지 않고 거부를 알린다.
 * 확인된 목록을 얻는 일과 거부 알림을 이 클래스에서만 하므로, 호출부가 미확인 목록으로 판정하거나 거부 처리를 빠뜨릴 수 없다.
 * 판정 결과에 판정한 목록의 경로를 담아, 호출부가 판정한 폴더와 다른 폴더에 실행하지 않게 한다.
 * @param orchestrator 거부 오류를 사용자에게 알리는 FileOperationOrchestrator
 * @param unverifiedMessage 목적지 목록을 확인하지 못해 작업을 실행하지 않았다는 오류 메시지를 반환하는 함수
 */
class ConflictCheckUseCase(
    private val orchestrator: FileOperationOrchestrator,
    private val unverifiedMessage: () -> String,
) {

    /**
     * 복사·이동·전송 시 [destination]의 현재 폴더에 같은 이름이 이미 있는 항목을 판정한다.
     * @param items 작업 대상 항목
     * @param destination 목적지 폴더를 탐색 중인 FileNavigatorUseCase
     * @param nameOf 항목에서 파일 이름을 꺼내는 함수
     * @return 판정한 목적지 경로와 충돌 항목. 목적지 목록을 확인하지 못했으면 거부를 알리고 null
     */
    fun <T> findCopyMoveConflicts(items: List<T>, destination: FileNavigatorUseCase, nameOf: (T) -> String): DestinationConflicts<T>? {
        val listing = verifiedOrReject(destination.verifiedListing()) ?: return null
        return DestinationConflicts(listing.request.path, ConflictRules.copyMoveConflicts(items, namesOf(listing.items), nameOf), listing.completeness)
    }

    /**
     * 압축 해제 시 생성될 폴더(확장자를 뗀 이름)가 [destination]의 현재 폴더에 이미 있는 아카이브 항목을 판정한다.
     * @param items 압축 해제 대상 아카이브 항목
     * @param destination 목적지 폴더를 탐색 중인 FileNavigatorUseCase
     * @param nameOf 항목에서 파일 이름(확장자 포함)을 꺼내는 함수
     * @return 판정한 목적지 경로와 충돌 항목. 목적지 목록을 확인하지 못했으면 거부를 알리고 null
     */
    fun <T> findUnzipConflicts(items: List<T>, destination: FileNavigatorUseCase, nameOf: (T) -> String): DestinationConflicts<T>? {
        val listing = verifiedOrReject(destination.verifiedListing()) ?: return null
        return DestinationConflicts(listing.request.path, ConflictRules.unzipConflicts(items, namesOf(listing.items), nameOf), listing.completeness)
    }

    /**
     * [folder]의 현재 폴더에 있는 [targetPath] 항목의 이름을 [newName]으로 바꿀 때 기존 항목과 어떻게 겹치는지 판정한다.
     * 확인된 목록에 [targetPath] 항목이 없으면(이름 변경을 시작한 뒤 다른 폴더로 이동한 경우 등) 그 항목의 폴더 목록이 아니므로 판정하지 않는다.
     * @param targetPath 이름을 바꿀 항목의 경로
     * @param targetIsDirectory 이름을 바꿀 항목이 폴더이면 true
     * @param newName 새 이름
     * @param folder 이름을 바꿀 항목이 있는 폴더를 탐색 중인 FileNavigatorUseCase
     * @param ignoreCase 저장소가 대소문자를 구분하지 않으면 true — 이름을 대소문자 무시로 비교한다
     * @return 겹치는 방식. 그 항목의 폴더 목록을 확인하지 못했으면 거부를 알리고 null
     */
    fun findRenameConflict(
        targetPath: String,
        targetIsDirectory: Boolean,
        newName: String,
        folder: FileNavigatorUseCase,
        ignoreCase: Boolean,
    ): RenameConflict? {
        val listing = verifiedOrReject(listingContaining(folder, targetPath)) ?: return null
        return renameConflictAmong(listing.items, targetPath, targetIsDirectory, newName, ignoreCase)
    }

    /**
     * 이름 입력 중 미리 보기용으로 [findRenameConflict]와 같은 판정을 하되, 목록을 확인하지 못했어도 거부를 알리지 않는다.
     * 입력할 때마다 호출되므로 알리면 같은 오류가 반복 표시된다. 실행 시점의 판정·거부는 [findRenameConflict]가 한다.
     * @param targetPath 이름을 바꿀 항목의 경로
     * @param targetIsDirectory 이름을 바꿀 항목이 폴더이면 true
     * @param newName 입력 중인 새 이름
     * @param folder 이름을 바꿀 항목이 있는 폴더를 탐색 중인 FileNavigatorUseCase
     * @param ignoreCase 저장소가 대소문자를 구분하지 않으면 true
     * @return 겹치는 방식. 그 항목의 폴더 목록을 확인하지 못했으면 null
     */
    fun previewRenameConflict(
        targetPath: String,
        targetIsDirectory: Boolean,
        newName: String,
        folder: FileNavigatorUseCase,
        ignoreCase: Boolean,
    ): RenameConflict? = listingContaining(folder, targetPath)?.let { listing ->
        renameConflictAmong(listing.items, targetPath, targetIsDirectory, newName, ignoreCase)
    }

    /**
     * [folder]의 확인된 목록이 [targetPath] 항목을 담고 있으면 그 목록을 반환한다.
     * @param folder 항목이 있는 폴더를 탐색 중인 FileNavigatorUseCase
     * @param targetPath 목록에 있어야 하는 항목의 경로
     * @return [targetPath]가 들어 있는 확인된 목록. 없으면 null
     */
    private fun listingContaining(folder: FileNavigatorUseCase, targetPath: String): DirectoryListing.Loaded? =
        folder.verifiedListing()?.takeIf { listing -> listing.items.any { it.path == targetPath } }

    /**
     * 확인된 목록이 없으면 판정할 수 없으므로 거부를 알린다.
     * @param listing 확인된 목록. 없으면 null
     * @return [listing] 그대로
     */
    private fun verifiedOrReject(listing: DirectoryListing.Loaded?): DirectoryListing.Loaded? =
        listing ?: null.also { orchestrator.emitError(unverifiedMessage()) }

    /**
     * [siblings] 중 [newName]과 겹치는 항목으로 이름 변경의 처리 방식을 정한다.
     * @param siblings 확인된 폴더 항목
     * @param targetPath 이름을 바꿀 항목의 경로
     * @param targetIsDirectory 이름을 바꿀 항목이 폴더이면 true
     * @param newName 새 이름
     * @param ignoreCase 이름을 대소문자 무시로 비교하면 true
     * @return 겹치는 방식
     */
    private fun renameConflictAmong(
        siblings: List<FileHolderItem>,
        targetPath: String,
        targetIsDirectory: Boolean,
        newName: String,
        ignoreCase: Boolean,
    ): RenameConflict = ConflictRules.renameConflict(
        targetPath = targetPath,
        targetIsDirectory = targetIsDirectory,
        newName = newName,
        siblings = siblings,
        ignoreCase = ignoreCase,
        pathOf = { it.path },
        nameOf = { it.name },
        isDirectoryOf = { it.isDirectory },
    )

    /**
     * 항목들의 이름 집합을 만든다.
     * @param items 이름을 모을 항목
     * @return 이름 집합
     */
    private fun namesOf(items: List<FileHolderItem>): Set<String> = items.mapTo(HashSet()) { it.name }
}
