package com.todokanai.composepracticenew.model

/**
 * 원격 스토리지 접속 정보 입력을 검증한 결과.
 * 저장은 [Valid]만 받으므로 검증하지 않은 입력이나 거부된 입력으로 저장하는 코드는 컴파일되지 않는다(#366).
 */
sealed interface RemoteStorageInputCheck {

    /**
     * 모든 규칙을 통과한 입력. domain의 검증만 만들 수 있고, 저장할 항목은 domain 밖에 드러내지 않는다.
     * @param item 입력을 바꾼 저장할 항목
     */
    class Valid internal constructor(internal val item: RemoteStorageItem) : RemoteStorageInputCheck

    /**
     * 규칙을 하나 이상 위반한 입력.
     * @param errors 위반한 규칙의 집합. 비어 있지 않다
     */
    data class Invalid(val errors: Set<RemoteStorageInputError>) : RemoteStorageInputCheck {
        init {
            require(errors.isNotEmpty()) { "위반한 규칙이 없는 입력은 Invalid가 아니다" }
        }
    }
}
