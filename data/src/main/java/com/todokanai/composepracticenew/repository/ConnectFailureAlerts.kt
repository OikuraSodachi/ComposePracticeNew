package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ConnectFailureAlert
import com.todokanai.composepracticenew.model.PendingNotice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 연결 결과를 받은 화면이 연결 실패를 넣고, 화면 호스트가 꺼내 알리는 알림함. */
@Singleton
class ConnectFailureAlerts @Inject constructor() : ConnectFailureAlertRepository, ConnectFailureAlertReporter {

    /** 연결 실패를 보관하고 꺼내 주는 알림함. 실패는 사용자가 시작한 연결 시도마다 한 번이라 연결 시도 수를 넘지 않는다. */
    private val box = NoticeBox<ConnectFailureAlert>()

    override val pending: StateFlow<List<PendingNotice<ConnectFailureAlert>>> get() = box.pending

    override val claimedIds: Flow<Set<Long>> get() = box.claimedIds

    override fun report(alert: ConnectFailureAlert) = box.report(alert)

    override fun connectionStarted() = box.discard { true }

    override fun claim(id: Long): Boolean = box.claim(id)

    override fun complete(id: Long) = box.complete(id)

    override fun release(id: Long) = box.release(id)
}
