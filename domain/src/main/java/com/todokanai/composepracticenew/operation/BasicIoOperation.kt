package com.todokanai.composepracticenew.operation

import kotlinx.coroutines.CancellationException

/**
 * @param targets 작업 대상 파일 목록을 특정짓는 string 목록
 * @param onError 작업 도중 오류 발생 시 행동. [ABORT_ON_ERROR]: 전체 작업 중단  [SKIP_ON_ERROR]: 해당 작업 건너뛰기
 */
abstract class BasicIoOperation(
    val targets: List<String>,
    val onError: Int
) {
    companion object {
        const val ABORT_ON_ERROR = 0
        const val SKIP_ON_ERROR = 1
    }

    /**
     * targets 항목을 순서대로 처리한다. [ABORT_ON_ERROR]이면 첫 오류 시 전체 중단, [SKIP_ON_ERROR]이면 해당 항목을 건너뛰고 계속한다.
     */
    suspend fun run() {
        for (target in targets) {
            if (onError == SKIP_ON_ERROR) processItemSafely(target)
            else processItem(target)
        }
    }

    /** [target] 하나에 대해 IO 작업을 수행한다. */
    abstract suspend fun processItem(target: String)

    /** [target] 처리 중 발생한 예외를 건너뛴다. CancellationException은 재전파한다. */
    private suspend fun processItemSafely(target: String) {
        try {
            processItem(target)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
    }
}