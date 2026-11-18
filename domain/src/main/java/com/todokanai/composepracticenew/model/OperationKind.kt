package com.todokanai.composepracticenew.model

/**
 * 실행한 파일 작업이 무엇인지. 진행 표시·보호가 있는 작업과 바로 끝나는 순간 작업을 타입으로 나눈다(#460).
 * 완료·취소·실패 메시지의 문구(실패의 머리말 포함)는 app이 이 값으로 정하므로, 저장소와 UseCase는 실패 원인에 작업 맥락을 붙이지 않는다.
 */
sealed interface OperationKind {

    /**
     * 진행 표시·보호·취소가 있는 작업.
     * @param type 작업 종류
     */
    data class Tracked(val type: FileOperationType) : OperationKind

    /**
     * 진행 표시·보호 없이 바로 끝나는 작업.
     * @param operation 작업 종류
     * @param name 작업이 만들거나 바꾸려던 이름(새 폴더 이름, 바꿀 새 이름)
     */
    data class Instant(val operation: InstantOperation, val name: String) : OperationKind
}

/** 진행 표시·보호 없이 바로 끝나는 작업의 종류. */
enum class InstantOperation {
    /** 이름 변경. */
    RENAME,

    /** 새 폴더 만들기. */
    MAKE_FOLDER,
}
