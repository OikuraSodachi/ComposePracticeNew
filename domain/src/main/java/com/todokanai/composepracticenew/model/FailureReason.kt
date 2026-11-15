package com.todokanai.composepracticenew.model

/**
 * 파일 입출력 실패의 원인. 문장 대신 무엇이 실패했는지만 담고, 표시 문구는 app 레이어가 종류별로 문자열 리소스에서 정한다(#218).
 * 서버 응답 원문·항목 이름처럼 번역하지 않는 값은 각 종류의 필드로 담는다.
 */
sealed interface FailureReason {

    /**
     * data가 만든 원인 문장 그대로. data의 원인을 종류로 바꾸는 #218의 다음 단계까지만 쓴다.
     * @param text 표시할 원인 문장
     */
    data class Raw(val text: String) : FailureReason

    /**
     * 이름을 바꾸지 못했다.
     * @param name 바꾸려던 새 이름
     * @param cause 저장소가 알린 원인. 모르면 null
     */
    data class RenameFailed(val name: String, val cause: FailureReason?) : FailureReason

    /**
     * 폴더를 만들지 못했다.
     * @param name 만들려던 폴더 이름
     * @param cause 저장소가 알린 원인. 모르면 null
     */
    data class MakeFolderFailed(val name: String, val cause: FailureReason?) : FailureReason

    /** 시스템이 프로세스 보호를 거부해 작업을 시작하지 않았다. */
    data object ProtectionRefused : FailureReason
}
