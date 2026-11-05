package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.PendingNotice
import com.todokanai.composepracticenew.model.PreferenceFailure
import kotlinx.coroutines.flow.StateFlow

/** 앱 설정 저장소의 실패를 알려준다. 설정 저장 함수는 결과를 돌려주지 않으므로 실패는 이 알림으로만 드러난다. */
interface PreferenceFailureRepository : NoticeInbox<PreferenceFailure> {
    /**
     * 아직 화면에 알리지 않은 실패(일어난 순서). 화면이 꺼내기([claim]) 전까지 남아, 화면이 보이기 전에 일어난 실패도 잃지 않는다.
     * [PreferenceFailure.READ]는 저장소가 다시 읽히면 꺼내기 전이라도 빠지고, 꺼낸 뒤라면 돌려놓지([release]) 않는다 — 더는 기본값으로 보이고 있지 않으므로.
     */
    override val pending: StateFlow<List<PendingNotice<PreferenceFailure>>>
}
