package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.ConflictAnswer
import com.todokanai.composepracticenew.model.ConflictQuestion
import com.todokanai.composepracticenew.repository.ConflictQuestionRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * 진행 중인 파일 작업이 쓰기 중에 만난 같은 이름의 항목을 화면이 묻고 답하게 하는 UseCase.
 * @param questions 답을 기다리는 충돌 질문을 보고 답하는 통로
 */
class ConflictQuestionUseCase(private val questions: ConflictQuestionRepository) {

    /** 지금 물을 질문. 없으면 null. */
    val current: StateFlow<ConflictQuestion?> get() = questions.current

    /**
     * [question]에 [answer]로 답한다. 이미 답했거나 거둔 질문이면 아무것도 하지 않는다.
     * @param question 답할 질문
     * @param answer 사용자의 답
     * @return Unit
     */
    fun answer(question: ConflictQuestion, answer: ConflictAnswer) = questions.answer(question.id, answer)
}
