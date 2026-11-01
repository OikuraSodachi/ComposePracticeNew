package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.PreferenceFailure
import com.todokanai.composepracticenew.repository.PreferenceFailureRepository
import kotlinx.coroutines.flow.Flow

/**
 * 앱 설정 저장소의 실패를 화면에 알리기 위해 제공하는 UseCase.
 * @param repo 설정 저장소의 실패를 알려주는 PreferenceFailureRepository
 */
class PreferenceFailureUseCase(private val repo: PreferenceFailureRepository) {
    /** 설정 저장소에서 실패가 일어날 때마다 방출된다. 수집하는 쪽은 하나여야 한다. */
    val failures: Flow<PreferenceFailure> = repo.failures
}
