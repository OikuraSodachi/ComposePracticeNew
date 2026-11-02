package com.todokanai.composepracticenew.model

/**
 * 화면에 알리기를 기다리는 설정 저장소 실패 하나.
 * @param id 알림마다 다른 식별자. 화면이 이 알림을 꺼낼 때([com.todokanai.composepracticenew.repository.PreferenceFailureRepository.claim]) 쓴다
 * @param failure 일어난 실패
 */
data class PreferenceFailureNotice(val id: Long, val failure: PreferenceFailure)
