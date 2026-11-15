package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.ConnectFailureAlert
import com.todokanai.composepracticenew.repository.ConnectFailureAlertRepository

/**
 * 원격 연결 실패를 화면에 알리기 위해 제공하는 UseCase.
 * @param repo 원격 연결 실패를 알려주는 ConnectFailureAlertRepository
 */
class ConnectFailureAlertUseCase(private val repo: ConnectFailureAlertRepository) {
    /**
     * 알리기를 기다리는 연결 실패를 [show]로 하나씩, 앞 알림을 다 보여 준 뒤에 넘긴다. 취소될 때까지 돌아오지 않으며, 화면이 보이는 동안만 부른다 —
     * 보이지 않는 동안 실패한 것과 보여 주는 도중 화면이 사라진 알림은 다시 보일 때 넘긴다.
     * 알림마다 먼저 꺼낸 한 곳에만 넘기므로 여러 화면이 함께 불러도 한 번만 알린다.
     * @param show 연결 실패를 보여 주고, 다 보여 줄 때까지 돌아오지 않는 함수
     * @return 돌아오지 않는다(취소로만 끝난다)
     */
    suspend fun deliver(show: suspend (ConnectFailureAlert) -> Unit): Nothing = repo.deliverOneByOne(show)
}
