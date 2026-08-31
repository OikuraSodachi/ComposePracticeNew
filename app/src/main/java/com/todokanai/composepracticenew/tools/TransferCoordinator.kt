package com.todokanai.composepracticenew.tools

import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** IO 전송 Flow의 hasError 추적, onCompletion 분기, catch를 표준화한다. */
@Singleton
class TransferCoordinator @Inject constructor() {

    /**
     * [source]를 [scope]에서 수집해 진행률·완료·에러를 표준 패턴으로 처리한다.
     * @param onProgress 정상 진행 중 상태를 받아 progress 저장소를 갱신한다
     * @param onRemove progress 항목을 제거한다 — 에러 발생 시와 완료 시 모두 호출된다
     * @param onSuccess 정상 완료 시 호출된다 — 알림 발송·목록 갱신 등 후처리를 담는다
     * @param onError 인밴드 에러(state.error) 또는 Flow 예외 발생 시 호출된다
     */
    fun launch(
        scope: CoroutineScope,
        sources: List<Flow<ProgressState>>,
        onProgress: suspend (ProgressState) -> Unit,
        onRemove: suspend () -> Unit,
        onSuccess: suspend () -> Unit = {},
        onError: suspend (message: String?) -> Unit = {}
    ) {
        scope.launch {
            var hasError = false          // 인밴드 에러(state.error) 발생 여부
            var lastError: String? = null // 마지막으로 기록된 에러 메시지
            try {
                for (source in sources) {
                    source.collect { state ->
                        if (state.error != null) {
                            hasError = true
                            lastError = state.error   // 인밴드 에러 기록 — 다중 에러 발생 시 마지막 1건만 onError로 방출됨 (다건 방출 방식은 보류)
                        } else {
                            runCatching { onProgress(state) } // onProgress 예외는 전송 성공 판정에 영향을 주지 않음
                        }
                    }
                }
                if (hasError) onError(lastError) else onSuccess() // 정상 완료 분기
            } catch (e: CancellationException) {
                throw e                   // 취소는 재전파 — 코루틴 취소 메커니즘 보존
            } catch (e: Exception) {
                onError(e.message)        // Flow 자체 예외
            } finally {
                onRemove()                // 완료·예외·취소 불문하고 progress 항목 제거
            }
        }
    }

}
