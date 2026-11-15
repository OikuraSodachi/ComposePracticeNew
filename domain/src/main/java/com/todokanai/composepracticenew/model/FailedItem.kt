package com.todokanai.composepracticenew.model

/** 여러 항목 실패 메시지에 경로를 나열할 최대 항목 수. 나머지는 "외 n개"로 줄인다. */
private const val MAX_LISTED_FAILURES = 3

/**
 * 여러 항목을 처리하는 작업에서 실패한 항목 하나.
 * [path]는 실패 메시지에만 쓰는 표시용 경로다. 저장소에 다시 넘기지 않으므로 저장소의 경로 규칙과 무관하게 이름들을 '/'로 잇는다.
 * @param path 작업 대상 기준 상대 표시 경로. 빈 문자열이면 작업 대상 자신이다
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

/** 원인을 알 수 없는 실패를 원인별로 셀 때 쓰는 표시 문구. */
private const val UNKNOWN_REASON = "원인 모름"

/**
 * 실패 목록으로 표시할 메시지를 만든다. 실패가 하나면 경로와 원인을, 여럿이면 개수·원인 요약·경로 일부를 담는다.
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
    return "$prefix: ${failures.size}개 항목 — ${reasonSummary(failures)} (${listedPaths(failures)})"
}

/**
 * 실패 원인을 요약한다(#217). 원인이 한 종류면 그 원인을, 여러 종류면 가장 많은 원인과 그 수, 나머지 원인의 종류 수와 항목 수를 담는다.
 * 원인은 문구가 같을 때만 같은 종류로 센다. 가장 많은 원인이 여럿이면 먼저 나온 것을 쓴다.
 * @param failures 실패한 항목 목록. 비어 있지 않아야 한다
 * @return 원인 요약 문구
 */
private fun reasonSummary(failures: List<FailedItem>): String {
    val countsByReason = failures.groupingBy { it.reason ?: UNKNOWN_REASON }.eachCount()
    if (countsByReason.size == 1) return countsByReason.keys.single()
    val (topReason, topCount) = countsByReason.entries.maxBy { it.value }
    return "$topReason ${topCount}개, 그 밖의 원인 ${countsByReason.size - 1}종 ${failures.size - topCount}개"
}

/**
 * 실패한 항목의 경로를 최대 [MAX_LISTED_FAILURES]개 나열하고, 나머지는 "외 n개"로 줄인다.
 * @param failures 실패한 항목 목록
 * @return 나열한 경로 문구
 */
private fun listedPaths(failures: List<FailedItem>): String {
    val listed = failures.take(MAX_LISTED_FAILURES).joinToString { it.path }
    val omitted = failures.size - MAX_LISTED_FAILURES
    val omittedSuffix = if (omitted > 0) " 외 ${omitted}개" else ""
    return listed + omittedSuffix
}
