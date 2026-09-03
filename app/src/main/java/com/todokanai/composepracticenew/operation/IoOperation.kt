package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** IO 작업의 생명주기(init → 작업 → 완료)를 표준화하는 Template Method 기반 추상 클래스. */
abstract class IoOperation {

    /** 현재 작업의 진행 상태 — mainOperation()이 갱신하고, progressTracker()가 읽는다. */
    protected var progressState: ProgressState = ProgressState(progress = 0)

    /**
     * 외부 진입점 — 생명주기 순서를 강제한다.
     * @param scope ViewModel 파괴 시에도 전송이 유지되어야 하므로 appScope을 전달해야 한다.
     */
    fun execute(scope: CoroutineScope) {
        scope.launch {
            try {
                progressTracker()
                mainOperation()
                onCompletion()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.message ?: "알 수 없는 오류")
            } finally {
                removeProgress()
            }
        }
    }

    /** 주 작업 코드 — 진행 중 progressState를 갱신한다. */
    abstract suspend fun mainOperation()

    /** 정상 완료 시 콜백 — 알림 발송, 파일 목록 갱신 등 후처리를 담는다. */
    abstract suspend fun onCompletion()

    /** progressState를 ProgressRepository에 반영한다 — execute()가 초기화(0%) 시 1회 호출한다. */
    abstract suspend fun progressTracker()

    protected open suspend fun removeProgress() {}
    protected open suspend fun onError(message: String) {}
}
