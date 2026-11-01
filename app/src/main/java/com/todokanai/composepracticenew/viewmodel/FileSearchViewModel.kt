package com.todokanai.composepracticenew.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todokanai.composepracticenew.model.SearchBatch
import com.todokanai.composepracticenew.model.SearchQuery
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.LocalSelectMode
import com.todokanai.composepracticenew.ui.model.SearchResult
import com.todokanai.composepracticenew.ui.model.SearchStatus
import com.todokanai.composepracticenew.ui.model.SearchUiState
import com.todokanai.composepracticenew.ui.model.SelectMode
import com.todokanai.composepracticenew.usecase.FileSearchUseCase
import com.todokanai.composepracticenew.usecase.OpenFileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 로컬 목록 화면의 파일 검색 상태(열림 여부, 검색어, 결과)와 모든 저장소 검색을 맡는다.
 * 검색은 내비게이션 destination이 아니라 이 화면 안의 상태라 백스택에 쌓이지 않는다. 목록 destination의 백스택 항목에 스코프되므로 화면 회전에도 검색과 결과가 이어진다.
 * 검색어 입력이 [QUERY_DEBOUNCE_MILLIS] 동안 멈추면 진행 중인 검색을 취소하고 새로 시작한다. 파일을 열고 돌아와도 결과가 남도록 화면 수명과 무관하게 끝까지 훑는다.
 * @param fileSearchUseCase 모든 저장소에서 이름으로 항목을 찾고 항목의 위치로 이동하는 UseCase
 * @param openFileUseCase 항목 열기(디렉터리 진입 포함)를 수행하는 UseCase
 */
@HiltViewModel
class FileSearchViewModel @Inject constructor(
    private val fileSearchUseCase: FileSearchUseCase,
    private val openFileUseCase: OpenFileUseCase
) : ViewModel() {

    private companion object {
        /** 검색어 입력이 이만큼 멈추면 검색을 시작한다 — 글자마다 저장소 전체를 다시 훑지 않도록 */
        const val QUERY_DEBOUNCE_MILLIS = 300L
    }

    /** [state]의 변경 가능한 원본. */
    private val _state = MutableStateFlow<SearchUiState?>(null)
    /** 검색 결과 표시 상태. null이면 검색이 닫혀 있다. */
    val state: StateFlow<SearchUiState?> = _state.asStateFlow()

    /**
     * 검색창에 입력한 그대로의 검색어. TextField에 비동기 Flow를 물리면 입력이 늦게 반영돼 커서가 튀므로 Compose 상태로 둔다.
     */
    var query by mutableStateOf("")
        private set

    /** 지금 검색어의 검색. 검색어가 바뀌거나 검색을 닫으면 취소한다. */
    private var searchJob: Job? = null

    /** 검색을 연다. 이미 열려 있으면 그대로 둔다. */
    fun open() {
        if (_state.value == null) _state.value = SearchUiState()
    }

    /** 검색을 닫고 진행 중인 검색과 검색어·결과를 버린다. */
    fun close() {
        cancelSearch()
        query = ""
        _state.value = null
    }

    /**
     * 검색어를 바꾸고, 입력이 멈추면 새 검색어로 다시 찾는다. 이전 검색어의 결과는 바로 지운다 — 다른 검색어의 결과로 보이지 않도록.
     * @param text 입력한 검색어
     */
    fun onQueryChange(text: String) {
        if (_state.value == null) return
        query = text
        cancelSearch()
        val searchQuery = SearchQuery.of(text)
        if (searchQuery == null) {
            _state.value = SearchUiState()
            return
        }
        _state.value = SearchUiState(status = SearchStatus.Searching)
        searchJob = viewModelScope.launch {
            delay(QUERY_DEBOUNCE_MILLIS)
            search(searchQuery)
        }
    }

    /**
     * 검색 결과 [item]을 누른 동작. 목록 행 클릭과 같은 규칙을 따른다 — 폴더는 그리로 이동하고 검색을 닫으며,
     * 파일은 기본 모드에서만 열고 검색은 그대로 둔다(돌아와 다른 결과를 볼 수 있도록). 다중 선택 중에는 무시한다.
     * @param item 누른 결과 항목
     * @param selectMode 지금 로컬 화면의 선택 모드
     */
    fun onResultClick(item: FileHolderItem, selectMode: LocalSelectMode) {
        when (selectMode) {
            SelectMode.Default -> if (item.isDirectory) navigateAndClose { openFileUseCase.open(item.toDomain()) } else openFile(item)
            SelectMode.MultiSelect -> { }
            is SelectMode.LocalOperation, SelectMode.Download -> if (item.isDirectory) navigateAndClose { openFileUseCase.open(item.toDomain()) }
        }
    }

    /**
     * 검색 결과 [item]이 들어 있는 폴더로 이동하고 검색을 닫는다.
     * @param item 위치를 열 결과 항목
     */
    fun onOpenLocation(item: FileHolderItem) {
        navigateAndClose { fileSearchUseCase.openLocation(item.toDomain()) }
    }

    /**
     * 모든 저장소에서 [searchQuery]를 찾아 결과를 찾는 대로 [state]에 더하고, 다 훑으면 끝났음을 표시한다.
     * 검색할 저장소가 없으면 결과 없음이 아니라 저장소 없음으로 표시한다.
     */
    private suspend fun search(searchQuery: SearchQuery) {
        val batches = fileSearchUseCase.search(searchQuery)
        if (batches == null) {
            _state.update { it?.copy(status = SearchStatus.NoStorage) }
            return
        }
        uiBatches(batches).collect { (found, unreadableCount) ->
            _state.update { it?.copy(results = it.results + found, unreadableCount = it.unreadableCount + unreadableCount) }
        }
        _state.update { it?.copy(status = SearchStatus.Finished) }
    }

    /**
     * domain 결과 묶음을 화면용 결과와 읽지 못한 항목 수로 바꾼다. 항목마다 썸네일 URI를 만드는 비용이 있어 메인 스레드 밖에서 바꾼다.
     * @param batches domain 결과 묶음 Flow
     * @return 화면용 결과와 읽지 못한 항목 수의 쌍 Flow
     */
    private fun uiBatches(batches: Flow<SearchBatch>): Flow<Pair<List<SearchResult>, Int>> = batches
        .map { batch ->
            val results = FileHolderItem.fromList(batch.found, isLocalFile = true).zip(batch.found) { item, domain ->
                SearchResult(item, fileSearchUseCase.locationOf(domain))
            }
            results to batch.unreadable.size
        }
        .flowOn(Dispatchers.IO)

    /** [item] 파일을 연다. */
    private fun openFile(item: FileHolderItem) {
        viewModelScope.launch { openFileUseCase.open(item.toDomain()) }
    }

    /** 검색을 닫고 [navigate]로 다른 폴더로 이동한다. */
    private fun navigateAndClose(navigate: suspend () -> Unit) {
        close()
        viewModelScope.launch { navigate() }
    }

    /** 진행 중인 검색을 취소한다. */
    private fun cancelSearch() {
        searchJob?.cancel()
        searchJob = null
    }
}
