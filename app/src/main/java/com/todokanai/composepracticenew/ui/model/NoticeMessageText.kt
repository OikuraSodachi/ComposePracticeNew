package com.todokanai.composepracticenew.ui.model

import androidx.annotation.StringRes
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.model.FileOperationType
import com.todokanai.composepracticenew.model.LeftoverCause
import com.todokanai.composepracticenew.model.NamingOperation
import com.todokanai.composepracticenew.model.NoticeMessage
import com.todokanai.composepracticenew.model.OpenFailure
import com.todokanai.composepracticenew.model.OperationKind
import com.todokanai.composepracticenew.model.QuestionKind
import com.todokanai.composepracticenew.model.RelocateOperation

/**
 * 파일 작업 메시지의 표시 문구를 정한다(#218). 메시지 종류마다 exhaustive `when`으로 문자열 리소스에 대응시켜, 종류가 늘면 컴파일러가 알린다.
 * @param message 알릴 메시지
 * @return 표시 문구
 */
fun noticeTextOf(message: NoticeMessage): UiText = when (message) {
    is NoticeMessage.Completed -> UiText.Resource(completionTextRes(message.kind))
    is NoticeMessage.Cancelled -> cancelledTextOf(message.kind)
    is NoticeMessage.OperationFailed -> failureTextOf(message.failure, message.kind) ?: UiText.Resource(R.string.notice_unknown_failure)
    is NoticeMessage.UnansweredQuestion -> UiText.Resource(
        unansweredQuestionRes(message.kind),
        listOf(UiText.Resource(operationNameRes(message.operationType)), message.name, message.waitHours)
    )
    is NoticeMessage.InvalidName -> UiText.Resource(R.string.notice_invalid_name, listOf(UiText.Resource(namingPrefixRes(message.operation)), message.name))
    is NoticeMessage.DestinationInsideSource ->
        UiText.Resource(R.string.notice_destination_inside_source, listOf(UiText.Resource(relocatePrefixRes(message.operation)), message.sourceName))
    is NoticeMessage.SameDestination -> UiText.Resource(R.string.notice_same_destination, listOf(UiText.Resource(relocatePrefixRes(message.operation))))
    is NoticeMessage.UnsupportedArchiveFormat -> UiText.Resource(R.string.notice_unsupported_archive_format, listOf(message.extension))
    is NoticeMessage.OpenFailed -> UiText.Resource(openFailureRes(message.reason), listOf(message.name))
    NoticeMessage.DestinationUnverified -> UiText.Resource(R.string.error_destination_unverified)
    NoticeMessage.NoRemoteSession -> UiText.Resource(R.string.notice_no_remote_session)
    is NoticeMessage.FolderUnreadable -> UiText.Resource(R.string.notice_folder_unreadable, listOf(message.name))
    is NoticeMessage.UploadLeftover -> UiText.Resource(R.string.notice_upload_leftover, listOf(message.path, leftoverCauseTextOf(message.cause)))
}

/**
 * 작업 완료 문구를 정한다. 화면 알림과 시스템 완료 알림이 함께 쓴다.
 * @param kind 마친 작업
 * @return 완료 문구의 문자열 리소스 id
 */
@StringRes
fun completionTextRes(kind: OperationKind): Int = when (kind) {
    is OperationKind.Tracked -> when (kind.type) {
        FileOperationType.MOVE -> R.string.noti_move_complete
        FileOperationType.DELETE, FileOperationType.REMOTE_DELETE -> R.string.noti_delete_complete
        FileOperationType.DOWNLOAD -> R.string.noti_download_complete
        FileOperationType.UPLOAD -> R.string.noti_upload_complete
        FileOperationType.COPY, FileOperationType.COMPRESS, FileOperationType.UNZIP -> R.string.noti_complete
    }
    is OperationKind.Instant -> R.string.noti_complete
}

/**
 * 취소 문구를 만든다. 취소는 쓰다 만 파일만 지우고 이미 끝난 항목은 되돌리지 않으므로, 그 사실을 함께 알린다.
 * @param kind 취소한 작업
 * @return 취소 문구
 */
private fun cancelledTextOf(kind: OperationKind): UiText = when (kind) {
    is OperationKind.Tracked ->
        UiText.Resource(R.string.notice_cancelled, listOf(UiText.Resource(operationNameRes(kind.type)), UiText.Resource(cancelRemainderRes(kind.type))))
    is OperationKind.Instant -> UiText.Resource(R.string.notice_cancelled_instant)
}

