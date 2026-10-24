package com.todokanai.composepracticenew.ui.model

/**
 * 파일 목록 화면 하나의 선택 상태. 모드와 그 모드에서 의미 있는 항목 목록을 한 값으로 묶는다.
 * 선택 목록은 다중 선택·같은 저장소 작업 상태에만, 전송 대기 목록은 전송 목적지 상태에만 있으므로
 * "기본 모드인데 선택이 남은" 상태나 "전송 목적지인데 이 화면의 선택이 남은" 상태를 만들 수 없다.
 * 화면별로 가능한 작업은 타입 인자로 좁힌다.
 *
 * @param Op 이 화면에서 선택을 유지한 채 목적지를 고르는 같은 저장소 작업. 없으면 Nothing
 * @param T 이 화면이 목적지가 되는 전송 모드. 없으면 Nothing
 */
sealed interface SelectionState<out Op : SameStorageOperation, out T : TransferMode> {

    /** 행 강조와 하단 버튼이 다루는 이 화면의 선택 목록. 선택이 의미 없는 상태에서는 비어 있다. */
    val selected: List<FileHolderItem>

    /** 일반 탐색 상태. 선택도 전송 대기 목록도 없다. */
    data object Default : SelectionState<Nothing, Nothing> {
        override val selected: List<FileHolderItem> get() = emptyList()
    }

    /**
     * 다중 선택 상태.
     * @param selected 선택한 항목 목록
     */
    data class MultiSelect(override val selected: List<FileHolderItem>) : SelectionState<Nothing, Nothing>

    /**
     * 선택을 유지한 채 같은 저장소 작업의 목적지를 고르는 상태.
     * @param operation 확인 시 실행할 작업
     * @param selected 작업 대상 항목 목록
     */
    data class Operation<out O : SameStorageOperation>(
        val operation: O,
        override val selected: List<FileHolderItem>
    ) : SelectionState<O, Nothing>

    /**
     * 다른 화면에서 고른 항목을 받을 목적지를 고르는 상태. 작업 대상은 이 화면의 선택이 아닌 [pending]이다.
     * @param transfer 확인 시 실행할 전송
     * @param pending 전송 대기 항목 목록(다른 저장소의 항목)
     */
    data class Receiving<out R : TransferMode>(
        val transfer: R,
        val pending: List<FileHolderItem>
    ) : SelectionState<Nothing, R> {
        override val selected: List<FileHolderItem> get() = emptyList()
    }
}

/** 로컬 화면의 선택 상태를 화면이 분기에 쓰는 모드로 바꾼다. */
fun SelectionState<SelectMode.LocalOperation, SelectMode.Download>.localMode(): LocalSelectMode = when (this) {
    SelectionState.Default -> SelectMode.Default
    is SelectionState.MultiSelect -> SelectMode.MultiSelect
    is SelectionState.Operation -> operation
    is SelectionState.Receiving -> transfer
}

/** 원격 화면의 선택 상태를 화면이 분기에 쓰는 모드로 바꾼다. */
fun SelectionState<Nothing, SelectMode.Upload>.remoteMode(): RemoteSelectMode = when (this) {
    SelectionState.Default -> SelectMode.Default
    is SelectionState.MultiSelect -> SelectMode.MultiSelect
    is SelectionState.Operation -> operation
    is SelectionState.Receiving -> transfer
}
