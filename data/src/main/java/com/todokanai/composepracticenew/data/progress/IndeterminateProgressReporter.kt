package com.todokanai.composepracticenew.data.progress

import com.todokanai.composepracticenew.model.IndeterminatePhase
import com.todokanai.composepracticenew.model.ProgressPhase

/**
 * 퍼센트 없이 보이는 단계([IndeterminatePhase])의 진행을 [throttle]의 시간 간격마다 보고한다.
 * 진행 보고는 작업 취소를 확인하는 자리이기도 하므로, 그런 단계에서도 일하는 동안 계속 불러야 취소가 듣는다(#284, #418).
 * 부를 때마다 보고하면 알림 갱신이 몰리므로 간격이 지났을 때만 보고한다.
 * @param onProgress 진행 상태를 수신하는 콜백
 * @param throttle 보고 간격을 정하는 ProgressThrottle
 */
class IndeterminateProgressReporter internal constructor(
    private val onProgress: (ProgressPhase) -> Unit,
    private val throttle: ProgressThrottle = ProgressThrottle()
) {
    /**
     * 보고할 때가 됐으면 [phase]로 만든 단계를 보고한다.
     * @param phase 보고할 단계를 만드는 함수. 보고할 때만 부른다
     */
    fun report(phase: () -> IndeterminatePhase) {
        if (throttle.shouldReport(null)) onProgress(phase())
    }
}
