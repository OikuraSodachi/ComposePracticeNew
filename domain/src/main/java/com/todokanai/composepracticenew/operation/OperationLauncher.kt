package com.todokanai.composepracticenew.operation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 파일 작업을 실행하며 완료·실패·진행 상태를 통합 관리하는 실행 엔진.
 * @param progressHandle 작업 인스턴스의 진행 상태 핸들
 * @param callback 완료·실패 시 알림·이벤트·갱신을 처리하는 콜백
 * @param work 실제 IO 작업 코드
 */
fun CoroutineScope.launchOperation(
    progressHandle: ProgressHandle,
    callback: OperationCallback,
    work: suspend () -> Unit
) {
    launch {
        try {
            work()
            callback.onSuccess(progressHandle.instanceId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            callback.onFailure(e.message ?: "알 수 없는 오류", progressHandle.instanceId)
        } finally {
            progressHandle.clear()
        }
    }
}
