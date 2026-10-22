package com.todokanai.composepracticenew.model

/** 여러 항목 실패 메시지에 경로를 나열할 최대 항목 수. 나머지는 "외 n개"로 줄인다. */
private const val MAX_LISTED_FAILURES = 3

/**
 * 여러 항목을 처리하는 작업에서 실패한 항목 하나.
 * @param path 작업 대상 기준 상대 경로. 빈 문자열이면 작업 대상 자신이다
 * @param cause 실패 원인 예외
 */
class FailedItem(val path: String, val cause: Throwable) {

    /**
     * 이 실패를 바깥 작업의 항목 [parentPath] 아래 경로로 옮긴다. 중첩된 부분 실패를 평탄화할 때 쓴다.
     * @param parentPath 바깥 작업에서 이 실패를 품은 항목의 경로
     * @return [parentPath] 기준 경로를 가진 같은 실패
     */
    fun under(parentPath: String): FailedItem = FailedItem(joinedPath(parentPath, path), cause)

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
 * 여러 항목을 처리하는 작업에서 일부 항목이 실패했음을 알리는 예외.
 * 실패를 문자열이 아닌 목록으로 담아, 바깥 작업이 이 예외를 감싸지 않고 실패 목록을 합칠 수 있게 한다(중첩 평탄화).
 * 메시지는 실패 목록으로부터 한 번만 만든다. 원인 예외는 suppressed로도 붙여 디버깅 정보를 남긴다.
 * @param failurePrefix 메시지 머리말(예: "삭제 실패")
 * @param failures 실패한 항목 목록. 비어 있지 않아야 한다
 */
class PartialFailureException(
    val failurePrefix: String,
    val failures: List<FailedItem>
) : Exception(partialFailureMessage(failurePrefix, failures)) {
    init {
        failures.forEach { addSuppressed(it.cause) }
    }
}

/**
 * 실패 목록으로 표시할 메시지를 만든다. 실패가 하나면 경로와 원인을, 여럿이면 개수와 경로 일부를 담는다.
 * @param prefix 메시지 머리말
 * @param failures 실패한 항목 목록
 * @return 표시할 메시지
 */
private fun partialFailureMessage(prefix: String, failures: List<FailedItem>): String {
    if (failures.size == 1) {
        val failure = failures.single()
        val location = failure.path.takeIf { it.isNotEmpty() }?.let { ": $it" } ?: ""
        return prefix + location + (failure.cause.message?.let { " ($it)" } ?: "")
    }
    val listed = failures.take(MAX_LISTED_FAILURES).joinToString { it.path }
    val omitted = failures.size - MAX_LISTED_FAILURES
    val omittedSuffix = if (omitted > 0) " 외 ${omitted}개" else ""
    return "$prefix: ${failures.size}개 항목 ($listed$omittedSuffix)"
}
