package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.ui.model.ConflictResolution
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.NameCollisionGroup
import com.todokanai.composepracticenew.ui.model.PendingConflict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "충돌 검사 → 충돌 없으면 즉시 실행 / 있으면 사용자 확인 대기 후 실행" 흐름을 관리한다.
 * 확인을 기다리는 요청은 줄을 세워 먼저 들어온 것부터 하나씩 묻는다 — 따로 시작한 작업의 확인이 겹쳐도 어느 요청도 버리지 않는다.
 * ViewModel이 구성(composition)으로 보유해 대기 상태가 구성 변경 후에도 유지된다.
 */
class ConflictGate {

    private val _pending = MutableStateFlow<PendingConflict?>(null)
    /** 지금 사용자 확인을 기다리는 충돌(줄의 맨 앞). null이면 대기 중인 충돌 없음. */
    val pending: StateFlow<PendingConflict?> = _pending.asStateFlow()

    /** 확인을 기다리는 요청들(들어온 순서). 맨 앞이 [pending]으로 보인다. */
    private val queue = ArrayDeque<Waiting>()

    /**
     * 충돌도 겹칠 수 있는 이름도 없으면 즉시 [execute]를 실행하고, 있으면 확인 대기 줄에 넣는다.
     * @param targets 작업 대상 전체 항목
     * @param conflicts 목적지와 이름이 겹치는 항목
     * @param collisions 함께 보내는 항목끼리 목적지에서 같은 항목이 될 수 있는 이름 묶음
     * @param onCancel 사용자가 확인을 취소했을 때 부르는 함수
     * @param execute 건너뛸 항목 목록을 받아 작업을 실행하는 함수
     * @return 즉시 실행했으면 true, 확인 대기 줄에 넣었으면 false
     */
    fun request(
        targets: List<FileHolderItem>,
        conflicts: List<FileHolderItem>,
        collisions: List<NameCollisionGroup>,
        onCancel: () -> Unit,
        execute: (skip: List<FileHolderItem>) -> Unit
    ): Boolean {
        if (conflicts.isEmpty() && collisions.isEmpty()) {
            execute(emptyList())
            return true
        }
        queue.addLast(Waiting(PendingConflict(targets, conflicts, collisions), onCancel, execute))
        showFront()
        return false
    }

    /**
     * 지금 확인을 기다리는 충돌을 [resolution]대로 처리하고, 줄의 다음 요청을 보인다.
     * @param resolution 사용자가 고른 처리 방법
     * @return 작업을 실행했으면 true, 취소했거나 대기 중인 충돌이 없으면 false
     */
    fun resolve(resolution: ConflictResolution): Boolean {
        val front = queue.removeFirstOrNull() ?: return false
        showFront()
        return when (resolution) {
            ConflictResolution.OVERWRITE -> { front.execute(emptyList()); true }
            ConflictResolution.SKIP -> { front.execute(front.conflict.conflicts); true }
            ConflictResolution.CANCEL -> { front.onCancel(); false }
        }
    }

    /** 확인을 기다리는 요청을 모두 실행하지도 취소 처리하지도 않고 버린다. 작업이 더는 성공할 수 없을 때(원격 세션 끊김) 쓴다. */
    fun discardAll() {
        queue.clear()
        showFront()
    }

    /** 줄의 맨 앞 요청을 [pending]으로 보인다. */
    private fun showFront() {
        _pending.value = queue.firstOrNull()?.conflict
    }

    /**
     * 확인을 기다리는 요청 하나.
     * @param conflict 사용자에게 보일 충돌
     * @param onCancel 취소했을 때 부르는 함수
     * @param execute 건너뛸 항목 목록을 받아 작업을 실행하는 함수
     */
    private class Waiting(
        val conflict: PendingConflict,
        val onCancel: () -> Unit,
        val execute: (skip: List<FileHolderItem>) -> Unit
    )
}
