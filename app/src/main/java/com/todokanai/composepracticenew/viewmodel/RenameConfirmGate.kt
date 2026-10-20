package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.model.RenameConflict
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.PendingRename
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 이름 변경의 "충돌 판정 → 겹치지 않으면 즉시 실행 / 파일·폴더가 겹치면 덮어쓰기 확인 후 실행" 흐름을 관리한다.
 * ViewModel이 구성(composition)으로 보유해 확인 대기 상태가 구성 변경 후에도 유지된다.
 */
class RenameConfirmGate {

    private val _pending = MutableStateFlow<PendingRename?>(null)
    /** 덮어쓰기 확인을 기다리는 이름 변경. null이면 대기 중인 확인 없음. */
    val pending: StateFlow<PendingRename?> = _pending.asStateFlow()

    /** 확인 후 실행할 작업. [pending]이 null이 아닐 때만 non-null이다. */
    private var pendingExecution: ((overwrite: Boolean) -> Unit)? = null

    /**
     * [conflict]가 파일·폴더 겹침이면 확인 대기로 두고, 아니면 덮어쓰지 않는 실행을 즉시 한다.
     * 종류가 다른 겹침([RenameConflict.TYPE_MISMATCH])도 덮어쓰지 않는 실행으로 넘겨, 실행 계층이 거부하고 오류를 알리게 한다.
     * 이미 확인을 기다리는 요청이 있으면 새 요청은 무시한다 — 대기 중인 요청이 사용자 확인 없이 바뀌지 않게 한다.
     * @param target 이름을 바꿀 항목
     * @param newName 새 이름
     * @param conflict 같은 폴더의 기존 항목과 겹치는 방식
     * @param execute 덮어쓰기 여부를 받아 이름 변경을 실행하는 함수
     * @return Unit
     */
    fun request(target: FileHolderItem, newName: String, conflict: RenameConflict, execute: (overwrite: Boolean) -> Unit) {
        if (_pending.value != null) return
        when (conflict) {
            RenameConflict.FILE, RenameConflict.FOLDER -> {
                pendingExecution = execute
                _pending.value = PendingRename(target, newName, conflict)
            }
            RenameConflict.NONE, RenameConflict.TYPE_MISMATCH -> execute(false)
        }
    }

    /**
     * 대기 중인 이름 변경을 덮어쓰기로 실행하고 대기 상태를 해제한다.
     * @return Unit
     */
    fun confirm() {
        val execute = pendingExecution
        clear()
        execute?.invoke(true)
    }

    /**
     * 대기 중인 이름 변경을 실행하지 않고 대기 상태를 해제한다.
     * @return Unit
     */
    fun cancel() = clear()

    /** 대기 상태와 실행 작업을 비운다. */
    private fun clear() {
        _pending.value = null
        pendingExecution = null
    }
}
