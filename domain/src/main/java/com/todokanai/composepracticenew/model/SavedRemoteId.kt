package com.todokanai.composepracticenew.model

/** 저장된 마지막 접속 서버 id를 읽은 결과. 저장된 값이 없는 것과 읽지 못한 것을 구별한다. */
sealed interface SavedRemoteId {

    /**
     * 저장된 id가 있다.
     * @param id 마지막으로 접속한 서버의 id
     */
    data class Saved(val id: Long) : SavedRemoteId

    /** 저장된 id가 없다. */
    data object None : SavedRemoteId

    /** 설정을 읽지 못해 저장된 id가 있는지 알 수 없다. */
    data object Unreadable : SavedRemoteId
}
