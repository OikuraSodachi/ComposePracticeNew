package com.todokanai.composepracticenew.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todokanai.composepracticenew.compose.dialog.ArchivePasswordDialog
import com.todokanai.composepracticenew.compose.dialog.WriteConflictDialog
import com.todokanai.composepracticenew.ui.model.ConflictQuestion
import com.todokanai.composepracticenew.ui.model.PasswordAnswer
import com.todokanai.composepracticenew.ui.model.PasswordQuestion
import com.todokanai.composepracticenew.viewmodel.OperationQuestionViewModel

/**
 * 진행 중인 작업의 질문(쓰기 충돌·압축 파일 비밀번호)을 어느 화면에 있든 하나씩 묻는 섹션. 질문 수집의 리컴포즈 범위를 이 composable로 한정한다.
 * @param viewModel 답을 기다리는 작업 질문을 보고 답하는 ViewModel
 */
@Composable
internal fun OperationQuestionSection(viewModel: OperationQuestionViewModel) {
    val question by viewModel.question.collectAsStateWithLifecycle()
    // 질문 id를 키로 — 다시 묻는 비밀번호 질문도 새 질문이므로, 앞 질문에 입력하던 값이 남지 않고 빈 입력으로 시작한다
    question?.let { current ->
        key(current.id) {
            when (current) {
                is ConflictQuestion -> WriteConflictDialog(question = current, onAnswer = { answer -> viewModel.answerConflict(current, answer) })
                is PasswordQuestion -> ArchivePasswordDialog(
                    archiveName = current.archiveName,
                    prompt = current.prompt,
                    onConfirm = { password -> viewModel.answerPassword(current, PasswordAnswer.Enter(password)) },
                    onCancel = { viewModel.answerPassword(current, PasswordAnswer.Cancel) }
                )
            }
        }
    }
}
