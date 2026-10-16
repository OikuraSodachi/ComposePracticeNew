package com.todokanai.composepracticenew.repository

/** 마지막으로 접속한 원격 스토리지 id의 영속성 계약. */
interface LastConnectionRepository {
    /** 마지막 접속 서버 id를 반환한다. 없으면 null. */
    suspend fun lastRemoteId(): Long?

    /** 마지막 접속 서버 id를 저장한다. null이면 초기화한다. */
    fun setLastRemoteId(id: Long?)
}
