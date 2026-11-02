package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.PreferenceFailureNotice
import kotlinx.coroutines.flow.StateFlow

/** 앱 설정 저장소의 실패를 알려준다. 설정 저장 함수는 결과를 돌려주지 않으므로 실패는 이 알림으로만 드러난다. */
interface PreferenceFailureRepository {
    /**
     * 아직 화면에 알리지 않은 실패(일어난 순서). 화면이 꺼내기([claim]) 전까지 남아, 화면이 보이기 전에 일어난 실패도 잃지 않는다.
     * [com.todokanai.composepracticenew.model.PreferenceFailure.READ]는 저장소가 다시 읽히면 꺼내기 전이라도 빠진다 — 더는 기본값으로 보이고 있지 않으므로.
     */
    val pending: StateFlow<List<PreferenceFailureNotice>>

    /**
     * [id]의 알림을 [pending]에서 꺼낸다. 꺼낸 쪽만 알린다 — 여러 화면이 같은 알림을 보아도 한 번만 알리도록.
     * @param id 꺼낼 알림의 [PreferenceFailureNotice.id]
     * @return 이 호출이 꺼냈으면 true. 이미 꺼냈거나 빠진 알림이면 false
     */
    fun claim(id: Long): Boolean
}
