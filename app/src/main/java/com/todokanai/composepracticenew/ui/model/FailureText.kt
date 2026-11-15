package com.todokanai.composepracticenew.ui.model

import androidx.annotation.StringRes
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.IoResult

/** 여러 항목 실패 메시지에 경로를 나열할 최대 항목 수. 나머지는 "외 n개"로 줄인다. */
private const val MAX_LISTED_FAILURES = 3

/**
 * 파일 입출력 실패의 표시 문구를 만든다(#218). 실패 종류와 원인마다 exhaustive `when`으로 문자열 리소스에 대응시킨다.
 * @param failure 표시할 실패
 * @param operationType 실패한 작업의 종류. 부분 실패의 머리말을 정한다. null이면 순간 작업이거나 작업이 아닌 조회다
 * @return 표시 문구. 원인을 알 수 없는 항목 실패면 null
 */
fun failureTextOf(failure: IoResult.Failure, operationType: FileOperationType?): UiText? = when (failure) {
    is IoResult.ItemFailed -> failure.reason?.let(::reasonTextOf)
    is IoResult.ConnectionLost -> reasonTextOf(failure.reason)
    is IoResult.PartiallyFailed -> partialFailureTextOf(UiText.Resource(partialFailureHeadingRes(operationType)), failure.failures)
}

/**
 * 실패 원인의 표시 문구를 만든다.
 * @param reason 실패 원인
 * @return 표시 문구
 */
private fun reasonTextOf(reason: FailureReason): UiText = when (reason) {
    is FailureReason.Raw -> UiText.Raw(reason.text)
    is FailureReason.RenameFailed -> namedFailureTextOf(R.string.notice_failure_prefix_rename, reason.name, reason.cause)
    is FailureReason.MakeFolderFailed -> namedFailureTextOf(R.string.notice_failure_prefix_make_folder, reason.name, reason.cause)
    FailureReason.ProtectionRefused -> UiText.Resource(R.string.error_protection_refused)
}

/**
 * 이름 붙은 항목의 실패 문구("머리말: 이름 (원인)")를 만든다.
 * @param prefixRes 머리말의 문자열 리소스 id
 * @param name 실패한 항목 이름
 * @param cause 저장소가 알린 원인. 모르면 null
 * @return 표시 문구
 */
private fun namedFailureTextOf(@StringRes prefixRes: Int, name: String, cause: FailureReason?): UiText =
    withCause(UiText.Resource(R.string.failure_at, listOf(UiText.Resource(prefixRes), name)), cause)

/**
 * 문구 뒤에 원인을 괄호로 붙인다.
 * @param text 원인을 붙일 문구
 * @param cause 붙일 원인. null이면 [text] 그대로다
 * @return 원인을 붙인 문구
 */
private fun withCause(text: UiText, cause: FailureReason?): UiText =
    cause?.let { UiText.Resource(R.string.failure_with_cause, listOf(text, reasonTextOf(it))) } ?: text

/**
 * 부분 실패의 문구를 만든다. 실패가 하나면 경로와 원인을, 여럿이면 개수·원인 요약·경로 일부를 담는다.
 * @param heading 머리말
 * @param failures 실패한 항목 목록. 비어 있지 않아야 한다
 * @return 표시 문구
 */
private fun partialFailureTextOf(heading: UiText, failures: List<FailedItem>): UiText {
    if (failures.size == 1) {
        val failure = failures.single()
        val located = if (failure.path.isEmpty()) heading else UiText.Resource(R.string.failure_at, listOf(heading, failure.path))
        return withCause(located, failure.reason)
    }
    return UiText.Resource(R.string.failure_partial_multiple, listOf(heading, failures.size, reasonSummaryOf(failures), listedPathsOf(failures)))
}

/**
 * 실패 원인을 요약한다(#217). 원인이 한 종류면 그 원인을, 여러 종류면 가장 많은 원인과 그 수, 나머지 원인의 종류 수와 항목 수를 담는다.
 * 원인은 값이 같을 때만 같은 종류로 센다. 가장 많은 원인이 여럿이면 먼저 나온 것을 쓴다.
 * @param failures 실패한 항목 목록. 비어 있지 않아야 한다
 * @return 원인 요약 문구
 */
private fun reasonSummaryOf(failures: List<FailedItem>): UiText {
    val countsByReason = failures.groupingBy { it.reason }.eachCount()
    if (countsByReason.size == 1) return reasonOrUnknownTextOf(countsByReason.keys.single())
    val (topReason, topCount) = countsByReason.entries.maxBy { it.value }
    return UiText.Resource(
        R.string.failure_reason_summary_mixed,
        listOf(reasonOrUnknownTextOf(topReason), topCount, countsByReason.size - 1, failures.size - topCount)
    )
}

/**
 * 원인의 문구를 만든다. 원인을 모르면 "원인 모름"이다.
 * @param reason 실패 원인. 모르면 null
 * @return 원인 문구
 */
private fun reasonOrUnknownTextOf(reason: FailureReason?): UiText =
    reason?.let(::reasonTextOf) ?: UiText.Resource(R.string.failure_reason_unknown)

/**
 * 실패한 항목의 경로를 최대 [MAX_LISTED_FAILURES]개 나열하고, 나머지는 "외 n개"로 줄인다.
 * @param failures 실패한 항목 목록
 * @return 나열한 경로 문구
 */
private fun listedPathsOf(failures: List<FailedItem>): UiText {
    val listed = failures.take(MAX_LISTED_FAILURES).joinToString { it.path }
    val omitted = failures.size - MAX_LISTED_FAILURES
    return if (omitted > 0) UiText.Resource(R.string.failure_listed_paths_omitted, listOf(listed, omitted)) else UiText.Raw(listed)
}

/**
 * 부분 실패의 머리말을 작업 종류로 정한다. 안쪽 수집이 아니라 작업 단위로 정해, 평탄화된 실패도 작업의 머리말로 보인다.
 * @param operationType 실패한 작업의 종류. null이면 순간 작업이거나 작업이 아닌 조회다
 * @return 머리말의 문자열 리소스 id
 */
@StringRes
private fun partialFailureHeadingRes(operationType: FileOperationType?): Int = when (operationType) {
    FileOperationType.COPY -> R.string.failure_partial_heading_copy
    FileOperationType.MOVE -> R.string.failure_partial_heading_move
    FileOperationType.DELETE -> R.string.failure_partial_heading_delete
    FileOperationType.COMPRESS -> R.string.failure_partial_heading_compress
    FileOperationType.UNZIP -> R.string.failure_partial_heading_unzip
    FileOperationType.DOWNLOAD -> R.string.failure_partial_heading_download
    FileOperationType.UPLOAD -> R.string.failure_partial_heading_upload
    null -> R.string.failure_partial_heading_instant
}
