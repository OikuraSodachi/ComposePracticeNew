package com.todokanai.composepracticenew.ui.model

import androidx.annotation.StringRes
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.model.FailedItem
import com.todokanai.composepracticenew.model.FailureReason
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.FileSystemRefusal
import com.todokanai.composepracticenew.model.InstantOperation
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ListingRefusal
import com.todokanai.composepracticenew.model.OperationKind
import com.todokanai.composepracticenew.model.RemoteStep

/** 여러 항목 실패 메시지에 경로를 나열할 최대 항목 수. 나머지는 "외 n개"로 줄인다. */
private const val MAX_LISTED_FAILURES = 3

/** 공간 부족 문구에 KB로 보이기 위해 바이트 수를 나누는 값. */
private const val BYTES_PER_KB = 1024

/**
 * 파일 입출력 실패의 표시 문구를 만든다(#218). 실패 종류와 원인마다 exhaustive `when`으로 문자열 리소스에 대응시킨다.
 * 실패 원인에는 작업 맥락이 없으므로, 머리말은 여기서 작업으로 정한다(#460).
 * @param failure 표시할 실패
 * @param kind 실패한 작업. null이면 작업이 아닌 조회다
 * @return 표시 문구. 진행 표시가 있는 작업·조회의 원인을 알 수 없는 항목 실패면 null
 */
fun failureTextOf(failure: IoResult.Failure, kind: OperationKind?): UiText? = when (kind) {
    is OperationKind.Instant -> instantFailureTextOf(failure, kind)
    is OperationKind.Tracked -> trackedFailureTextOf(failure, kind.type)
    null -> trackedFailureTextOf(failure, null)
}

/**
 * 진행 표시가 있는 작업이나 조회의 실패 문구를 만든다. 부분 실패만 작업 종류로 머리말을 붙인다.
 * @param failure 표시할 실패
 * @param operationType 실패한 작업의 종류. null이면 작업이 아닌 조회다
 * @return 표시 문구. 원인을 알 수 없는 항목 실패면 null
 */
private fun trackedFailureTextOf(failure: IoResult.Failure, operationType: FileOperationType?): UiText? = when (failure) {
    is IoResult.ItemFailed -> failure.reason?.let(::reasonTextOf)
    is IoResult.ConnectionLost -> reasonTextOf(failure.reason)
    is IoResult.PartiallyFailed ->
        partialFailureTextOf(UiText.Resource(partialFailureHeadingRes(operationType)), headingStepOf(operationType), failure.failures)
}

/**
 * 순간 작업(이름 변경·새 폴더)의 실패 문구를 만든다. 항목 실패와 연결 끊김은 "머리말: 이름 (원인)"으로, 부분 실패는 같은 머리말로 항목을 나열한다.
 * @param failure 표시할 실패
 * @param kind 실패한 순간 작업
 * @return 표시 문구
 */
private fun instantFailureTextOf(failure: IoResult.Failure, kind: OperationKind.Instant): UiText {
    val prefixRes = instantPrefixRes(kind.operation)
    val headingStep = instantStepOf(kind.operation)
    return when (failure) {
        is IoResult.ItemFailed -> namedFailureTextOf(prefixRes, headingStep, kind.name, failure.reason)
        is IoResult.ConnectionLost -> namedFailureTextOf(prefixRes, headingStep, kind.name, failure.reason)
        is IoResult.PartiallyFailed -> partialFailureTextOf(UiText.Resource(prefixRes), headingStep, failure.failures)
    }
}

/**
 * 순간 작업 실패 문구의 머리말을 정한다.
 * @param operation 순간 작업의 종류
 * @return 머리말의 문자열 리소스 id
 */
@StringRes
private fun instantPrefixRes(operation: InstantOperation): Int = when (operation) {
    InstantOperation.RENAME -> R.string.notice_failure_prefix_rename
    InstantOperation.MAKE_FOLDER -> R.string.notice_failure_prefix_make_folder
}

/**
 * 순간 작업의 머리말이 말하는 원격 단계를 정한다. 원인이 같은 단계면 단계 이름을 되풀이하지 않도록 쓴다.
 * @param operation 순간 작업의 종류
 * @return 머리말이 말하는 원격 단계
 */
private fun instantStepOf(operation: InstantOperation): RemoteStep = when (operation) {
    InstantOperation.RENAME -> RemoteStep.RENAME
    InstantOperation.MAKE_FOLDER -> RemoteStep.MAKE_FOLDER
}

/**
 * 실패 원인의 표시 문구를 만든다.
 * @param reason 실패 원인
 * @return 표시 문구
 */