/**
 * 메시지에 쓸 작업 이름을 정한다.
 * @param operationType 작업 종류
 * @return 작업 이름의 문자열 리소스 id
 */
@StringRes
private fun operationNameRes(operationType: FileOperationType): Int = when (operationType) {
    FileOperationType.COPY -> R.string.op_name_copy
    FileOperationType.MOVE -> R.string.op_name_move
    FileOperationType.DELETE, FileOperationType.REMOTE_DELETE -> R.string.op_name_delete
    FileOperationType.COMPRESS -> R.string.op_name_compress
    FileOperationType.UNZIP -> R.string.op_name_unzip
    FileOperationType.DOWNLOAD -> R.string.op_name_download
    FileOperationType.UPLOAD -> R.string.op_name_upload
}

/**
 * 작업을 취소했을 때 이미 처리한 항목이 어떻게 남는지 알리는 문구를 정한다.
 * @param operationType 작업 종류
 * @return 남는 항목에 대한 문구의 문자열 리소스 id
 */
@StringRes
private fun cancelRemainderRes(operationType: FileOperationType): Int = when (operationType) {
    FileOperationType.COPY -> R.string.notice_cancel_remainder_copy
    FileOperationType.MOVE -> R.string.notice_cancel_remainder_move
    FileOperationType.DELETE, FileOperationType.REMOTE_DELETE -> R.string.notice_cancel_remainder_delete
    FileOperationType.COMPRESS -> R.string.notice_cancel_remainder_compress
    FileOperationType.UNZIP -> R.string.notice_cancel_remainder_unzip
    FileOperationType.DOWNLOAD -> R.string.notice_cancel_remainder_download
    FileOperationType.UPLOAD -> R.string.notice_cancel_remainder_upload
}

/**
 * 이름 거부 메시지의 머리말을 정한다.
 * @param operation 이름을 쓰려던 작업
 * @return 머리말의 문자열 리소스 id
 */
@StringRes
private fun namingPrefixRes(operation: NamingOperation): Int = when (operation) {
    NamingOperation.RENAME -> R.string.notice_failure_prefix_rename
    NamingOperation.MAKE_FOLDER -> R.string.notice_failure_prefix_make_folder
    NamingOperation.COMPRESS -> R.string.notice_failure_prefix_compress
}

/**
 * 목적지 거부 메시지의 머리말을 정한다.
 * @param operation 실행하지 않은 작업
 * @return 머리말의 문자열 리소스 id
 */
@StringRes
private fun relocatePrefixRes(operation: RelocateOperation): Int = when (operation) {
    RelocateOperation.COPY -> R.string.notice_failure_prefix_copy
    RelocateOperation.MOVE -> R.string.notice_failure_prefix_move
}

/**
 * 파일을 열지 못한 이유의 문구를 정한다.
 * @param reason 열지 못한 이유
 * @return 문구의 문자열 리소스 id
 */
@StringRes
private fun openFailureRes(reason: OpenFailure): Int = when (reason) {
    OpenFailure.UNKNOWN_TYPE -> R.string.notice_open_unknown_type
    OpenFailure.NO_HANDLER -> R.string.notice_open_no_handler
    OpenFailure.REFUSED -> R.string.notice_open_refused
}

/**
 * 받다 만 파일을 지우지 못한 이유의 문구를 만든다.
 * @param cause 지우지 못한 이유
 * @return 이유 문구
 */
private fun leftoverCauseTextOf(cause: LeftoverCause): UiText = when (cause) {
    is LeftoverCause.Refused -> UiText.Resource(R.string.notice_leftover_refused, listOf(cause.reply))
    LeftoverCause.SessionEnded -> UiText.Resource(R.string.notice_leftover_session_ended)
}

/**
 * 답이 없어 멈춘 작업의 알림 문구를 질문 종류로 고른다.
 * @param kind 답을 받지 못한 질문의 종류
 * @return 문자열 리소스 id(인자: 작업 이름, 질문이 가리킨 이름, 기다린 시간)
 */
private fun unansweredQuestionRes(kind: QuestionKind): Int = when (kind) {
    QuestionKind.CONFLICT -> R.string.notice_unanswered_conflict
    QuestionKind.ARCHIVE_PASSWORD -> R.string.notice_unanswered_archive_password
}
