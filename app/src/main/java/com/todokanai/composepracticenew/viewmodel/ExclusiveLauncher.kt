package com.todokanai.composepracticenew.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 화면 안의 비동기 처리를 한 번에 하나만 실행하고, 처리 중에 다시 들어온 입력은 버리는 실행기(연타 규칙, #551).
 * 먼저 들어온 입력이 이기며, 시간으로 거르지(debounce) 않는다. 화면을 떠나지 않아 back stack entry의 RESUMED 확인으로 막을 수 없는 입력에 쓴다.
 * @param scope 작업을 실행할 코루틴 스코프(보통 viewModelScope)
 */
class ExclusiveLauncher(private val scope: CoroutineScope) {

    /** [isRunning]의 원본. */
    private val _isRunning = MutableStateFlow(false)

    /** 실행 중인 작업이 있는지. 작업이 끝나면(실패·취소 포함) false가 된다. */
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /**
     * 실행 중인 작업이 없으면 [task]를 실행한다. 있으면 아무것도 하지 않는다.
     * 실행 중 표시는 launch 전에 세운다 — 작업이 기다리는(suspend) 동안 들어온 입력과 같은 프레임의 연속 입력도 걸러진다.
     * 표시는 작업 안의 finally가 아니라 완료 콜백에서 내린다 — 스코프가 이미 취소돼 본문이 실행되지 않아도 내려가도록.
     * @param task 실행할 작업
     */
    fun launch(task: suspend CoroutineScope.() -> Unit) {
        if (!_isRunning.compareAndSet(expect = false, update = true)) return
        scope.launch(block = task).invokeOnCompletion { _isRunning.value = false }
    }
}
