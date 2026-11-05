package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.OperationNotice
import com.todokanai.composepracticenew.model.OperationNoticeKind
import kotlinx.coroutines.flow.StateFlow

/**
 * 파일 작업의 실패·거부·완료 메시지를 화면이 꺼내 알릴 때까지 보관하는 Repository.
 * 작업의 진행 상태는 [ProgressRepository]가 맡는다 — 실행 전 거부처럼 진행과 무관한 메시지도 여기로 온다.
 */
interface OperationNoticeRepository {
    /**
     * 아직 화면에 알리지 않은 메시지(넣은 순서). 화면이 꺼내기([claim]) 전까지 남아, 보이는 화면이 없을 때 생긴 메시지도 잃지 않는다.
     */
    val pending: StateFlow<List<OperationNotice>>

    /**
     * 메시지를 [pending]에 넣는다. 호출 스레드에서 바로 반환한다.
     * @param kind 메시지의 종류
     * @param message 사용자에게 보일 문구
     */
    fun report(kind: OperationNoticeKind, message: String)

    /**
     * [id]의 메시지를 [pending]에서 꺼낸다. 꺼낸 쪽만 알린다 — 여러 화면이 같은 메시지를 보아도 한 번만 알리도록.
     * @param id 꺼낼 메시지의 [OperationNotice.id]
     * @return 이 호출이 꺼냈으면 true. 이미 꺼낸 메시지면 false
     */
    fun claim(id: Long): Boolean
}
