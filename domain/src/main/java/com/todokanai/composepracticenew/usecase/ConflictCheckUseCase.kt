package com.todokanai.composepracticenew.usecase

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
}
