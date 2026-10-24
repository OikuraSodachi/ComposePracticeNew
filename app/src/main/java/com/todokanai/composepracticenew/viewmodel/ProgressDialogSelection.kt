package com.todokanai.composepracticenew.viewmodel

/**
 * 진행률 다이얼로그를 어느 작업에 띄울지 정하는 상태. 한 번에 한 작업의 다이얼로그만 띄운다.
 * 새 작업이 시작되면 그중 가장 나중에 시작한 작업을 띄우고, 다이얼로그를 닫거나 그 작업이 끝나도 다른 작업으로 넘어가지 않는다.
 * 처음 상태는 [startingWith]로 만든다. 기본값을 두지 않아, 그때 이미 진행 중인 작업을 모른 채 시작할 수 없다.
 * @param shownId 다이얼로그를 띄울 작업 인스턴스 ID. null이면 띄우지 않는다
 * @param knownIds 직전까지 본 진행 중 작업 ID. 여기에 없는 작업이 진행 중이면 새로 시작한 작업이다
 */
internal data class ProgressDialogSelection(
    val shownId: Int?,
    val knownIds: Set<Int>
) {

    companion object {
        /**
         * 다이얼로그를 띄우지 않은 처음 상태를 돌려준다. [activeIds]는 이미 진행 중이던 작업이라 새로 시작한 작업으로 보지 않는다.
         * @param activeIds 상태를 만드는 시점에 진행 중인 작업 인스턴스 ID
         */
        fun startingWith(activeIds: Set<Int>): ProgressDialogSelection =
            ProgressDialogSelection(shownId = null, knownIds = activeIds)
    }

    /**
     * 진행 중인 작업 목록이 바뀌었을 때의 다음 상태를 돌려준다.
     * 새로 시작한 작업이 있으면 그중 가장 나중에 시작한 작업을 띄우고, 띄운 작업이 끝났으면 아무것도 띄우지 않는다.
     * @param activeIds 지금 진행 중인 작업 인스턴스 ID
     */
    fun onActiveIdsChanged(activeIds: Set<Int>): ProgressDialogSelection = ProgressDialogSelection(
        shownId = newestStarted(activeIds - knownIds) ?: shownId?.takeIf { it in activeIds },
        knownIds = activeIds
    )

    /**
     * [instanceId] 작업의 다이얼로그를 닫은 상태를 돌려준다. 다른 작업의 다이얼로그를 띄우지 않는다.
     * 이미 다른 작업의 다이얼로그로 바뀌었으면 그 다이얼로그는 닫지 않는다.
     * @param instanceId 사용자가 닫은 다이얼로그의 작업 인스턴스 ID
     */
    fun dismiss(instanceId: Int): ProgressDialogSelection =
        if (shownId == instanceId) copy(shownId = null) else this

    /**
     * [instanceId] 작업의 다이얼로그를 띄운 상태를 돌려준다. 그 작업이 진행 중이 아니면 화면에 나타나지 않고, 다음 목록 변경 때 지워진다.
     * @param instanceId 다이얼로그를 띄울 작업 인스턴스 ID
     */
    fun show(instanceId: Int): ProgressDialogSelection = copy(shownId = instanceId)

    /** [startedIds] 중 가장 나중에 시작한 작업 ID. 작업 ID는 발급 순서대로 커지므로 가장 큰 ID다. */
    private fun newestStarted(startedIds: Set<Int>): Int? = startedIds.maxOrNull()
}
