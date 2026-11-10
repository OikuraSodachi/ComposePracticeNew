package com.todokanai.composepracticenew.viewmodel

import com.todokanai.composepracticenew.ui.model.DirectoryItem
import com.todokanai.composepracticenew.usecase.FileNavigatorUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 목록 화면의 경로 breadcrumb 상태와 그 항목을 눌렀을 때의 이동을 맡는다. 로컬·원격 목록 ViewModel이 구성(composition)으로 보유한다.
 * @param fileNavigatorUseCase 표시할 저장소의 디렉터리 탐색 상태를 제공하는 UseCase
 * @param scope 경로 수집과 이동 요청이 속할 CoroutineScope
 */
class DirTreeHolder(
    private val fileNavigatorUseCase: FileNavigatorUseCase,
    private val scope: CoroutineScope
) {

    /** 경로 breadcrumb에 표시할 디렉터리 트리. */
    val dirTree: StateFlow<List<DirectoryItem>> = fileNavigatorUseCase.dirTree
        .map { list -> list.map { DirectoryItem(name = it.name, path = it.path) } }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** breadcrumb에서 누른 [entry]의 디렉터리로 이동한다. */
    fun onDirClick(entry: DirectoryItem) {
        scope.launch { fileNavigatorUseCase.setPath(entry.path) }
    }
}
