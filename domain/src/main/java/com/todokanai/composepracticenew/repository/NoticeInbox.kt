package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.PendingNotice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 화면이 알림을 꺼내 알릴 때까지 보관하는 알림함. 알림은 다 알릴 때까지([complete]) 지우지 않는다 —
 * 꺼낸 뒤 알리기를 마치지 못하면([release]) 돌려놓아, 표시 도중 화면이 사라져도 알림을 잃지 않도록.
 */
interface NoticeInbox<out T> {
    /** 아직 아무도 꺼내지 않은 알림(넣은 순서). 꺼내기([claim]) 전까지 남아, 받을 화면이 없을 때 생긴 알림도 잃지 않는다. */
    val pending: StateFlow<List<PendingNotice<T>>>

    /**
     * 꺼냈지만 아직 [complete]·[release]하지 않은 알림의 id. 꺼낸 동안 더는 유효하지 않게 된 알림은 여기서 빠진다 —
     * 꺼낸 쪽이 그 알림을 보여 주기를 멈출 수 있도록.
     */
    val claimedIds: Flow<Set<Long>>

    /**
     * [id]의 알림을 [pending]에서 꺼낸다. 꺼낸 쪽만 알린다 — 여러 화면이 같은 알림을 보아도 한 번만 알리도록.
     * 꺼낸 알림은 [complete]나 [release]를 부를 때까지 꺼낸 쪽이 맡는다.
     * @param id 꺼낼 알림의 [PendingNotice.id]
     * @return 이 호출이 꺼냈으면 true. 이미 꺼냈거나 빠진 알림이면 false
     */
    fun claim(id: Long): Boolean

    /**
     * 꺼낸 [id]의 알림을 다 알렸다. 알림을 지운다.
     * @param id [claim]으로 꺼낸 알림의 [PendingNotice.id]
     */
    fun complete(id: Long)

    /**
     * 꺼낸 [id]의 알림을 다 알리지 못했다. 넣은 순서 자리로 [pending]에 돌려놓아 다음에 다시 꺼내게 한다.
     * 꺼낸 동안 더는 유효하지 않게 된 알림은 돌려놓지 않는다.
     * @param id [claim]으로 꺼낸 알림의 [PendingNotice.id]
     */
    fun release(id: Long)
}
