package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.ui.model.ConflictResolution
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.PendingConflict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "충돌 검사 → 충돌 없으면 즉시 실행 / 있으면 사용자 확인 대기 후 실행" 흐름을 관리한다.
 * ViewModel이 구성(composition)으로 보유해 대기 상태가 구성 변경 후에도 유지된다.
 */
class ConflictGate {

    private val _pending = MutableStateFlow<PendingConflict?>(null)
    /** 사용자 확인을 기다리는 충돌. null이면 대기 중인 충돌 없음. */
    val pending: StateFlow<PendingConflict?> = _pending.asStateFlow()

    /** 사용자 확인 후 실행할 작업. [pending]이 null이 아닐 때만 non-null이다. */
    private var pendingExecution: ((skip: List<FileHolderItem>) -> Unit)? = null

    /**
     * 충돌이 없으면 즉시 [execute]를 실행하고, 있으면 확인 대기 상태로 둔다.
     * 이미 확인을 기다리는 충돌이 있으면 새 요청은 실행하지도 대기시키지도 않고 거부한다 — 대기 중인 작업이 사용자 확인 없이 덮어써지지 않게 한다.
     * @param targets 작업 대상 전체 항목
     * @param conflicts 목적지와 이름이 겹치는 항목
     * @param execute 건너뛸 항목 목록을 받아 작업을 실행하는 함수
     * @return 즉시 실행했으면 true, 확인 대기로 전환했거나 거부했으면 false
     */
    fun request(
        targets: List<FileHolderItem>,
        conflicts: List<FileHolderItem>,
        execute: (skip: List<FileHolderItem>) -> Unit
    ): Boolean {
        if (_pending.value != null) return false
        if (conflicts.isEmpty()) {
            execute(emptyList())
            return true
        }
        pendingExecution = execute
        _pending.value = PendingConflict(targets, conflicts)
        return false
    }

    /**
     * 대기 중인 충돌을 [resolution]대로 처리하고 대기 상태를 해제한다.
     * @param resolution 사용자가 고른 처리 방법
     * @return 작업을 실행했으면 true, 취소했거나 대기 중인 충돌이 없으면 false
     */
    fun resolve(resolution: ConflictResolution): Boolean {
        val pending = _pending.value ?: return false
        val execute = pendingExecution
        _pending.value = null
        pendingExecution = null
        return when (resolution) {
            ConflictResolution.OVERWRITE -> { execute?.invoke(emptyList()); true }
            ConflictResolution.SKIP -> { execute?.invoke(pending.conflicts); true }
            ConflictResolution.CANCEL -> false
        }
    }
}
