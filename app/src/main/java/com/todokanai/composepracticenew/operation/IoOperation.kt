package com.todokanai.composepracticenew.operation

import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * IO 작업의 생명주기(init → 작업 → 완료)를 표준화하는 Template Method 기반 추상 클래스.
 * @param setProgress 진행 상태를 외부 저장소에 반영하는 람다
 * @param clearProgress 작업 종료 시 진행 상태를 제거하는 람다
 */
abstract class IoOperation(
    protected val setProgress: (ProgressState) -> Unit,
    private val clearProgress: () -> Unit
) {

    /** 현재 작업의 진행 상태 — mainOperation()이 갱신하고, execute()가 읽는다. */
    @Volatile
    protected var progressState: ProgressState = ProgressState(progress = 0)

    /**
     * 외부 진입점 — 생명주기 순서를 강제한다.
     * @param scope ViewModel 파괴 시에도 전송이 유지되어야 하므로 appScope을 전달해야 한다.
     */
    fun execute(scope: CoroutineScope) {
        scope.launch {
            try {
                setProgress(progressState)
                mainOperation()
                onCompletion()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.message ?: "알 수 없는 오류")
            } finally {
                clearProgress()
            }
        }
    }

    /** 주 작업 코드 — 진행 중 progressState를 갱신한다. */
    abstract suspend fun mainOperation()

    /** 작업 완료 시 콜백 — 성공·인밴드 에러 구분 없이 항상 호출된다. */
    abstract suspend fun onCompletion()

    protected open suspend fun onError(message: String) {}
}
