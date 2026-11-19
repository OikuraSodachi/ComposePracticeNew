package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.model.ArchiveFormat
import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.model.FileNameParts
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.usecase.ConflictCheckUseCase
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase

/**
 * 압축 다이얼로그가 만들 압축 파일의 형식·위치·이름(기본 이름 제안·검증)을 정한다. 로컬 목록 ViewModel이 구성(composition)으로 보유한다(#450).
 * 화면이 쓰는 멤버는 ViewModel이 다시 노출하지 않고 public으로 둔다. 압축 실행은 ViewModel이 [format]과 함께 한다.
 * @param validateArchiveName 이름(확장자 제외)·만들 폴더·형식을 받아 저장소의 이름 규칙 위반 집합을 반환하는 함수
 * @param conflictCheckUseCase 같은 이름 항목과 겹치지 않는 이름을 제안하는 UseCase
 * @param folder 압축할 항목이 있는 화면의 탐색 UseCase — 부모 폴더·표시 이름 규칙과 겹침을 볼 목록을 준다
 */
class ArchiveNaming(
    private val validateArchiveName: (name: String, folder: String, format: ArchiveFormat) -> Set<FileNameError>,
    private val conflictCheckUseCase: ConflictCheckUseCase,
    private val folder: FileNavigatorUseCase,
) {

    /** 압축 다이얼로그가 만드는 아카이브 형식. 형식을 고르는 화면이 없으므로 ZIP 하나다. */
    val format = ArchiveFormat.ZIP

    /**
     * 압축 파일을 만들 폴더를 정한다. 압축 파일은 첫 항목 옆에 만든다. 다이얼로그를 열 때 한 번 정해 [validateName]과 압축 실행에 함께 넘긴다.
     * @param selectedList 압축할 항목
     * @return 첫 항목의 부모 폴더. 항목이 없거나 부모를 정할 수 없으면 null(화면은 압축 메뉴를 두지 않는다)
     */
    fun folderOf(selectedList: List<FileHolderItem>): String? =
        selectedList.firstOrNull()?.path?.let(folder::parentOf)

    /**
     * [targetFolder]에 만들 압축 파일 이름([name]+[format]의 확장자)을 검증한다. 실행 시점의 강제는 UseCase가 한다.
     * @param targetFolder 다이얼로그를 연 때 [folderOf]로 정한 폴더 — 압축 실행에도 같은 값이 넘어간다
     * @param name 사용자가 입력한 압축 파일 이름(확장자 제외)
     * @return 위반한 규칙의 집합. 비어 있으면 유효하다
     */
    fun validateName(targetFolder: String, name: String): Set<FileNameError> =
        validateArchiveName(name, targetFolder, format)

    /**
     * 압축 다이얼로그에 미리 채울 이름(확장자 제외). 같은 이름의 아카이브가 있으면 `이름 (n)`으로 겹치지 않게 고른다.
     * @param selectedList 압축할 항목
     * @return 하나면 그 항목의 이름(파일은 확장자 제외), 여러 개면 항목들이 있는 폴더의 이름
     */
    fun suggestName(selectedList: List<FileHolderItem>): String {
        val first = selectedList.firstOrNull() ?: return ""
        return conflictCheckUseCase.suggestNameBeside(
            siblingPath = first.path,
            baseName = baseNameOf(selectedList, first),
            extension = format.extension,
            folder = folder,
        )
    }

    /**
     * 압축 파일의 기본 이름을 정한다. 항목들이 있는 폴더가 파일시스템 루트라 이름이 없으면 첫 항목의 이름을 쓴다.
     * @param selectedList 압축할 항목
     * @param first [selectedList]의 첫 항목
     * @return 확장자를 뺀 기본 이름
     */
    private fun baseNameOf(selectedList: List<FileHolderItem>, first: FileHolderItem): String {
        val folderName = folder.parentOf(first.path)?.let(folder::displayNameOf).orEmpty()
        return if (selectedList.size > 1 && folderName.isNotEmpty()) folderName else nameWithoutExtension(first)
    }

    /**
     * 항목의 이름에서 확장자를 뗀다. 폴더 이름의 점은 확장자가 아니므로 그대로 둔다.
     * @param item 이름을 꺼낼 항목
     * @return 파일은 확장자를 뺀 이름, 폴더는 이름 그대로
     */
    private fun nameWithoutExtension(item: FileHolderItem): String =
        if (item.isDirectory) item.name else item.name.substring(0, FileNameParts.baseNameEnd(item.name))
}
