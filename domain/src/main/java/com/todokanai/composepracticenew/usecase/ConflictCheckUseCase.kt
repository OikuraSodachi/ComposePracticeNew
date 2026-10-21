package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.RenameConflict

/**
 * 목적지 디렉터리에 이미 같은 이름이 존재해 충돌하는 항목을 판정하는 UseCase.
 * 항목 타입에 무관하게 동작하도록 이름 추출 함수를 받아, 호출부가 이름 추출·필터링을 반복하지 않게 한다.
 */
class ConflictCheckUseCase {

    /**
     * 복사·이동·전송 시 목적지에 같은 이름이 이미 있는 항목을 반환한다.
     * @param items 작업 대상 항목
     * @param destinationNames 목적지 디렉터리의 기존 파일 이름 집합
     * @param nameOf 항목에서 파일 이름을 꺼내는 함수
     * @return [items] 중 충돌하는 항목(원래 순서 유지)
     */
    fun <T> findCopyMoveConflicts(
        items: List<T>,
        destinationNames: Set<String>,
        nameOf: (T) -> String,
    ): List<T> = items.filter { nameOf(it) in destinationNames }

    /**
     * 압축 해제 시 생성될 폴더(확장자를 뗀 이름)가 목적지에 이미 있는 아카이브 항목을 반환한다.
     * @param items 압축 해제 대상 아카이브 항목
     * @param destinationNames 목적지 디렉터리의 기존 파일 이름 집합
     * @param nameOf 항목에서 파일 이름(확장자 포함)을 꺼내는 함수
     * @return [items] 중 충돌하는 항목(원래 순서 유지)
     */
    fun <T> findUnzipConflicts(
        items: List<T>,
        destinationNames: Set<String>,
        nameOf: (T) -> String,
    ): List<T> = items.filter { nameOf(it).substringBeforeLast('.') in destinationNames }

    /**
     * [target]의 이름을 [newName]으로 바꿀 때 같은 폴더의 기존 항목과 어떻게 겹치는지 판정한다.
     * [target] 자신은 비교에서 제외하므로, 대소문자만 바꾸는 이름 변경은 충돌이 아니다.
     * @param target 이름을 바꿀 항목
     * @param newName 새 이름
     * @param siblings [target]이 있는 폴더의 항목 목록([target] 포함 가능)
     * @param ignoreCase 저장소가 대소문자를 구분하지 않으면 true — 이름을 대소문자 무시로 비교한다
     * @param pathOf 항목의 경로를 꺼내는 함수 — [target] 자신을 가려내는 데 쓴다
     * @param nameOf 항목의 이름을 꺼내는 함수
     * @param isDirectoryOf 항목이 폴더인지 판별하는 함수
     * @return 겹치는 항목이 없으면 [RenameConflict.NONE], 있으면 두 항목의 종류에 따른 처리 방식
     */
    fun <T> findRenameConflict(
        target: T,
        newName: String,
        siblings: List<T>,
        ignoreCase: Boolean,
        pathOf: (T) -> String,
        nameOf: (T) -> String,
        isDirectoryOf: (T) -> Boolean,
    ): RenameConflict {
        val existing = siblings.firstOrNull { pathOf(it) != pathOf(target) && nameOf(it).equals(newName, ignoreCase) }
            ?: return RenameConflict.NONE
        return conflictBetween(isDirectoryOf(target), isDirectoryOf(existing))
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
