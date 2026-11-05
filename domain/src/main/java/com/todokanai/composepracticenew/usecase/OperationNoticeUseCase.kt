package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.repository.OperationNoticeRepository

/**
 * 파일 작업의 실패·거부·완료 메시지를 화면에 알리기 위해 제공하는 UseCase.
 * @param repo 알리기를 기다리는 메시지를 보관하는 OperationNoticeRepository
 */
class OperationNoticeUseCase(private val repo: OperationNoticeRepository) {
    /**
     * 알리기를 기다리는 메시지를 [show]로 하나씩 넘긴다. 취소될 때까지 돌아오지 않으며, 화면이 보이는 동안만 부른다 —
     * 보이는 화면이 없는 동안 생긴 메시지는 다시 보일 때 넘긴다.
     * 메시지마다 먼저 꺼낸 한 곳에만 넘기므로 여러 화면이 함께 불러도 한 번만 알린다. 꺼낸 뒤 [show]까지 일시 중단하지 않아, 취소돼도 꺼낸 메시지를 잃지 않는다.
     * @param show 메시지 문구를 사용자에게 보여 주는 함수
     * @return 돌아오지 않는다(취소로만 끝난다)
     */
    suspend fun deliver(show: (String) -> Unit): Nothing =
        repo.pending.collect { notices ->
            notices.forEach { if (repo.claim(it.id)) show(it.message) }
        }
}
