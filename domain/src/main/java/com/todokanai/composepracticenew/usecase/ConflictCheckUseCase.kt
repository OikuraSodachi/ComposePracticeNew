package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.DestinationConflicts
import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.FileHolderItem
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.OperationNoticeKind
import com.todokanai.composepracticenew.model.RenameConflict
import com.todokanai.composepracticenew.model.valueOr
import com.todokanai.composepracticenew.repository.OperationNoticeReporter
import com.todokanai.composepracticenew.repository.StorageNameMatching

/**
 * 목적지 폴더의 확인된 목록으로 이름 충돌을 판정하는 UseCase. 목록을 확인하지 못했으면(조회 중·실패·이전 요청의 목록) 판정하지 않고 거부를 알린다.
 * 확인된 목록을 얻는 일과 거부 알림을 이 클래스에서만 하므로, 호출부가 미확인 목록으로 판정하거나 거부 처리를 빠뜨릴 수 없다.
 * 판정 결과에 판정한 목록의 경로를 담아, 호출부가 판정한 폴더와 다른 폴더에 실행하지 않게 한다.
 * 이름은 그 폴더 저장소의 비교 규칙([StorageNameMatching])으로만 비교하므로, 모든 판정이 저장소가 같은 항목으로 다룰 수 있는 이름을 겹침으로 본다.
 * @param notices 거부 메시지를 화면에 알리도록 맡기는 OperationNoticeReporter
 * @param nameMatching 이름이 놓인 폴더의 저장소가 같은 항목으로 다룰 수 있는 이름을 가려내는 StorageNameMatching
 * @param unverifiedMessage 목적지 목록을 확인하지 못해 작업을 실행하지 않았다는 오류 메시지를 반환하는 함수
 */
