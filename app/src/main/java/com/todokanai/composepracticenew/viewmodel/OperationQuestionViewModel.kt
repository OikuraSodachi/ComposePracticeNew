package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.ui.model.ConflictAnswer
import com.todokanai.composepracticenew.ui.model.ConflictQuestion
import com.todokanai.composepracticenew.ui.model.OperationQuestion
import com.todokanai.composepracticenew.ui.model.PasswordAnswer
import com.todokanai.composepracticenew.ui.model.PasswordQuestion
import com.todokanai.composepracticenew.usecase.OperationQuestionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 진행 중인 파일 작업이 실행 중에 묻는 질문(쓰기 충돌·압축 파일 비밀번호)을 어느 화면에 있든 묻는 ViewModel. Activity 스코프로 화면 호스트가 하나만 둔다.
 * @param operationQuestionUseCase 답을 기다리는 작업 질문을 보고 답하는 UseCase
 */
@HiltViewModel
class OperationQuestionViewModel @Inject constructor(
    private val operationQuestionUseCase: OperationQuestionUseCase,
) : ViewModel() {

    /** 지금 물을 질문. 없으면 null. 작업이 화면 밖에서 물은 질문도 화면이 다시 보일 때 여기서 나온다. */
    val question: StateFlow<OperationQuestion?> = operationQuestionUseCase.current
        .map { current -> current?.let(OperationQuestion::from) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = operationQuestionUseCase.current.value?.let(OperationQuestion::from)
        )

    /**
     * 충돌 질문 [question]에 [answer]로 답한다. 이미 답했거나 거둔 질문이면 아무것도 하지 않는다.
     * @param question 답할 질문
     * @param answer 사용자의 답
     */
    fun answerConflict(question: ConflictQuestion, answer: ConflictAnswer) = operationQuestionUseCase.answerConflict(question.id, answer.toDomain())

    /**
     * 비밀번호 질문 [question]에 [answer]로 답한다. 이미 답했거나 거둔 질문이면 아무것도 하지 않는다.
     * @param question 답할 질문
     * @param answer 사용자의 답
     */
    fun answerPassword(question: PasswordQuestion, answer: PasswordAnswer) = operationQuestionUseCase.answerPassword(question.id, answer.toDomain())
}
