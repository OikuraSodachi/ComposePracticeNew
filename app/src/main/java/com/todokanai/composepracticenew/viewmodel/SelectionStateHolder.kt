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

    /**
     * 다중 선택 중이면 item을 선택 목록에 더한다. 같은 경로의 항목이 이미 있으면 그대로 둔다 —
     * 선택 목록은 경로마다 항목 하나만 가져, 작업이 같은 항목을 두 번 처리하지 않게 한다.
     */
    fun add(item: FileHolderItem) = editSelection { selected -> if (selected.any { it.path == item.path }) selected else selected + item }

    /** 다중 선택 중이면 item과 같은 경로의 항목을 선택 목록에서 뺀다. 화면의 선택 표시와 같이 경로로 판단한다. */
    fun remove(item: FileHolderItem) = editSelection { selected -> selected.filterNot { it.path == item.path } }

    /** 다중 선택 중이면 선택 목록을 items로 바꾼다(같은 경로는 처음 것 하나만 남긴다). 드래그 범위 선택 시 반복 add 대신 사용한다. */
    fun replaceSelection(items: List<FileHolderItem>) = editSelection { items.distinctBy { it.path } }

    /** 다중 선택 중이면 선택을 유지한 채 operation의 목적지 확인 단계로 전환한다. */
    fun enterOperation(operation: Op) {
        _state.update { current ->
            if (current is SelectionState.MultiSelect) SelectionState.Operation(operation, current.selected) else current
        }
    }

    /** 다른 화면에서 고른 pending을 받을 transfer의 목적지 확인 단계로 전환한다. 이 화면의 선택은 버린다. */
    fun receive(transfer: T, pending: List<FileHolderItem>) { _state.value = SelectionState.Receiving(transfer, pending) }

    /**
     * 이 화면의 선택을 작업 대상으로 꺼내고, 같은 갱신에서 기본 모드로 되돌린다.
     * 작업 함수는 [SubmittedSelection]만 받으므로, 선택을 비우지 않고 작업을 부르는 코드는 컴파일되지 않는다.
     * @return 다중 선택·같은 저장소 작업 상태에 항목이 있었으면 그 선택, 꺼낼 선택이 없으면(그 밖의 상태, 모두 해제한 다중 선택) 상태를 바꾸지 않고 null
     */
    fun submit(): SubmittedSelection? {
        var submitted: SubmittedSelection? = null
        _state.update { current ->
            val items = submittableItems(current)
            submitted = items?.let(::Submitted)
            if (items == null) current else SelectionState.Default
        }
        return submitted
    }

    /** state에서 작업 대상으로 꺼낼 선택 목록. 이 화면의 선택이 아니거나(기본·전송 목적지) 비었으면 null. */
    private fun submittableItems(state: SelectionState<Op, T>): List<FileHolderItem>? = when (state) {
        is SelectionState.MultiSelect, is SelectionState.Operation -> state.selected.takeIf { it.isNotEmpty() }
        SelectionState.Default, is SelectionState.Receiving -> null
    }

    /**
     * 전송 목적지 확인 중이면 그 대기를 꺼내고, 같은 갱신에서 기본 모드로 되돌린다 — 꺼낸 대기는 꺼낸 쪽만 가지므로 다음 전송이 그 자리를 써도 사라지지 않는다.
     * @return 꺼낸 대기. 전송 목적지 확인 중이 아니면 상태를 바꾸지 않고 null
     */
    fun takeReceiving(): SelectionState.Receiving<T>? {
        var taken: SelectionState.Receiving<T>? = null
        _state.update { current ->
            taken = current as? SelectionState.Receiving
            if (taken == null) current else SelectionState.Default
        }
        return taken
    }

    /** 이 화면이 기본 모드(선택도 대기도 없음)일 때만 [receiving]을 다시 전송 목적지 확인으로 둔다. 그사이 다른 선택·대기가 자리를 차지했으면 그대로 둔다. */
    fun restoreReceiving(receiving: SelectionState.Receiving<T>) {
        _state.update { current -> if (current == SelectionState.Default) receiving else current }
    }

    /** 기본 모드로 되돌린다. 선택 목록과 전송 대기 목록도 함께 사라진다. */
    fun reset() { _state.value = SelectionState.Default }

    /** 전송 목적지 확인 중이면 기본 모드로 되돌린다. 이 화면의 선택·작업 상태는 그대로 둔다. */
    fun discardReceiving() {
        _state.update { current -> if (current is SelectionState.Receiving) SelectionState.Default else current }
    }

    /** 다중 선택 중일 때만 선택 목록에 edit를 적용한다. */
    private fun editSelection(edit: (List<FileHolderItem>) -> List<FileHolderItem>) {
        _state.update { current ->
            if (current is SelectionState.MultiSelect) SelectionState.MultiSelect(edit(current.selected)) else current
        }
    }

    /**
     * [submit]만 만드는 [SubmittedSelection] 구현.
     * @param items 꺼낸 선택 목록
     */
    private class Submitted(override val items: List<FileHolderItem>) : SubmittedSelection
}

/**
 * 화면의 선택에서 꺼낸 작업 대상. [SelectionStateHolder.submit]만 만들 수 있으며, 그 화면의 선택은 이 값을 꺼낼 때 이미 기본 모드로 되돌아가 있다.
 * 선택을 대상으로 하는 작업 함수가 목록 대신 이 값을 받아, 선택 초기화를 빠뜨린 호출을 컴파일 단계에서 막는다.
 */
sealed interface SubmittedSelection {
    /** 작업 대상 항목 목록. */
    val items: List<FileHolderItem>
}
