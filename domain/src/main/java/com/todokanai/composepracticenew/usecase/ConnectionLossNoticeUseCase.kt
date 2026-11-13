package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.ConnectionLossNotice
import com.todokanai.composepracticenew.repository.ConnectionLossNoticeRepository

/**
 * 원격 세션의 예기치 않은 끊김을 화면에 알리기 위해 제공하는 UseCase.
 * @param repo 원격 세션의 끊김을 알려주는 ConnectionLossNoticeRepository
 */
class ConnectionLossNoticeUseCase(private val repo: ConnectionLossNoticeRepository) {
    /**
     * 알리기를 기다리는 끊김을 [show]로 하나씩, 앞 알림을 다 보여 준 뒤에 넘긴다. 취소될 때까지 돌아오지 않으며, 화면이 보이는 동안만 부른다 —
     * 보이지 않는 동안 끊긴 것과 보여 주는 도중 화면이 사라진 알림은 다시 보일 때 넘긴다(그 사이 새 연결을 시작했으면 넘기지 않는다).
     * 알림마다 먼저 꺼낸 한 곳에만 넘기므로 여러 화면이 함께 불러도 한 번만 알린다.
     * @param show 끊김을 보여 주고, 다 보여 줄 때까지 돌아오지 않는 함수
     * @return 돌아오지 않는다(취소로만 끝난다)
     */
    suspend fun deliver(show: suspend (ConnectionLossNotice) -> Unit): Nothing = repo.deliverOneByOne(show)
}
