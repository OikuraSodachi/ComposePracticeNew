package com.todokanai.composepracticenew.viewmodel

import androidx.lifecycle.ViewModel
import com.todokanai.composepracticenew.model.ConflictAnswer
import com.todokanai.composepracticenew.model.ConflictQuestion
import com.todokanai.composepracticenew.usecase.ConflictQuestionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * 진행 중인 파일 작업이 쓰기 중에 만난 같은 이름의 항목을 어느 화면에 있든 묻는 ViewModel. Activity 스코프로 화면 호스트가 하나만 둔다.
 * @param conflictQuestionUseCase 답을 기다리는 충돌 질문을 보고 답하는 UseCase
 */
@HiltViewModel
class ConflictQuestionViewModel @Inject constructor(
    private val conflictQuestionUseCase: ConflictQuestionUseCase,
) : ViewModel() {

    /** 지금 물을 질문. 없으면 null. 작업이 화면 밖에서 물은 질문도 화면이 다시 보일 때 여기서 나온다. */
    val question: StateFlow<ConflictQuestion?> = conflictQuestionUseCase.current

    /**
     * [question]에 [answer]로 답한다.
     * @param question 답할 질문
     * @param answer 사용자의 답
     */
    fun answer(question: ConflictQuestion, answer: ConflictAnswer) = conflictQuestionUseCase.answer(question, answer)
}
