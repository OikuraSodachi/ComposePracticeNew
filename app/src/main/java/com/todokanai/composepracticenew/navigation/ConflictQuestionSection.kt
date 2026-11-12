package com.todokanai.composepracticenew.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todokanai.composepracticenew.compose.dialog.WriteConflictDialog
import com.todokanai.composepracticenew.viewmodel.ConflictQuestionViewModel

/**
 * 진행 중인 작업의 충돌 질문을 어느 화면에 있든 묻는 섹션. 질문 수집의 리컴포즈 범위를 이 composable로 한정한다.
 * @param viewModel 답을 기다리는 충돌 질문을 보고 답하는 ViewModel
 */
@Composable
internal fun ConflictQuestionSection(viewModel: ConflictQuestionViewModel) {
    val question by viewModel.question.collectAsStateWithLifecycle()
    question?.let { current ->
        WriteConflictDialog(question = current, onAnswer = { answer -> viewModel.answer(current, answer) })
    }
}
