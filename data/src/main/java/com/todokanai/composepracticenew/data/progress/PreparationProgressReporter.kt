package com.todokanai.composepracticenew.data.progress

import com.todokanai.composepracticenew.model.PreparationProgress
import com.todokanai.composepracticenew.model.ProgressState

/**
 * 처리 전에 대상 트리를 훑는 준비 단계의 진행(찾은 항목 수)을 보고한다. 작업 하나의 준비 단계 전체(대상 여럿 포함)에 하나를 쓴다.
 * 진행 보고는 작업 취소를 확인하는 자리이기도 하므로, 훑는 동안에도 보고해야 취소가 듣는다 — 보고하지 않으면 훑기가 끝날 때까지 취소가 듣지 않는다(#284).
 * 항목마다 보고하면 알림 갱신이 몰리므로 [throttle]의 시간 간격마다 보고한다.
 * @param onProgress 진행 상태를 수신하는 콜백
 * @param throttle 보고 간격을 정하는 ProgressThrottle
 */
class PreparationProgressReporter internal constructor(
    private val onProgress: (ProgressState) -> Unit,
    private val throttle: ProgressThrottle = ProgressThrottle()
) {
    /** 지금까지 찾은 항목 수. */
    private var foundEntries = 0

    /**
     * 항목 하나를 찾았음을 알리고, 보고할 때가 됐으면 보고한다.
     * @param name 찾은 항목의 이름
     */
    fun onEntryFound(name: String) {
        foundEntries++
        if (throttle.shouldReport(null)) onProgress(ProgressState(preparing = PreparationProgress(foundEntries, name)))
    }
}
