package com.todokanai.composepracticenew.usecase

/**
 * 목적지 디렉터리에 이미 존재하는 파일 이름을 확인하는 UseCase.
 */
class ConflictCheckUseCase {

    /**
     * 복사·이동 시 목적지에 이미 존재하는 파일 이름 집합을 반환한다.
     * @param selectedNames 선택된 파일 이름 목록
     * @param destinationNames 목적지 디렉터리의 기존 파일 이름 집합
     * @return 목적지에 이미 존재하는 파일 이름 집합
     */
    fun checkCopyMoveConflicts(
        selectedNames: List<String>,
        destinationNames: Set<String>,
    ): Set<String> = selectedNames.filterTo(HashSet()) { it in destinationNames }

    /**
     * 압축 해제 시 목적지에 이미 존재하는 파일 기본 이름(확장자 제외) 집합을 반환한다.
     * @param selectedNames 선택된 zip 파일 이름 목록
     * @param destinationNames 목적지 디렉터리의 기존 파일 이름 집합
     * @return 목적지에 이미 존재하는 파일 기본 이름(확장자 제외) 집합
     */
    fun checkUnzipConflicts(
        selectedNames: List<String>,
        destinationNames: Set<String>,
    ): Set<String> = selectedNames.mapTo(HashSet()) { it.substringBeforeLast('.') }
        .filterTo(HashSet()) { it in destinationNames }
}
