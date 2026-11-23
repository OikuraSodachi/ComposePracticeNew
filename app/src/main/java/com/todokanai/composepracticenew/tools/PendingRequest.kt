package com.todokanai.composepracticenew.tools

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 화면이 처리할 일회성 요청(화면 이동·닫기·앱 종료)을 화면이 처리했다고 알릴 때까지 상태로 보관한다(#534).
 * Channel로 보낸 이벤트는 받는 쪽이 꺼낸 뒤 처리하기 전에 수집이 취소되면 사라지지만, 상태는 처리했다고 알리기 전까지 남아 있어
 * 회전·백그라운드·수집 취소에도 잃지 않는다. 처리하기 전에 새 요청이 오면 마지막 요청 하나만 남는다.
 * 화면은 [request]를 받아 처리한 직후, 그 사이에 멈추지 않고(suspend 없이) [consume]을 부른다 — 처리와 알림 사이에 취소되어 같은 요청을 두 번 처리하지 않도록.
 */
class PendingRequest<T : Any> {
    /** [request]의 변경 가능한 원본. */
    private val _request = MutableStateFlow<T?>(null)

    /** 처리를 기다리는 요청. 없으면 null. */
    val request: StateFlow<T?> = _request.asStateFlow()

    /**
     * [request]를 보관한다. 처리를 기다리는 요청이 있으면 이것으로 바꾼다.
     * @param request 보관할 요청
     */
    fun post(request: T) {
        _request.value = request
    }

    /**
     * [request]를 처리했다고 알린다. 그 사이에 다른 요청이 들어왔으면 그 요청은 남긴다.
     * @param request 처리한 요청
     */
    fun consume(request: T) {
        _request.compareAndSet(request, null)
    }
}
