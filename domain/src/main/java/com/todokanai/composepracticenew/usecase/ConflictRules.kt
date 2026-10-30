package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.FileNameParts
import com.todokanai.composepracticenew.model.RenameConflict

/**
 * 목적지의 기존 항목과 작업 대상의 이름이 겹치는지 판정하는 순수 규칙.
 * 목록이 확인된 것인지는 따지지 않으므로, 확인된 목록만 넘기는 [ConflictCheckUseCase]를 거쳐서만 쓴다.
 */
internal object ConflictRules {

    /**
     * 복사·이동·전송 시 목적지에 같은 이름이 이미 있는 항목을 반환한다.
     * @param items 작업 대상 항목
     * @param destinationNames 목적지 디렉터리의 기존 파일 이름 집합
     * @param nameOf 항목에서 파일 이름을 꺼내는 함수
     * @return [items] 중 충돌하는 항목(원래 순서 유지)
     */
    fun <T> copyMoveConflicts(items: List<T>, destinationNames: Set<String>, nameOf: (T) -> String): List<T> =
        items.filter { nameOf(it) in destinationNames }

    /**
     * 압축 해제 시 생성될 폴더(확장자를 뗀 이름)가 목적지에 이미 있는 아카이브 항목을 반환한다.
     * @param items 압축 해제 대상 아카이브 항목
     * @param destinationNames 목적지 디렉터리의 기존 파일 이름 집합
     * @param nameOf 항목에서 파일 이름(확장자 포함)을 꺼내는 함수
     * @return [items] 중 충돌하는 항목(원래 순서 유지)
     */
    fun <T> unzipConflicts(items: List<T>, destinationNames: Set<String>, nameOf: (T) -> String): List<T> =
        items.filter { nameOf(it).substringBeforeLast('.') in destinationNames }

    /**
     * [targetPath] 항목의 이름을 [newName]으로 바꿀 때 같은 폴더의 기존 항목과 어떻게 겹치는지 판정한다.
     * 이름을 바꿀 항목 자신은 비교에서 제외하므로, 대소문자만 바꾸는 이름 변경은 충돌이 아니다.
     * @param targetPath 이름을 바꿀 항목의 경로
     * @param targetIsDirectory 이름을 바꿀 항목이 폴더이면 true
     * @param newName 새 이름
     * @param siblings 이름을 바꿀 항목이 있는 폴더의 항목 목록(그 항목 자신 포함 가능)
     * @param ignoreCase 저장소가 대소문자를 구분하지 않으면 true — 이름을 대소문자 무시로 비교한다
     * @param pathOf 항목의 경로를 꺼내는 함수 — 이름을 바꿀 항목 자신을 가려내는 데 쓴다
     * @param nameOf 항목의 이름을 꺼내는 함수
     * @param isDirectoryOf 항목이 폴더인지 판별하는 함수
     * @return 겹치는 항목이 없으면 [RenameConflict.NONE], 있으면 두 항목의 종류에 따른 처리 방식
     */
    fun <T> renameConflict(
        targetPath: String,
        targetIsDirectory: Boolean,
        newName: String,
        siblings: List<T>,
        ignoreCase: Boolean,
        pathOf: (T) -> String,
        nameOf: (T) -> String,
        isDirectoryOf: (T) -> Boolean,
    ): RenameConflict {
        val existing = siblings.firstOrNull { pathOf(it) != targetPath && nameOf(it).equals(newName, ignoreCase) }
            ?: return RenameConflict.NONE
        return conflictBetween(targetIsDirectory, isDirectoryOf(existing))
    }

    /**
     * [baseName]+[extension]이 [existingNames]와 겹치면 [FileNameParts.numbered] 순서로 번호를 붙여 겹치지 않는 첫 기본 이름을 반환한다.
     * @param baseName 확장자를 뺀 원하는 이름
     * @param extension 점을 포함한 확장자(없으면 빈 문자열)
     * @param existingNames 폴더의 기존 항목 이름
     * @param ignoreCase 저장소가 대소문자를 구분하지 않으면 true
     * @return 겹치지 않는 이름(확장자 제외)
     */
    fun firstFreeBaseName(baseName: String, extension: String, existingNames: List<String>, ignoreCase: Boolean): String {
        val index = generateSequence(0) { it + 1 }.first { index ->
            val candidate = FileNameParts.numbered(baseName, extension, index)
            existingNames.none { it.equals(candidate, ignoreCase) }
        }
        return FileNameParts.numbered(baseName, "", index)
    }

    /**
     * 바꿀 항목과 기존 항목의 종류로 처리 방식을 정한다.
     * @param sourceIsDirectory 이름을 바꿀 항목이 폴더이면 true
     * @param existingIsDirectory 같은 이름의 기존 항목이 폴더이면 true
     * @return 둘 다 파일이면 [RenameConflict.FILE], 둘 다 폴더면 [RenameConflict.FOLDER], 종류가 다르면 [RenameConflict.TYPE_MISMATCH]
     */
    private fun conflictBetween(sourceIsDirectory: Boolean, existingIsDirectory: Boolean): RenameConflict = when {
        sourceIsDirectory != existingIsDirectory -> RenameConflict.TYPE_MISMATCH
        sourceIsDirectory -> RenameConflict.FOLDER
        else -> RenameConflict.FILE
    }
}
