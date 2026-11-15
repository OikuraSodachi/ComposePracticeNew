package com.todokanai.composepracticenew.model

/**
 * 여러 항목을 처리하는 작업에서 실패한 항목 하나.
 * [path]는 실패 메시지에만 쓰는 표시용 경로다. 저장소에 다시 넘기지 않으므로 저장소의 경로 규칙과 무관하게 이름들을 '/'로 잇는다.
 * @param path 작업 대상 기준 상대 표시 경로. 빈 문자열이면 작업 대상 자신이다
 * @param reason 실패 원인. 원인을 알 수 없으면 null
 */
data class FailedItem(val path: String, val reason: FailureReason?) {

    /**
     * 이 실패를 바깥 작업의 항목 [parentPath] 아래 경로로 옮긴다. 중첩된 부분 실패를 평탄화할 때 쓴다.
     * @param parentPath 바깥 작업에서 이 실패를 품은 항목의 경로
     * @return [parentPath] 기준 경로를 가진 같은 실패
     */
    fun under(parentPath: String): FailedItem = copy(path = joinedPath(parentPath, path))

    /**
     * 바깥 경로와 안쪽 상대 경로를 '/'로 잇는다. 어느 쪽이 빈 문자열(작업 대상 자신)이면 다른 쪽을 그대로 쓴다.
     * @param parentPath 바깥 경로
     * @param childPath 안쪽 상대 경로
     * @return 이은 경로
     */
    private fun joinedPath(parentPath: String, childPath: String): String = when {
        childPath.isEmpty() -> parentPath
        parentPath.isEmpty() -> childPath
        else -> "$parentPath/$childPath"
    }
}
