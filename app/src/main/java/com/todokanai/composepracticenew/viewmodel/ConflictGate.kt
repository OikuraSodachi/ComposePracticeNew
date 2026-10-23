package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.model.DestinationConflicts
import com.todokanai.composepracticenew.model.ListingCompleteness
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
     * 충돌이 없다고 확인되면 즉시 [execute]를 실행하고, 그렇지 않으면 확인 대기 상태로 둔다.
     * 목적지 목록이 확인되지 않았으면 충돌이 없어 보여도 같은 이름의 항목이 덮어써질 수 있으므로 확인을 기다린다.
     * 이미 확인을 기다리는 충돌이 있으면 새 요청은 실행하지도 대기시키지도 않고 거부한다 — 대기 중인 작업이 사용자 확인 없이 덮어써지지 않게 한다.
     * @param targets 작업 대상 전체 항목
     * @param checked 목적지 목록으로 판정한 이름 충돌 결과
     * @param execute 건너뛸 항목 목록을 받아 작업을 실행하는 함수
     * @return 즉시 실행했으면 true, 확인 대기로 전환했거나 거부했으면 false
     */
    fun request(
        targets: List<FileHolderItem>,
        checked: DestinationConflicts<FileHolderItem>,
        execute: (skip: List<FileHolderItem>) -> Unit
    ): Boolean {
        if (_pending.value != null) return false
        val destinationUnconfirmed = isUnconfirmed(checked.destinationCompleteness)
        if (checked.conflicts.isEmpty() && !destinationUnconfirmed) {
            execute(emptyList())
            return true
        }
        pendingExecution = execute
        _pending.value = PendingConflict(targets, checked.conflicts, destinationUnconfirmed)
        return false
    }

    /**
     * 목적지 목록이 확인되지 않아 사용자 확인이 필요한지 판별한다.
     * @param completeness 목적지 목록의 확인 여부
     * @return 확인되지 않은 목록이면 true
     */
    private fun isUnconfirmed(completeness: ListingCompleteness): Boolean = when (completeness) {
        ListingCompleteness.COMPLETE -> false
        ListingCompleteness.UNCONFIRMED -> true
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
