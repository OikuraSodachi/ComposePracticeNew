package com.todokanai.composepracticenew.model

/** 여러 항목 실패 메시지에 경로를 나열할 최대 항목 수. 나머지는 "외 n개"로 줄인다. */
private const val MAX_LISTED_FAILURES = 3

/**
 * 여러 항목을 처리하는 작업에서 실패한 항목 하나.
 * @param path 작업 대상 기준 상대 경로. 빈 문자열이면 작업 대상 자신이다
 * @param reason 실패 원인 메시지. 원인을 알 수 없으면 null
 */
data class FailedItem(val path: String, val reason: String?) {

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

/**
 * 실패 목록으로 표시할 메시지를 만든다. 실패가 하나면 경로와 원인을, 여럿이면 개수와 경로 일부를 담는다.
 * @param prefix 메시지 머리말
 * @param failures 실패한 항목 목록
 * @return 표시할 메시지
 */
internal fun partialFailureMessage(prefix: String, failures: List<FailedItem>): String {
    if (failures.size == 1) {
        val failure = failures.single()
        val location = failure.path.takeIf { it.isNotEmpty() }?.let { ": $it" } ?: ""
        return prefix + location + (failure.reason?.let { " ($it)" } ?: "")
    }
    val listed = failures.take(MAX_LISTED_FAILURES).joinToString { it.path }
    val omitted = failures.size - MAX_LISTED_FAILURES
    val omittedSuffix = if (omitted > 0) " 외 ${omitted}개" else ""
    return "$prefix: ${failures.size}개 항목 ($listed$omittedSuffix)"
}
