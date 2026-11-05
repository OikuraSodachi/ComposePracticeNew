package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.PendingNotice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 알림을 넣고 화면이 꺼내 알리는 [NoticeInbox] 구현. 알림 종류마다 저장소가 이것을 합성해 쓴다.
 * 사건을 흘려보내는 대기열이 아니라 상태로 둔다 — 받을 화면이 없을 때 생긴 알림도 잃지 않고, 화면이 여럿이어도 각 알림을 꺼낸 한 곳에서만 알리도록.
 * 꺼낸 알림은 다 알릴 때까지 따로 맡아 두어, 알리지 못하면 돌려놓는다.
 */
class NoticeBox<T> : NoticeInbox<T> {

    /** [_pending]과 [claimed]를 함께 바꾸는 잠금. 알림은 한 곳에만 있어야 하므로 두 목록을 한 번에 바꾼다. */
    private val lock = Any()

    /** 다음 알림에 붙일 id. 알림마다 다르게 해 꺼낼 때 어느 알림인지 가리고, 돌려놓을 때 넣은 순서를 지킨다. [lock] 안에서만 바꾼다. */
    private var nextId = 0L

    /** 아직 아무도 꺼내지 않은 알림. 알림은 사용자 동작마다 하나라 사용자 동작 수를 넘지 않는다. */
    private val _pending = MutableStateFlow<List<PendingNotice<T>>>(emptyList())

    /** 꺼냈지만 아직 다 알리지 않은 알림(id별). [lock] 안에서만 읽고 바꾼다. */
    private val claimed = mutableMapOf<Long, PendingNotice<T>>()

    override val pending: StateFlow<List<PendingNotice<T>>> = _pending.asStateFlow()

    /**
     * 알림 [content]를 넣는다. 호출 스레드에서 바로 반환한다.
     * @param content 알릴 내용
     */
    fun report(content: T) {
        synchronized(lock) { _pending.value += PendingNotice(nextId++, content) }
    }

    /**
     * [obsolete]인 알림을 꺼냈든 아니든 모두 뺀다. 꺼낸 알림이라면 알리지 못해도 돌려놓지 않는다.
     * @param obsolete 더는 사실이 아니어서 알리면 안 되는 알림인지
     */
    fun discard(obsolete: (T) -> Boolean) {
        synchronized(lock) {
            _pending.value = _pending.value.filterNot { obsolete(it.content) }
            claimed.values.removeAll { obsolete(it.content) }
        }
    }

    override fun claim(id: Long): Boolean = synchronized(lock) {
        val notice = _pending.value.find { it.id == id } ?: return false
        _pending.value -= notice
        claimed[id] = notice
        true
    }

    override fun complete(id: Long) {
        synchronized(lock) { claimed.remove(id) }
    }

    override fun release(id: Long) {
        synchronized(lock) {
            val notice = claimed.remove(id) ?: return
            _pending.value = (_pending.value + notice).sortedBy { it.id }
        }
    }
}
