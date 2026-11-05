package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.OperationNotice
import com.todokanai.composepracticenew.model.OperationNoticeKind
import com.todokanai.composepracticenew.model.PendingNotice
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 파일 작업이 메시지를 맡기고([OperationNoticeReporter]), 화면이 꺼내 알리는([OperationNoticeRepository]) 메시지 알림함. 보이는 화면이 없을 때(저장소 화면, Activity 없음) 생긴 실패도 잃지 않는다. */
@Singleton
class OperationNotices @Inject constructor() : OperationNoticeRepository, OperationNoticeReporter {

    /** 메시지를 보관하고 꺼내 주는 알림함. */
    private val box = NoticeBox<OperationNotice>()

    override val pending: StateFlow<List<PendingNotice<OperationNotice>>> get() = box.pending

    override fun report(kind: OperationNoticeKind, message: String) = box.report(OperationNotice(kind, message))

    override fun claim(id: Long): Boolean = box.claim(id)

    override fun complete(id: Long) = box.complete(id)

    override fun release(id: Long) = box.release(id)
}
