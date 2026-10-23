package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.IoResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 실패 메시지가 없을 때 표시할 메시지. */
private const val UNKNOWN_FAILURE_MESSAGE = "알 수 없는 오류"

/**
 * 파일 작업을 실행하며 완료·실패·진행 상태를 통합 관리하는 실행 엔진.
 * 작업의 실패는 결과([IoResult.Failure])로 받는다. 예외는 구현 오류이지만, 작업 하나 때문에 앱 수명 스코프가 무너지지 않도록
 * 이곳에서만 실패로 바꿔 그 작업만 끝낸다.
 * @param progressHandle 작업 인스턴스의 진행 상태 핸들
 * @param callback 완료·실패 시 알림·이벤트·갱신을 처리하는 콜백
 * @param work 실제 IO 작업 코드
 */
fun CoroutineScope.launchOperation(
    progressHandle: ProgressHandle,
    callback: OperationCallback,
    work: suspend () -> IoResult<Unit>
) {
    launch {
        try {
            when (val result = work()) {
                is IoResult.Success -> callback.onSuccess(progressHandle.instanceId)
                is IoResult.Failure -> callback.onFailure(result.message ?: UNKNOWN_FAILURE_MESSAGE, progressHandle.instanceId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            callback.onFailure(e.message ?: UNKNOWN_FAILURE_MESSAGE, progressHandle.instanceId)
        } finally {
            progressHandle.clear()
        }
    }
}
