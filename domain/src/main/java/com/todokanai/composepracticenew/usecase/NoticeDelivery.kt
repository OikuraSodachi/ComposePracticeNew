package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.PendingNotice
import com.todokanai.composepracticenew.repository.NoticeInbox

/**
 * 알림함의 알림을 [show]로 하나씩, 앞 알림을 다 보여 준 뒤에 다음 알림을 꺼내 넘긴다. 취소될 때까지 돌아오지 않는다.
 * 한 번에 하나만 꺼내므로 꺼냈는데 보여 주지 못하고 쌓이는 알림이 없다 — 표시 수단의 한도를 넘어 버려지는 일이 없도록.
 * 보여 주는 도중 취소되면 그 알림을 돌려놓아 다음에 다시 알린다.
 * @param show 알림을 보여 주고, 다 보여 줄 때까지 돌아오지 않는 함수
 * @return 돌아오지 않는다(취소로만 끝난다)
 */
internal suspend fun <T> NoticeInbox<T>.deliverOneByOne(show: suspend (T) -> Unit): Nothing =
    pending.collect { notices ->
        notices.forEach { if (claim(it.id)) showClaimed(it, show) }
    }

/** 꺼낸 [notice]를 [show]로 보여 주고, 다 보여 줬으면 지우고 아니면 돌려놓는다. */
private suspend fun <T> NoticeInbox<T>.showClaimed(notice: PendingNotice<T>, show: suspend (T) -> Unit) {
    var shown = false
    try {
        show(notice.content)
        shown = true
    } finally {
        if (shown) complete(notice.id) else release(notice.id)
    }
}
