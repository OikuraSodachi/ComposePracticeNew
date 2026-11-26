package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.model.DirectoryListing
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

    /** [state]의 변경 가능한 원본. */
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

    /** 다중 선택 중이면 선택 목록을 items로 바꾼다(같은 경로는 처음 것 하나만 남긴다). 드래그 범위 선택과 모두 선택/해제에 사용한다. */
    fun replaceSelection(items: List<FileHolderItem>) = editSelection { items.distinctBy { it.path } }

    /**
     * 다중 선택 중이면 선택을 새로 조회한 목록 [listed]에 맞춘다(#524). 각 항목은 같은 경로의 [listed] 항목으로 바꾸고(크기·날짜도 새 값),
     * [listed]에 없는 항목은 뺀다 — 화면에 없는 항목이 선택 수와 하단 작업의 대상에 남지 않도록. 모두 빠져도 모두 해제와 같이 다중 선택은 유지한다.
     */
    fun retainListed(listed: List<FileHolderItem>) {
        val listedByPath = listed.associateBy { it.path }
        editSelection { selected -> selected.mapNotNull { listedByPath[it.path] } }
    }

    /** 다중 선택 중이면 기본 모드로 되돌린다. 작업 목적지 확인·전송 대기는 그대로 둔다. */
    fun endMultiSelect() {
        _state.update { current -> if (current is SelectionState.MultiSelect) SelectionState.Default else current }
    }

    /**
     * 다중 선택을 화면의 새 목록 조회 상태에 맞춘다(#524). 조회 결과가 오면 선택을 그 목록에 맞추고([retainListed]), 조회에 실패했거나 목록이 없으면 고를 항목이 없으므로 다중 선택을 끝낸다.
     * 다시 조회하는 중에는 결과를 기다리며 선택을 그대로 둔다. 작업 목적지 확인·전송 대기는 다른 폴더로 옮겨 다니는 상태이므로 맞추지 않는다.
     * @param listing 이 화면의 새 목록 조회 상태
     * @param isLocalFile 로컬 파일 목록이면 true — 선택 항목을 목록 화면과 같은 앱 레이어 항목으로 만든다
     */
    fun followListing(listing: DirectoryListing, isLocalFile: Boolean) {
        when (listing) {
            is DirectoryListing.Loaded -> retainListed(selectedIn(listing, isLocalFile))
            is DirectoryListing.Loading -> Unit
            DirectoryListing.NoPath, is DirectoryListing.Failed -> endMultiSelect()
        }
    }

    /**
     * [listing]에서 다중 선택과 경로가 같은 항목만 앱 레이어 항목으로 바꾼다 — 목록 전체는 화면이 따로 바꾸므로, 맞추는 데 필요한 항목만 바꾼다.
     * @param listing 새 조회 결과
     * @param isLocalFile 로컬 파일 목록이면 true
     * @return 선택한 경로의 새 항목. 다중 선택이 아니면 빈 목록
     */
    private fun selectedIn(listing: DirectoryListing.Loaded, isLocalFile: Boolean): List<FileHolderItem> {
        val current = state.value as? SelectionState.MultiSelect ?: return emptyList()
        val selectedPaths = current.selected.mapTo(HashSet()) { it.path }
        return FileHolderItem.fromList(listing.items.filter { it.path in selectedPaths }, isLocalFile)
    }

    /** 다중 선택 중이면 선택을 유지한 채 operation의 목적지 확인 단계로 전환한다. */
    fun enterOperation(operation: Op) {
        _state.update { current ->
            if (current is SelectionState.MultiSelect) SelectionState.Operation(operation, current.selected) else current
        }
    }

    /** 다른 화면에서 고른 대기 대상을 담은 transfer의 목적지 확인 단계로 전환한다. 이 화면의 선택은 버린다. */
    fun receive(transfer: T) { _state.value = SelectionState.Receiving(transfer) }

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

    /**
     * 이 화면의 선택이 정확히 한 항목일 때만 그 항목을 작업 대상으로 꺼내고, 같은 갱신에서 기본 모드로 되돌린다.
     * 한 항목만 받는 작업(이름 변경)은 [SubmittedItem]만 받으므로, 여러 항목을 넘기는 호출은 컴파일되지 않는다(#446).
     * @return 꺼낸 항목. 꺼낼 선택이 없거나 한 항목이 아니면 상태를 바꾸지 않고 null
     */
    fun submitSingle(): SubmittedItem? {
        var submitted: SubmittedItem? = null
        _state.update { current ->
            val item = submittableItems(current)?.singleOrNull()
            submitted = item?.let(::SubmittedSingle)
            if (item == null) current else SelectionState.Default
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

    /**
     * [matches]에 맞는 전송의 목적지 확인 중이면 기본 모드로 되돌린다. 이 화면의 선택·작업 상태와 다른 전송은 그대로 둔다.
     * @param matches 버릴 전송인지 판별하는 함수
     */
    fun discardReceiving(matches: (T) -> Boolean) {
        _state.update { current -> if (current is SelectionState.Receiving && matches(current.transfer)) SelectionState.Default else current }
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

    /**
     * [submitSingle]만 만드는 [SubmittedItem] 구현.
     * @param item 꺼낸 항목
     */
    private class SubmittedSingle(override val item: FileHolderItem) : SubmittedItem
}

/**
 * 화면의 선택에서 꺼낸 작업 대상 한 항목. [SelectionStateHolder.submitSingle]만 만들 수 있으며, 그 화면의 선택은 이 값을 꺼낼 때 이미 기본 모드로 되돌아가 있다.
 * 한 항목만 받는 작업 함수가 이 값을 받아, 선택 초기화를 빠뜨리거나 여러 항목을 넘기는 호출을 컴파일 단계에서 막는다.
 */
sealed interface SubmittedItem {
    /** 작업 대상 항목. */
    val item: FileHolderItem
}

/**
 * 화면의 선택에서 꺼낸 작업 대상. [SelectionStateHolder.submit]만 만들 수 있으며, 그 화면의 선택은 이 값을 꺼낼 때 이미 기본 모드로 되돌아가 있다.
 * 선택을 대상으로 하는 작업 함수가 목록 대신 이 값을 받아, 선택 초기화를 빠뜨린 호출을 컴파일 단계에서 막는다.
 */
sealed interface SubmittedSelection {
    /** 작업 대상 항목 목록. */
    val items: List<FileHolderItem>
}
