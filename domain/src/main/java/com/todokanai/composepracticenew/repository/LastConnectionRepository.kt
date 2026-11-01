package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.SavedRemoteId

/** 마지막으로 접속한 원격 스토리지 id의 영속성 계약. 저장 실패는 [PreferenceFailureRepository.failures]로 알린다. */
interface LastConnectionRepository {
    /**
     * 마지막 접속 서버 id를 읽는다. 저장된 id가 없는 것과 설정을 읽지 못한 것을 구별해 돌려준다.
     *
     * 의도치 않게 서버 연결이 끊어졌을 때 재연결 용도로만 사용할 것.
     */
    suspend fun lastRemoteId(): SavedRemoteId

    /** 마지막 접속 서버 id를 저장한다. null이면 초기화한다. 저장은 호출 순서대로 반영된다. */
    fun setLastRemoteId(id: Long?)

    /**
     * 저장된 마지막 접속 서버 id가 [id]와 같을 때만 초기화한다. 확인과 초기화는 한 번의 저장 안에서 이루어지고,
     * 앞서 호출한 [setLastRemoteId]가 반영된 값을 기준으로 판단한다.
     */
    fun clearLastRemoteIdIf(id: Long)
}
