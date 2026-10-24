package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.SameStorageOperation
import com.todokanai.composepracticenew.ui.model.SelectionState
import com.todokanai.composepracticenew.ui.model.TransferMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 파일 목록 화면 하나의 [SelectionState]를 보관하고, 아래 전환 함수로만 바꾼다.
 * 선택 편집과 작업 진입은 다중 선택 상태에서만 일어나며, 다른 상태에서 호출되면 상태를 바꾸지 않는다.
 */
class SelectionStateHolder<Op : SameStorageOperation, T : TransferMode> {

    private val _state = MutableStateFlow<SelectionState<Op, T>>(SelectionState.Default)
    /** 현재 선택 상태. */
    val state: StateFlow<SelectionState<Op, T>> = _state.asStateFlow()

    /** first 하나를 선택한 채로 다중 선택을 시작한다. */
    fun startMultiSelect(first: FileHolderItem) { _state.value = SelectionState.MultiSelect(listOf(first)) }

    /** 다중 선택 중이면 item을 선택 목록에 더한다. 호출부가 토글을 보장해야 한다 — 이미 있는 항목을 다시 더하면 중복된다. */
    fun add(item: FileHolderItem) = editSelection { it + item }

    /** 다중 선택 중이면 item을 선택 목록에서 뺀다. */
    fun remove(item: FileHolderItem) = editSelection { it - item }

    /** 다중 선택 중이면 선택 목록을 items로 바꾼다. 드래그 범위 선택 시 반복 add 대신 사용한다. */
    fun replaceSelection(items: List<FileHolderItem>) = editSelection { items }

    /** 다중 선택 중이면 선택을 유지한 채 operation의 목적지 확인 단계로 전환한다. */
    fun enterOperation(operation: Op) {
        _state.update { current ->
            if (current is SelectionState.MultiSelect) SelectionState.Operation(operation, current.selected) else current
        }
    }

    /** 다른 화면에서 고른 pending을 받을 transfer의 목적지 확인 단계로 전환한다. 이 화면의 선택은 버린다. */
    fun receive(transfer: T, pending: List<FileHolderItem>) { _state.value = SelectionState.Receiving(transfer, pending) }

    /** 기본 모드로 되돌린다. 선택 목록과 전송 대기 목록도 함께 사라진다. */
    fun reset() { _state.value = SelectionState.Default }

    /** 다중 선택 중일 때만 선택 목록에 edit를 적용한다. */
    private fun editSelection(edit: (List<FileHolderItem>) -> List<FileHolderItem>) {
        _state.update { current ->
            if (current is SelectionState.MultiSelect) SelectionState.MultiSelect(edit(current.selected)) else current
        }
    }
}
