package com.todokanai.composepracticenew.ui.model

import androidx.compose.runtime.Immutable
import com.todokanai.composepracticenew.model.ArchivePasswordPrompt
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.ConflictQuestion as DomainConflictQuestion
import com.todokanai.composepracticenew.model.OperationQuestion as DomainOperationQuestion
import com.todokanai.composepracticenew.model.PasswordAnswer as DomainPasswordAnswer
import com.todokanai.composepracticenew.model.PasswordQuestion as DomainPasswordQuestion

/** 진행 중인 작업이 멈추고 답을 기다리는 질문(표시용). 화면 호스트가 종류별 다이얼로그로 하나씩 묻는다. */
sealed interface OperationQuestion {
    /** 질문 식별자. 답할 때 어느 질문인지 가리킨다. */
    val id: Long

    companion object {
        /** domain [DomainOperationQuestion]을 표시용 질문으로 바꾼다. */
        fun from(question: DomainOperationQuestion): OperationQuestion = when (question) {
            is DomainConflictQuestion -> ConflictQuestion.from(question)
            is DomainPasswordQuestion -> PasswordQuestion.from(question)
        }
    }
}

/**
 * 압축 해제 중 암호화된 압축 파일의 비밀번호를 기다리는 질문(표시용).
 * @param id 질문 식별자
 * @param operationType 질문한 작업의 종류
 * @param archiveName 비밀번호를 묻는 압축 파일의 이름
 * @param prompt 묻는 이유(처음·틀림·틀렸거나 손상)
 */
@Immutable
data class PasswordQuestion(
    override val id: Long,
    val operationType: FileOperationType,
    val archiveName: String,
    val prompt: ArchivePasswordPrompt,
) : OperationQuestion {
    companion object {
        /** domain [DomainPasswordQuestion]을 표시용 질문으로 바꾼다. */
        fun from(question: DomainPasswordQuestion) = PasswordQuestion(
            id = question.id,
            operationType = question.operationType,
            archiveName = question.archiveName,
            prompt = question.prompt,
        )
    }
}

/** 사용자가 비밀번호 질문에 준 답. */
sealed interface PasswordAnswer {
    /** UseCase 호출 시 domain 답으로 바꾼다. */
    fun toDomain(): DomainPasswordAnswer

    /**
     * 입력한 비밀번호로 푼다.
     * @param password 입력한 비밀번호(비어 있지 않다)
     */
    data class Enter(val password: String) : PasswordAnswer {
        override fun toDomain() = DomainPasswordAnswer.Enter(password)

        // 비밀번호가 로그에 찍히지 않도록 내용을 숨긴다
        override fun toString(): String = "Enter(password=***)"
    }

    /** 작업을 취소한다. 이미 푼 항목은 그대로 둔다. */
    data object Cancel : PasswordAnswer {
        override fun toDomain() = DomainPasswordAnswer.Cancel
    }
}
