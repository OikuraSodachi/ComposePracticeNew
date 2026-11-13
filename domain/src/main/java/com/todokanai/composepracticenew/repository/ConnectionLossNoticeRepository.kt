package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ConnectionLossNotice
import com.todokanai.composepracticenew.model.PendingNotice
import kotlinx.coroutines.flow.StateFlow

/** 원격 세션의 예기치 않은 끊김을 알려준다. 끊긴 순간 받을 화면이 없어도 알림을 잃지 않도록 알림함으로 둔다. */
interface ConnectionLossNoticeRepository : NoticeInbox<ConnectionLossNotice> {
    /**
     * 아직 화면에 알리지 않은 끊김(일어난 순서). 화면이 꺼내기([claim]) 전까지 남아, 화면이 보이지 않을 때 끊긴 것도 다시 보일 때 알린다.
     * 새 연결을 시작하면 꺼내기 전이라도 빠지고, 꺼낸 뒤라면 돌려놓지([release]) 않는다 — 새 연결이 끊긴 것으로 보이지 않도록.
     */
    override val pending: StateFlow<List<PendingNotice<ConnectionLossNotice>>>
}
