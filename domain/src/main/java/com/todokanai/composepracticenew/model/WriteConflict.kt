package com.todokanai.composepracticenew.model

/**
 * 작업이 항목을 쓰려는 자리에 이미 같은 이름의 항목이 있다는 사실. 쓰기를 시도한 파일 시스템이 알려 준 결과이며, 목록으로 미리 짐작한 것이 아니다.
 * @param name 겹친 항목의 이름
 * @param folderPath 겹친 항목이 놓인 폴더의 경로(표시용)
 * @param kind 쓰려던 항목과 이미 있는 항목의 종류 조합
 */
data class WriteConflict(
    val name: String,
    val folderPath: String,
    val kind: WriteConflictKind,
)

/** 쓰려던 항목과 이미 있는 항목의 종류 조합. 고를 수 있는 처리 방법이 조합마다 다르다. */
enum class WriteConflictKind(val canProceed: Boolean) {
    /** 파일 자리에 파일이 있다. 진행하면 덮어쓴다. */
    FILE(canProceed = true),
    /** 폴더 자리에 폴더가 있다. 진행하면 두 폴더를 합치고, 안쪽 항목은 다시 하나씩 판정한다. */
    FOLDER(canProceed = true),
    /** 파일과 폴더가 엇갈려 있다. 한쪽을 다른 쪽으로 바꿀 수 없으므로 건너뛰기만 할 수 있다. */
    TYPE_MISMATCH(canProceed = false),
}

/** 겹친 항목 하나를 어떻게 처리할지. 파일 쓰기 구현이 받는 값이다. */
enum class ConflictChoice {
    /** 파일은 덮어쓰고, 폴더는 합친다. [WriteConflictKind.canProceed]인 겹침에만 쓴다. */
    PROCEED,
    /** 이 항목(폴더면 그 안쪽 전체)을 쓰지 않고 다음 항목으로 넘어간다. */
    SKIP,
}

/** 사용자가 충돌 질문에 준 답. */
sealed interface ConflictAnswer {
    /**
     * 이 겹침을 [choice]대로 처리한다.
     * @param choice 처리 방법
     * @param applyToRest true면 같은 작업에서 같은 종류([WriteConflictKind])의 남은 겹침에도 묻지 않고 같은 처리를 쓴다
     */
    data class Choose(val choice: ConflictChoice, val applyToRest: Boolean) : ConflictAnswer

    /** 작업을 취소한다. 이미 쓴 항목은 그대로 둔다. */
    data object Cancel : ConflictAnswer
}

/**
 * 답을 기다리는 충돌 질문.
 * @param id 질문 식별자. 답할 때 어느 질문인지 가리킨다
 * @param operationType 질문한 작업의 종류
 * @param conflict 겹친 항목
 */
data class ConflictQuestion(
    val id: Long,
    val operationType: FileOperationType,
    val conflict: WriteConflict,
)