private fun reasonTextOf(reason: FailureReason): UiText = when (reason) {
    is FailureReason.MoveInterrupted ->
        UiText.Resource(R.string.failure_move_interrupted, listOf(reason.name, reasonOrUnknownTextOf(reason.cause, null), reason.movedCount))
    FailureReason.ProtectionRefused -> UiText.Resource(R.string.error_protection_refused)
    is FailureReason.IoFailed -> UiText.Raw(reason.detail)
    is FailureReason.FileSystemRefused -> fileSystemRefusedTextOf(reason)
    is FailureReason.FolderNotListed -> UiText.Resource(listingRefusalRes(reason.kind))
    FailureReason.FolderUnreadable -> UiText.Resource(R.string.failure_folder_unreadable)
    is FailureReason.NotEnoughSpace ->
        UiText.Resource(R.string.failure_not_enough_space, listOf(reason.neededBytes / BYTES_PER_KB, reason.freeBytes / BYTES_PER_KB))
    FailureReason.NoFreeSpace -> UiText.Resource(R.string.failure_no_free_space)
    is FailureReason.UnsupportedLink -> withLocation(UiText.Resource(R.string.failure_unsupported_link), reason.location)
    FailureReason.UnsafeArchiveEntryPath -> UiText.Resource(R.string.failure_unsafe_archive_entry_path)
    is FailureReason.UnsupportedArchiveFormat -> UiText.Resource(R.string.failure_unsupported_archive_format, listOf(reason.name))
    is FailureReason.MissingArchiveVolume -> UiText.Resource(R.string.failure_missing_archive_volume, listOf(reason.archiveName, reason.volumeName))
    FailureReason.NothingToCompress -> UiText.Resource(R.string.failure_nothing_to_compress)
    is FailureReason.NameTaken -> UiText.Resource(R.string.failure_name_taken, listOf(reason.name))
    is FailureReason.KindConflict -> UiText.Resource(R.string.failure_kind_conflict, listOf(reason.location))
    is FailureReason.MergedSourceNotRemoved -> UiText.Resource(R.string.failure_merged_source_not_removed, listOf(reason.name))
    FailureReason.UnsupportedPath -> UiText.Resource(R.string.failure_unsupported_path)
    is FailureReason.ServerRefused -> serverRefusedTextOf(reason)
    is FailureReason.RemoteStepFailed -> remoteStepTextOf(reason.step, reasonTextOf(reason.cause))
    is FailureReason.TransferUnconfirmed -> UiText.Resource(R.string.failure_transfer_unconfirmed, listOf(reason.reply))
    is FailureReason.TransferFailed -> reason.detail?.let { UiText.Raw(it) } ?: UiText.Resource(R.string.failure_transfer_failed)
    is FailureReason.DataConnectionFailed -> UiText.Resource(R.string.failure_data_connection, listOf(reason.detail))
    FailureReason.NotConnected -> UiText.Resource(R.string.failure_not_connected)
    FailureReason.ConnectionClosed -> UiText.Resource(R.string.failure_connection_closed)
    is FailureReason.NoResponse -> UiText.Resource(R.string.failure_no_response, listOf(reason.detail))
    FailureReason.ListingFormatUnknown -> UiText.Resource(R.string.failure_listing_format_unknown)
    is FailureReason.UnreadableListingEntry -> UiText.Resource(R.string.failure_unreadable_listing_entry, listOf(reason.line))
    is FailureReason.TooManyEntries ->
        withCycleNote(UiText.Resource(R.string.failure_too_many_entries, listOf(reason.path)), reason.cycleUndecidable)
    is FailureReason.FolderCycle -> UiText.Resource(R.string.failure_folder_cycle, listOf(reason.path))
    is FailureReason.PathTooLong ->
        withCycleNote(UiText.Resource(R.string.failure_path_too_long, listOf(reason.limitBytes, reason.path)), reason.cycleUndecidable)
    is FailureReason.Unexpected -> UiText.Raw(reason.detail)
}

/**
 * 파일 시스템 거부의 문구("항목: 거부 종류")를 만든다.
 * @param reason 파일 시스템 거부
 * @return 표시 문구. 항목을 모르면 거부 종류만이다
 */
private fun fileSystemRefusedTextOf(reason: FailureReason.FileSystemRefused): UiText {
    val kind = when (val refusal = reason.kind) {
        FileSystemRefusal.AlreadyExists -> UiText.Resource(R.string.failure_fs_already_exists)
        FileSystemRefusal.AccessDenied -> UiText.Resource(R.string.failure_fs_access_denied)
        FileSystemRefusal.NotFound -> UiText.Resource(R.string.failure_fs_not_found)
        FileSystemRefusal.NotEmpty -> UiText.Resource(R.string.failure_fs_not_empty)
        FileSystemRefusal.NotFolder -> UiText.Resource(R.string.failure_fs_not_folder)
        is FileSystemRefusal.Other -> UiText.Resource(R.string.failure_fs_other, listOf(refusal.exceptionName))
    }
    return reason.target?.let { UiText.Resource(R.string.failure_at, listOf(it, kind)) } ?: kind
}

