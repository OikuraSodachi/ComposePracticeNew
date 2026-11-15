package com.todokanai.composepracticenew.ui.model

import androidx.compose.runtime.Immutable
import com.todokanai.composepracticenew.model.ConflictChoice
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.WriteConflictKind
import com.todokanai.composepracticenew.model.ConflictAnswer as DomainConflictAnswer
import com.todokanai.composepracticenew.model.ConflictQuestion as DomainConflictQuestion

/**
 * 진행 중인 작업이 쓰려는 자리에 같은 이름의 항목이 있어 답을 기다리는 질문(표시용).
 * @param id 질문 식별자. 답할 때 어느 질문인지 가리킨다
 * @param operationType 질문한 작업의 종류
 * @param name 겹친 항목의 이름
 * @param folderPath 겹친 항목이 놓인 폴더의 표시용 경로
 * @param kind 쓰려던 항목과 이미 있는 항목의 종류 조합
 */
@Immutable
data class ConflictQuestion(
    val id: Long,
    val operationType: FileOperationType,
    val name: String,
    val folderPath: String,
    val kind: WriteConflictKind,
) {
    companion object {
        /** domain [DomainConflictQuestion]을 표시용 질문으로 바꾼다. */
        fun from(question: DomainConflictQuestion) = ConflictQuestion(
            id = question.id,
            operationType = question.operationType,
            name = question.conflict.name,
            folderPath = question.conflict.folderPath,
            kind = question.conflict.kind,
        )
    }
}

/** 사용자가 충돌 질문에 준 답. */
sealed interface ConflictAnswer {
    /** UseCase 호출 시 domain 답으로 바꾼다. */
    fun toDomain(): DomainConflictAnswer

    /**
     * 이 겹침을 [choice]대로 처리한다.
     * @param choice 처리 방법
     * @param applyToRest true면 같은 작업에서 같은 종류의 남은 겹침에도 묻지 않고 같은 처리를 쓴다
     */
    data class Choose(val choice: ConflictChoice, val applyToRest: Boolean) : ConflictAnswer {
        override fun toDomain() = DomainConflictAnswer.Choose(choice, applyToRest)
    }

    /** 작업을 취소한다. 이미 쓴 항목은 그대로 둔다. */
    data object Cancel : ConflictAnswer {
        override fun toDomain() = DomainConflictAnswer.Cancel
    }
}
