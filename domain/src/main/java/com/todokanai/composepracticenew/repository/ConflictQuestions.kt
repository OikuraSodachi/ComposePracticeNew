package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ConflictAnswer
import com.todokanai.composepracticenew.model.ConflictQuestion
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.WriteConflict
import kotlinx.coroutines.flow.StateFlow

/** 작업이 충돌 질문을 맡기고 답을 기다리는 쪽의 통로. 질문은 답을 받거나 기다림이 끝날 때(취소·시간 초과)까지 보관된다. */
interface ConflictQuestionAsker {
    /**
     * [conflict]를 묻고 답을 기다린다. 보이는 화면이 없어도 질문은 남아, 화면이 다시 보일 때 묻는다.
     * 기다리는 코루틴이 취소되면 질문을 거둔다.
     * @param operationType 질문하는 작업의 종류
     * @param conflict 겹친 항목
     * @return 사용자의 답
     */
    suspend fun ask(operationType: FileOperationType, conflict: WriteConflict): ConflictAnswer
}

/** 화면이 답을 기다리는 충돌 질문을 보고 답하는 쪽의 통로. */
interface ConflictQuestionRepository {
    /** 지금 물을 질문(가장 먼저 맡긴 것). 없으면 null. 여러 작업이 동시에 물으면 하나씩 차례로 나온다. */
    val current: StateFlow<ConflictQuestion?>

    /**
     * [id] 질문에 [answer]로 답한다. 이미 답했거나 거둔 질문이면 아무것도 하지 않는다.
     * @param id 답할 질문의 [ConflictQuestion.id]
     * @param answer 사용자의 답
     */
    fun answer(id: Long, answer: ConflictAnswer)
}
