package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ConflictAnswer
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.OperationQuestion
import com.todokanai.composepracticenew.model.PasswordAnswer
import com.todokanai.composepracticenew.model.QuestionRequest
import kotlinx.coroutines.flow.StateFlow

/**
 * 작업이 질문을 맡기고 답을 기다리는 쪽의 통로. 질문은 종류와 무관하게 한 질문함에 맡긴 순서대로 쌓이며, 답을 받거나 기다림이 끝날 때(취소·시간 초과)까지 보관된다.
 * 보이는 화면이 없어도 질문은 남아, 화면이 다시 보일 때 묻는다. 기다리는 코루틴이 취소되면 질문을 거둔다.
 */
interface OperationQuestionAsker {
    /**
     * [request]를 묻고 답을 기다린다.
     * @param operationType 질문하는 작업의 종류
     * @param request 묻는 내용. 받을 답의 타입을 정한다
     * @return 사용자의 답
     */
    suspend fun <A : Any> ask(operationType: FileOperationType, request: QuestionRequest<A>): A
}

/** 화면이 답을 기다리는 작업 질문을 보고 답하는 쪽의 통로. */
interface OperationQuestionRepository {
    /** 지금 물을 질문(가장 먼저 맡긴 것). 없으면 null. 여러 작업이 동시에 물으면 종류와 무관하게 하나씩 차례로 나온다. */
    val current: StateFlow<OperationQuestion?>

    /**
     * [id] 충돌 질문에 [answer]로 답한다. 이미 답했거나 거둔 질문, 충돌 질문이 아닌 질문이면 아무것도 하지 않는다.
     * @param id 답할 질문의 [OperationQuestion.id]
     * @param answer 사용자의 답
     */
    fun answerConflict(id: Long, answer: ConflictAnswer)

    /**
     * [id] 비밀번호 질문에 [answer]로 답한다. 이미 답했거나 거둔 질문, 비밀번호 질문이 아닌 질문이면 아무것도 하지 않는다.
     * @param id 답할 질문의 [OperationQuestion.id]
     * @param answer 사용자의 답
     */
    fun answerPassword(id: Long, answer: PasswordAnswer)
}
