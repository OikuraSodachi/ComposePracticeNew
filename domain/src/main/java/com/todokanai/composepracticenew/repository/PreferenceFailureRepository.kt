package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.PreferenceFailure
import kotlinx.coroutines.flow.Flow

/** 앱 설정 저장소의 실패를 알려준다. 설정 저장 함수는 결과를 돌려주지 않으므로 실패는 이 알림으로만 드러난다. */
interface PreferenceFailureRepository {
    /** 설정 저장소에서 실패가 일어날 때마다 방출된다. 수집하기 전에 일어난 실패는 수집을 시작하면 차례로 받는다. 수집하는 쪽은 하나여야 한다. */
    val failures: Flow<PreferenceFailure>
}