class ConflictCheckUseCase(
    private val notices: OperationNoticeReporter,
    private val nameMatching: StorageNameMatching,
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
        return DestinationConflicts(listing.request.path, ConflictRules.copyMoveConflicts(items, namesOf(listing.items), nameOf, matchKeyIn(listing)))
    }

    /**
     * 압축 해제가 목적지 바로 아래에 만들 이름이 [destination]의 현재 폴더에 이미 있는 아카이브 항목을 판정한다.
     * 목적지 목록은 아카이브를 읽기 전에 정한다 — 확인을 요청한 시점의 폴더에 대해 판정하고 실행하도록.
     * @param items 압축 해제 대상 아카이브 항목
     * @param destination 목적지 폴더를 탐색 중인 FileNavigatorUseCase
     * @param nameOf 항목에서 표시할 파일 이름을 꺼내는 함수 — 아카이브를 읽지 못했을 때의 거부 메시지에 쓴다
     * @param extractedNamesOf 항목과 목적지 경로를 받아, 그 항목을 압축 해제할 때 목적지 바로 아래에 생기는 이름 집합을 구하는 함수(배치 방식은 이 함수가 따른다)
     * @return 판정한 목적지 경로와 충돌 항목. 목적지 목록을 확인하지 못했거나 아카이브를 읽지 못했으면 거부를 알리고 null
     */
    suspend fun <T> findUnzipConflicts(
        items: List<T>,
        destination: FileNavigatorUseCase,
        nameOf: (T) -> String,
        extractedNamesOf: suspend (item: T, destinationPath: String) -> IoResult<Set<String>>,
    ): DestinationConflicts<T>? {
        val listing = verifiedOrReject(destination.verifiedListing()) ?: return null
        val extracted = items.associateWith { item ->
            extractedNamesOf(item, listing.request.path).valueOr { failure -> return null.also { reportUnreadableArchive(nameOf(item), failure) } }
        }
        return DestinationConflicts(
            listing.request.path,
            ConflictRules.unzipConflicts(items, namesOf(listing.items), extracted::getValue, matchKeyIn(listing))
        )
    }

    /**
     * 아카이브를 읽지 못해 충돌을 판정할 수 없으므로 거부를 알린다.
     * @param archiveName 읽지 못한 아카이브의 표시 이름
     * @param failure 읽기 실패
     */
    private fun reportUnreadableArchive(archiveName: String, failure: IoResult.Failure) {
        val cause = failure.message?.let { " ($it)" }.orEmpty()
        notices.report(OperationNoticeKind.FAILURE, "압축 해제 실패: ${archiveName}의 내용을 읽지 못해 충돌을 확인할 수 없습니다$cause")
    }

    /**
     * [folder]의 현재 폴더에 있는 [targetPath] 항목의 이름을 [newName]으로 바꿀 때 기존 항목과 어떻게 겹치는지 판정한다.
     * 확인된 목록에 [targetPath] 항목이 없으면(이름 변경을 시작한 뒤 다른 폴더로 이동한 경우 등) 그 항목의 폴더 목록이 아니므로 판정하지 않는다.
     * @param targetPath 이름을 바꿀 항목의 경로
     * @param targetIsDirectory 이름을 바꿀 항목이 폴더이면 true
     * @param newName 새 이름
     * @param folder 이름을 바꿀 항목이 있는 폴더를 탐색 중인 FileNavigatorUseCase
     * @return 겹치는 방식. 그 항목의 폴더 목록을 확인하지 못했으면 거부를 알리고 null
     */
    fun findRenameConflict(
        targetPath: String,
        targetIsDirectory: Boolean,
        newName: String,
        folder: FileNavigatorUseCase,
    ): RenameConflict? {
        val listing = verifiedOrReject(listingContaining(folder, targetPath)) ?: return null
        return renameConflictAmong(listing, targetPath, targetIsDirectory, newName)
    }

    /**
     * 이름 입력 중 미리 보기용으로 [findRenameConflict]와 같은 판정을 하되, 목록을 확인하지 못했어도 거부를 알리지 않는다.
     * 입력할 때마다 호출되므로 알리면 같은 오류가 반복 표시된다. 실행 시점의 판정·거부는 [findRenameConflict]가 한다.
     * @param targetPath 이름을 바꿀 항목의 경로
     * @param targetIsDirectory 이름을 바꿀 항목이 폴더이면 true
     * @param newName 입력 중인 새 이름
     * @param folder 이름을 바꿀 항목이 있는 폴더를 탐색 중인 FileNavigatorUseCase
     * @return 겹치는 방식. 그 항목의 폴더 목록을 확인하지 못했으면 null
     */
    fun previewRenameConflict(
        targetPath: String,
        targetIsDirectory: Boolean,
        newName: String,
        folder: FileNavigatorUseCase,
    ): RenameConflict? = listingContaining(folder, targetPath)?.let { listing ->
        renameConflictAmong(listing, targetPath, targetIsDirectory, newName)
    }

    /**
     * [folder]의 현재 폴더에 새로 만들 항목의 기본 이름을 제안한다. 같은 이름이 있으면 `이름 (n)` 형태로 겹치지 않는 이름을 고른다.
     * 입력란 기본값용이라 목록을 확인하지 못했어도 거부를 알리지 않고 번호 없이 제안한다. 실행 시점의 겹침 처리는 각 작업이 한다.
     * @param baseName 확장자를 뺀 원하는 이름
     * @param extension 점을 포함한 확장자(없으면 빈 문자열)
     * @param folder 항목을 만들 폴더를 탐색 중인 FileNavigatorUseCase
     * @return 제안할 이름(확장자 제외)
     */
    fun suggestNewEntryName(baseName: String, extension: String, folder: FileNavigatorUseCase): String =
        freeBaseName(folder.verifiedListing(), baseName, extension)

    /**
     * [siblingPath] 항목이 있는 폴더에 새로 만들 항목(예: 그 항목들의 압축 파일)의 기본 이름을 제안한다.
     * 확인된 목록에 [siblingPath]가 없으면 그 폴더의 목록이 아니므로 번호 없이 제안한다. 거부는 알리지 않는다.
     * @param siblingPath 새 항목과 같은 폴더에 있는 항목의 경로
     * @param baseName 확장자를 뺀 원하는 이름
     * @param extension 점을 포함한 확장자(없으면 빈 문자열)
     * @param folder [siblingPath]가 있는 폴더를 탐색 중인 FileNavigatorUseCase
     * @return 제안할 이름(확장자 제외)
     */
    fun suggestNameBeside(siblingPath: String, baseName: String, extension: String, folder: FileNavigatorUseCase): String =
        freeBaseName(listingContaining(folder, siblingPath), baseName, extension)

    /**
     * [listing]의 항목과 겹치지 않는 기본 이름을 고른다.
     * @param listing 확인된 목록. 없으면 겹침을 알 수 없으므로 [baseName] 그대로
     * @param baseName 확장자를 뺀 원하는 이름
     * @param extension 점을 포함한 확장자(없으면 빈 문자열)
     * @return 겹치지 않는 이름(확장자 제외)
     */
    private fun freeBaseName(listing: DirectoryListing.Loaded?, baseName: String, extension: String): String =
        listing?.let { ConflictRules.firstFreeBaseName(baseName, extension, namesOf(it.items), matchKeyIn(it)) } ?: baseName

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
        listing ?: null.also { notices.report(OperationNoticeKind.FAILURE, unverifiedMessage()) }

    /**
     * [listing]의 항목 중 [newName]과 겹치는 항목으로 이름 변경의 처리 방식을 정한다.
     * @param listing 이름을 바꿀 항목이 있는 폴더의 확인된 목록
     * @param targetPath 이름을 바꿀 항목의 경로
     * @param targetIsDirectory 이름을 바꿀 항목이 폴더이면 true
     * @param newName 새 이름
     * @return 겹치는 방식
     */
    private fun renameConflictAmong(
        listing: DirectoryListing.Loaded,
        targetPath: String,
        targetIsDirectory: Boolean,
        newName: String,
    ): RenameConflict = ConflictRules.renameConflict(
        targetPath = targetPath,
        targetIsDirectory = targetIsDirectory,
        newName = newName,
        siblings = listing.items,
        matchKeyOf = matchKeyIn(listing),
        pathOf = { it.path },
        nameOf = { it.name },
        isDirectoryOf = { it.isDirectory },
    )

    /**
     * [listing] 폴더의 저장소 규칙으로 이름의 비교 값을 구하는 함수를 만든다.
     * @param listing 이름이 놓인 폴더의 확인된 목록
     * @return 이름을 받아 비교 값을 돌려주는 함수
     */
    private fun matchKeyIn(listing: DirectoryListing.Loaded): (String) -> String =
        { name -> nameMatching.matchKeyOf(name, listing.request.path) }

    /**
     * 항목들의 이름 목록을 만든다.
     * @param items 이름을 모을 항목
     * @return 이름 목록
     */
    private fun namesOf(items: List<FileHolderItem>): List<String> = items.map { it.name }
}
