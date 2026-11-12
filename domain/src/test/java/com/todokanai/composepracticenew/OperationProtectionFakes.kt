package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.ConflictAnswer
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.WriteConflict
import com.todokanai.composepracticenew.repository.ConflictQuestionAsker
import com.todokanai.composepracticenew.operation.OperationProtection

/** 언제나 바로 보호를 주는 테스트용 작업 보호. 보호를 다루지 않는 테스트가 쓴다. */
internal object GrantedProtection : OperationProtection {
    override suspend fun acquire(): IoResult<Unit> = IoResult.Done
}

/** 충돌 질문이 오면 테스트를 실패시키는 질문 통로. */
internal object UnexpectedConflictQuestions : ConflictQuestionAsker {
    override suspend fun ask(operationType: FileOperationType, conflict: WriteConflict): ConflictAnswer = throw AssertionError("예상하지 못한 충돌 질문: $conflict")
}