/**
 * 폴더 안을 나열하지 못한 종류의 문구를 정한다.
 * @param kind 나열하지 못한 종류
 * @return 문구의 문자열 리소스 id
 */
@StringRes
private fun listingRefusalRes(kind: ListingRefusal): Int = when (kind) {
    ListingRefusal.NOT_FOLDER -> R.string.failure_listing_not_folder
    ListingRefusal.ACCESS_DENIED -> R.string.failure_listing_access_denied
    ListingRefusal.NOT_FOUND -> R.string.failure_listing_not_found
}

/**
 * 원격 서버 거부의 문구("단계 실패: 응답")를 만든다. 숨은 항목이 남아 거부됐을 수 있으면 그 설명을 덧붙인다.
 * @param reason 원격 서버 거부
 * @return 표시 문구
 */
private fun serverRefusedTextOf(reason: FailureReason.ServerRefused): UiText = remoteStepTextOf(reason.step, serverReplyTextOf(reason))

/**
 * 원격 서버 거부의 응답 문구를 만든다. 숨은 항목이 남아 거부됐을 수 있으면 그 설명을 덧붙인다.
 * @param reason 원격 서버 거부
 * @return 응답 문구(단계 이름 없음)
 */
private fun serverReplyTextOf(reason: FailureReason.ServerRefused): UiText =
    if (reason.mayHaveHiddenEntries) UiText.Resource(R.string.failure_hidden_entries_note, listOf(reason.reply)) else UiText.Raw(reason.reply)

/**
 * 머리말 아래에 붙는 원인의 문구를 만든다. 원인이 머리말과 같은 원격 단계의 실패면 단계 이름을 빼고 원인만 보인다
 * — "이름 변경 실패: b (이름 변경 실패: 550)"처럼 같은 말이 겹치지 않도록.
 * @param cause 실패 원인
 * @param headingStep 머리말이 이미 말하는 원격 단계. 없으면 null
 * @return 원인 문구
 */
private fun causeTextOf(cause: FailureReason, headingStep: RemoteStep?): UiText = when {
    cause is FailureReason.ServerRefused && cause.step == headingStep -> serverReplyTextOf(cause)
    cause is FailureReason.RemoteStepFailed && cause.step == headingStep -> reasonTextOf(cause.cause)
    else -> reasonTextOf(cause)
}

/**
 * 원격 단계 실패의 문구("단계 실패: 원인")를 만든다.
 * @param step 실패한 단계
 * @param cause 원인 문구
 * @return 표시 문구
 */
private fun remoteStepTextOf(step: RemoteStep, cause: UiText): UiText =
    UiText.Resource(R.string.failure_remote_step, listOf(UiText.Resource(remoteStepRes(step)), cause))

/**
 * 원격 작업 단계의 이름을 정한다.
 * @param step 원격 작업 단계
 * @return 이름의 문자열 리소스 id
 */
@StringRes
private fun remoteStepRes(step: RemoteStep): Int = when (step) {
    RemoteStep.OPEN_FOLDER -> R.string.remote_step_open_folder
    RemoteStep.LIST_FOLDER -> R.string.remote_step_list_folder
    RemoteStep.RENAME -> R.string.remote_step_rename
    RemoteStep.DELETE_FILE -> R.string.remote_step_delete_file
    RemoteStep.MAKE_FOLDER -> R.string.remote_step_make_folder
    RemoteStep.REMOVE_FOLDER -> R.string.remote_step_remove_folder
    RemoteStep.DOWNLOAD -> R.string.remote_step_download
    RemoteStep.UPLOAD -> R.string.remote_step_upload
    RemoteStep.PREPARE_TRANSFER -> R.string.remote_step_prepare_transfer
    RemoteStep.OPEN_SESSION -> R.string.remote_step_open_session
}

/**
 * 문구 뒤에 자리를 괄호로 붙인다.
 * @param text 자리를 붙일 문구
 * @param location 붙일 자리. null이면 [text] 그대로다
 * @return 자리를 붙인 문구
 */
private fun withLocation(text: UiText, location: String?): UiText =
    location?.let { UiText.Resource(R.string.failure_with_cause, listOf(text, it)) } ?: text

/**
 * 폴더 순환을 판별할 수 없었으면 문구 뒤에 그 설명을 붙인다.
 * @param text 설명을 붙일 문구
 * @param cycleUndecidable 서버의 목록으로 폴더 순환을 판별할 수 없었는지
 * @return 설명을 붙인 문구
 */
private fun withCycleNote(text: UiText, cycleUndecidable: Boolean): UiText =
    if (cycleUndecidable) UiText.Resource(R.string.failure_cycle_undecidable, listOf(text)) else text

