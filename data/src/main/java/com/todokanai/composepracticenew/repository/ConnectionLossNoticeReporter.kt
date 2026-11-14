package com.todokanai.composepracticenew.repository

/** 원격 세션의 끊김을 화면이 알릴 때까지 맡긴다. 맡긴 끊김을 꺼내는 쪽 계약은 [ConnectionLossNoticeRepository]다. */
interface ConnectionLossNoticeReporter {
    /** 연결된 세션이 끊겼을 때 부른다. 호출 스레드에서 바로 반환한다. */
    fun report()

    /** 새 연결을 시작할 때 부른다. 아직 다 알리지 않은 끊김을 뺀다 — 새 연결이 끊긴 것으로 보이지 않도록. */
    fun connectionStarted()
}
