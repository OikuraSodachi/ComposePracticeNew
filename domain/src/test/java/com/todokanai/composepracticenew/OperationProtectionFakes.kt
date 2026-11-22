package com.todokanai.composepracticenew

import com.todokanai.composepracticenew.model.ArchivePasswordPrompt
import com.todokanai.composepracticenew.model.ConflictAnswer
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.QuestionRequest
import com.todokanai.composepracticenew.model.PasswordAnswer
import com.todokanai.composepracticenew.model.WriteConflict
import com.todokanai.composepracticenew.repository.OperationQuestionAsker
import com.todokanai.composepracticenew.operation.OperationProtection
import com.todokanai.composepracticenew.operation.UnexpectedFailureRecorder

/** 언제나 바로 보호를 주는 테스트용 작업 보호. 보호를 다루지 않는 테스트가 쓴다. */
internal object GrantedProtection : OperationProtection {
    override suspend fun acquire(): IoResult<Unit> = IoResult.Done
}

/** 작업 질문이 오면 테스트를 실패시키는 질문 통로. */
internal object UnexpectedQuestions : OperationQuestionAsker {
    override suspend fun <A : Any> ask(operationType: FileOperationType, request: QuestionRequest<A>): A = when (request) {
        is QuestionRequest.Conflict -> throw AssertionError("예상하지 못한 충돌 질문: ${request.conflict}")
        is QuestionRequest.ArchivePassword -> throw AssertionError("예상하지 못한 비밀번호 질문: ${request.archiveName}")
    }
}

/** 기록한 구현 오류를 버리는 테스트용 기록기. 구현 오류 기록을 다루지 않는 테스트가 쓴다. */
internal object NoOpFailureRecorder : UnexpectedFailureRecorder {
    override fun record(error: Throwable) {}
}
