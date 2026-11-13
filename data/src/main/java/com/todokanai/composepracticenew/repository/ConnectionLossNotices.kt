package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ConnectionLossNotice
import com.todokanai.composepracticenew.model.PendingNotice
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 원격 세션이 끊김을 넣고, 화면이 꺼내 알리는 알림함.
 * 연결 상태를 관찰해 넣지 않고 세션이 끊김으로 바꾸는 곳에서 넣는다 — 상태 흐름은 빠르게 이어진 변화를 합쳐 끊김을 건너뛸 수 있으므로.
 */
@Singleton
class ConnectionLossNotices @Inject constructor() : ConnectionLossNoticeRepository {

    /** 끊김을 보관하고 꺼내 주는 알림함. 끊김은 연결마다 한 번이라 연결 시도 수를 넘지 않는다. */
    private val box = NoticeBox<ConnectionLossNotice>()

    override val pending: StateFlow<List<PendingNotice<ConnectionLossNotice>>> get() = box.pending

    /** 연결된 세션이 끊겼을 때 부른다. 호출 스레드에서 바로 반환한다. */
    fun report() = box.report(ConnectionLossNotice)

    /** 새 연결을 시작할 때 부른다. 아직 다 알리지 않은 끊김을 뺀다 — 새 연결이 끊긴 것으로 보이지 않도록. */
    fun connectionStarted() = box.discard { true }

    override fun claim(id: Long): Boolean = box.claim(id)

    override fun complete(id: Long) = box.complete(id)

    override fun release(id: Long) = box.release(id)
}
