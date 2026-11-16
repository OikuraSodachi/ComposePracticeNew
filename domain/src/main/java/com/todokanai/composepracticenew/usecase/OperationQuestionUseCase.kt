package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.ConflictAnswer
import com.todokanai.composepracticenew.model.OperationQuestion
import com.todokanai.composepracticenew.model.PasswordAnswer
import com.todokanai.composepracticenew.repository.OperationQuestionRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * 진행 중인 파일 작업이 실행 중에 묻는 질문(쓰기 충돌·압축 파일 비밀번호)을 화면이 보고 답하게 하는 UseCase.
 * @param questions 답을 기다리는 작업 질문을 보고 답하는 통로
 */
class OperationQuestionUseCase(private val questions: OperationQuestionRepository) {

    /** 지금 물을 질문. 없으면 null. */
    val current: StateFlow<OperationQuestion?> get() = questions.current

    /**
     * [questionId]가 가리키는 충돌 질문에 [answer]로 답한다. 이미 답했거나 거둔 질문이면 아무것도 하지 않는다.
     * @param questionId 답할 질문의 식별자([OperationQuestion.id])
     * @param answer 사용자의 답
     * @return Unit
     */
    fun answerConflict(questionId: Long, answer: ConflictAnswer) = questions.answerConflict(questionId, answer)

    /**
     * [questionId]가 가리키는 비밀번호 질문에 [answer]로 답한다. 이미 답했거나 거둔 질문이면 아무것도 하지 않는다.
     * @param questionId 답할 질문의 식별자([OperationQuestion.id])
     * @param answer 사용자의 답
     * @return Unit
     */
    fun answerPassword(questionId: Long, answer: PasswordAnswer) = questions.answerPassword(questionId, answer)
}
