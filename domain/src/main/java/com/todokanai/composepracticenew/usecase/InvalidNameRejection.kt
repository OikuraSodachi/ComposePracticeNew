package com.todokanai.composepracticenew.usecase

import com.todokanai.composepracticenew.model.FileNameError
import com.todokanai.composepracticenew.model.NamingOperation
import com.todokanai.composepracticenew.model.NoticeMessage
import com.todokanai.composepracticenew.model.OperationNoticeKind
import com.todokanai.composepracticenew.repository.OperationNoticeReporter

/**
 * 검증 결과에 위반이 있으면 거부 메시지를 보관하고 작업을 중단해야 함을 알린다. 사용자가 정한 이름을 쓰는 로컬·원격 작업이 실행 전에 함께 쓴다.
 * 메시지는 다른 실행 전 거부(copy·move의 목적지 거부 등)와 같은 [OperationNoticeReporter] 경로로 표시된다.
 * @param errors [FileNameUseCase.validate]가 반환한 위반 규칙 집합
 * @param displayName 오류 메시지에 표시할 이름
 * @param operation 이름을 쓰려던 작업
 * @return 위반이 있어 작업을 실행하지 않아야 하면 true
 */
internal fun OperationNoticeReporter.rejectsInvalidName(errors: Set<FileNameError>, displayName: String, operation: NamingOperation): Boolean {
    if (errors.isEmpty()) return false
    report(OperationNoticeKind.FAILURE, NoticeMessage.InvalidName(operation, displayName))
    return true
}
