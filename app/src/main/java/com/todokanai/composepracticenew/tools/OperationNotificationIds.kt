package com.todokanai.composepracticenew.tools

/**
 * 작업 인스턴스 ID에 프로세스를 넘어서도 겹치지 않는 알림 id를 붙인다.
 * 작업 인스턴스 ID는 프로세스마다 다시 발급되지만 알림은 프로세스보다 오래 남으므로, 인스턴스 ID를 알림 id로 그대로 쓰면 이전 프로세스가 남긴 알림을 덮어쓴다.
 * 그래서 처음 id를 붙일 때 알림함에 남은 파일 작업 알림 중 가장 큰 id를 기준으로 삼고 그 뒤부터 차례로 붙인다 — 이 프로세스의 id는 모두 기준보다 커서
 * 남은 알림과도, 서로도 겹치지 않는다. id는 알림함에 알림이 남아 있는 동안 작업마다 하나씩 커지며, Int 범위를 넘기는 경우는 지원 범위 밖이다.
 * @param activeIds 지금 알림함에 남아 있는 파일 작업 알림의 id들을 돌려주는 함수
 */
internal class OperationNotificationIds(private val activeIds: () -> Collection<Int>) {

    /** 작업 인스턴스 ID별로 붙인 알림 id. 그 작업의 알림이 끝나면([release]) 지운다. */
    private val assigned = HashMap<Int, Int>()

    /** 마지막으로 붙인 알림 id. null이면 아직 기준을 정하지 않았다. */
    private var last: Int? = null

    /**
     * 작업 [instanceId]의 알림 id를 돌려준다. 처음이면 새 id를 붙인다.
     * @param instanceId 작업 인스턴스 ID
     * @return 그 작업의 알림 id
     */
    @Synchronized
    fun idFor(instanceId: Int): Int = assigned.getOrPut(instanceId) {
        val next = (last ?: activeIds().maxOrNull() ?: 0) + 1
        last = next
        next
    }

    /**
     * 작업 [instanceId]에 붙인 알림 id를 잊는다. 그 작업의 알림을 더 띄우지 않을 때 부른다. 잊은 id는 다시 붙이지 않는다.
     * @param instanceId 작업 인스턴스 ID
     */
    @Synchronized
    fun release(instanceId: Int) {
        assigned.remove(instanceId)
    }
}
