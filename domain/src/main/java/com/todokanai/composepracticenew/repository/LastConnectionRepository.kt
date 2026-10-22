package com.todokanai.composepracticenew.repository

/** 마지막으로 접속한 원격 스토리지 id의 영속성 계약. */
interface LastConnectionRepository {
    /** 마지막 접속 서버 id를 반환한다. 없으면 null. */
    suspend fun lastRemoteId(): Long?

    /** 마지막 접속 서버 id를 저장한다. null이면 초기화한다. 저장은 호출 순서대로 반영된다. */
    fun setLastRemoteId(id: Long?)

    /**
     * 저장된 마지막 접속 서버 id가 [id]와 같을 때만 초기화한다. 확인과 초기화는 한 번의 저장 안에서 이루어지고,
     * 앞서 호출한 [setLastRemoteId]가 반영된 값을 기준으로 판단한다.
     */
    fun clearLastRemoteIdIf(id: Long)
}
