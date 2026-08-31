package com.todokanai.composepracticenew.tools

import com.todokanai.composepracticenew.model.ProgressState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
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
        source: Flow<ProgressState>,
        onProgress: suspend (ProgressState) -> Unit,
        onRemove: suspend () -> Unit,
        onSuccess: suspend () -> Unit = {},
        onError: suspend (message: String?) -> Unit = {}
    ) {
        scope.launch {
            var hasError = false
            var lastError: String? = null
            source
                .onCompletion { cause ->
                    onRemove()
                    when {
                        cause != null -> onError(cause.message)
                        hasError -> onError(lastError)
                        else -> onSuccess()
                    }
                }
                .catch { }
                .collect { state ->
                    runCatching {
                        if (state.error != null) {
                            hasError = true
                            lastError = state.error
                        } else {
                            onProgress(state)
                        }
                    }.onFailure { e ->
                        hasError = true
                        lastError = e.message
                    }
                }
        }
    }

}
