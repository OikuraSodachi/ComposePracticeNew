package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.model.ConflictChoice
import com.todokanai.composepracticenew.model.WriteConflict

/**
 * 쓰기 중에 같은 이름의 항목을 만난 파일 작업 구현이 처리 방법을 묻는 통로.
 * 구현은 덮어쓰지 않는 쓰기를 먼저 시도하고, 파일 시스템이 이미 있다고 알릴 때만 묻는다 — 확인과 쓰기 사이에 생긴 항목도 묻지 않고 덮어쓰지 않도록.
 * 답을 기다리는 동안 작업이 멈춘다. 사용자가 작업을 취소했거나 답이 오지 않으면 CancellationException을 던지며, 구현은 그것을 잡지 않고 전파한다.
 */
fun interface WriteConflictResolver {
    /**
     * [conflict]를 어떻게 처리할지 정한다.
     * @param conflict 겹친 항목
     * @return 처리 방법. [com.todokanai.composepracticenew.model.WriteConflictKind.canProceed]가 아닌 겹침에는 SKIP만 돌려준다
     */
    suspend fun resolve(conflict: WriteConflict): ConflictChoice
}