/**
 * 이름 붙은 항목의 실패 문구("머리말: 이름 (원인)")를 만든다.
 * @param prefixRes 머리말의 문자열 리소스 id
 * @param headingStep 머리말이 말하는 원격 단계
 * @param name 실패한 항목 이름
 * @param cause 저장소가 알린 원인. 모르면 null
 * @return 표시 문구
 */
private fun namedFailureTextOf(@StringRes prefixRes: Int, headingStep: RemoteStep, name: String, cause: FailureReason?): UiText =
    withCause(UiText.Resource(R.string.failure_at, listOf(UiText.Resource(prefixRes), name)), headingStep, cause)

/**
 * 문구 뒤에 원인을 괄호로 붙인다.
 * @param text 원인을 붙일 문구
 * @param headingStep [text]가 이미 말하는 원격 단계. 없으면 null
 * @param cause 붙일 원인. null이면 [text] 그대로다
 * @return 원인을 붙인 문구
 */
private fun withCause(text: UiText, headingStep: RemoteStep?, cause: FailureReason?): UiText =
    cause?.let { UiText.Resource(R.string.failure_with_cause, listOf(text, causeTextOf(it, headingStep))) } ?: text

/**
 * 부분 실패의 문구를 만든다. 실패가 하나면 경로와 원인을, 여럿이면 개수·원인 요약·경로 일부를 담는다.
 * @param heading 머리말
 * @param headingStep 머리말이 말하는 원격 단계. 없으면 null
 * @param failures 실패한 항목 목록. 비어 있지 않아야 한다
 * @return 표시 문구
 */
private fun partialFailureTextOf(heading: UiText, headingStep: RemoteStep?, failures: List<FailedItem>): UiText {
    if (failures.size == 1) {
        val failure = failures.single()
        val located = if (failure.path.isEmpty()) heading else UiText.Resource(R.string.failure_at, listOf(heading, failure.path))
        return withCause(located, headingStep, failure.reason)
    }
    return UiText.Resource(
        R.string.failure_partial_multiple,
        listOf(heading, failures.size, reasonSummaryOf(failures, headingStep), listedPathsOf(failures))
    )
}

/**
 * 실패 원인을 요약한다(#217). 원인이 한 종류면 그 원인을, 여러 종류면 가장 많은 원인과 그 수, 나머지 원인의 종류 수와 항목 수를 담는다.
 * 원인은 값이 같을 때만 같은 종류로 센다. 가장 많은 원인이 여럿이면 먼저 나온 것을 쓴다.
 * @param failures 실패한 항목 목록. 비어 있지 않아야 한다
 * @param headingStep 머리말이 말하는 원격 단계. 없으면 null
 * @return 원인 요약 문구
 */
private fun reasonSummaryOf(failures: List<FailedItem>, headingStep: RemoteStep?): UiText {
    val countsByReason = failures.groupingBy { it.reason }.eachCount()
    if (countsByReason.size == 1) return reasonOrUnknownTextOf(countsByReason.keys.single(), headingStep)
    val (topReason, topCount) = countsByReason.entries.maxBy { it.value }
    return UiText.Resource(
        R.string.failure_reason_summary_mixed,
        listOf(reasonOrUnknownTextOf(topReason, headingStep), topCount, countsByReason.size - 1, failures.size - topCount)
    )
}

/**
 * 원인의 문구를 만든다. 원인을 모르면 "원인 모름"이다.
 * @param reason 실패 원인. 모르면 null
 * @param headingStep 머리말이 말하는 원격 단계. 없으면 null
 * @return 원인 문구
 */
private fun reasonOrUnknownTextOf(reason: FailureReason?, headingStep: RemoteStep?): UiText =
    reason?.let { causeTextOf(it, headingStep) } ?: UiText.Resource(R.string.failure_reason_unknown)

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
 * @param operationType 실패한 작업의 종류. null이면 작업이 아닌 조회다
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
    null -> R.string.failure_partial_heading_generic
}

/**
 * 부분 실패의 머리말이 말하는 원격 단계를 작업 종류로 정한다. 머리말과 단계 이름이 같은 말일 때만 단계를 돌려준다.
 * @param operationType 실패한 작업의 종류. null이면 작업이 아닌 조회다
 * @return 머리말이 말하는 원격 단계. 없으면 null
 */
private fun headingStepOf(operationType: FileOperationType?): RemoteStep? = when (operationType) {
    FileOperationType.DOWNLOAD -> RemoteStep.DOWNLOAD
    FileOperationType.UPLOAD -> RemoteStep.UPLOAD
    FileOperationType.COPY, FileOperationType.MOVE, FileOperationType.DELETE, FileOperationType.COMPRESS, FileOperationType.UNZIP, null -> null
}
