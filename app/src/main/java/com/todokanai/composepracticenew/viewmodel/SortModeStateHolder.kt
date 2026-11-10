package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.model.SortMode
import com.todokanai.composepracticenew.usecase.SortModeUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * 목록 화면의 정렬 기준을 노출하고 변경한다. 로컬·원격 목록 ViewModel이 구성(composition)으로 보유한다.
 * 모든 멤버를 화면이 직접 쓰므로 ViewModel은 이를 감추고 다시 노출하지 않고 public 프로퍼티로 둔다.
 * @param sortModeUseCase 정렬 기준을 조회·저장하는 UseCase
 * @param scope 정렬 기준 수집이 속할 CoroutineScope
 */
class SortModeStateHolder(
    private val sortModeUseCase: SortModeUseCase,
    scope: CoroutineScope
) {

    /** 현재 정렬 기준. DataStore에서 수집한다. */
    val sortMode: StateFlow<SortMode> = sortModeUseCase.sortBy
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SortMode.BY_DEFAULT
        )

    /** 정렬 다이얼로그에서 선택된 정렬 기준을 저장한다. */
    fun onSortModeSelected(mode: SortMode) {
        sortModeUseCase.saveSortBy(mode)
    }
}
