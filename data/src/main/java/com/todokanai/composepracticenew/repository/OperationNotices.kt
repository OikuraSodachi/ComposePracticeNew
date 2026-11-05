package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.OperationNotice
import com.todokanai.composepracticenew.model.OperationNoticeKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 파일 작업이 메시지를 넣고, 화면이 꺼내 알리는 메시지 목록.
 * 사건을 흘려보내는 흐름이 아니라 상태로 둔다 — 보이는 화면이 없을 때(저장소 화면, Activity 없음) 생긴 실패도 잃지 않고,
 * 화면이 여럿이어도 각 메시지를 꺼낸 한 곳에서만 알리도록.
 */
@Singleton
class OperationNotices @Inject constructor() : OperationNoticeRepository {

    /** 다음 메시지에 붙일 id. 메시지마다 다르게 해 꺼낼 때 어느 메시지인지 가린다. */
    private val nextId = AtomicLong()

    /** 알리기를 기다리는 메시지. 메시지는 사용자가 시작한 작업·거부된 요청마다 하나라 사용자 동작 수를 넘지 않는다. */
    private val _pending = MutableStateFlow<List<OperationNotice>>(emptyList())

    override val pending: StateFlow<List<OperationNotice>> = _pending.asStateFlow()

    override fun report(kind: OperationNoticeKind, message: String) {
        val notice = OperationNotice(nextId.getAndIncrement(), kind, message)
        _pending.update { it + notice }
    }

    override fun claim(id: Long): Boolean {
        var claimed = false
        _pending.update { notices ->
            notices.filterNot { it.id == id }.also { claimed = it.size != notices.size }
        }
        return claimed
    }
}
