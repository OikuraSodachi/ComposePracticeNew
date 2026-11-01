package com.todokanai.composepracticenew.compose.frag

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.holder.SearchResultHolder
import com.todokanai.composepracticenew.compose.listview.rememberTypeIcons
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.LocalSelectMode
import com.todokanai.composepracticenew.ui.model.SearchResult
import com.todokanai.composepracticenew.ui.model.SearchStatus
import com.todokanai.composepracticenew.ui.model.SearchUiState
import com.todokanai.composepracticenew.viewmodel.FileSearchViewModel

/**
 * 모든 저장소에서 이름으로 항목을 찾는 검색 화면. 검색창, 진행·읽지 못한 항목 안내, 결과 목록을 조합한다.
 * 로컬 목록 화면 안에서 목록 대신 그려지며 내비게이션 destination이 아니다 — 뒤로가기는 검색만 닫고 백스택·현재 경로는 건드리지 않는다.
 * 검색어와 결과는 이 화면에서 읽는다 — 결과 묶음·글자 입력마다의 리컴포지션이 로컬 목록 화면(FileListFrag)까지 번지지 않도록.
 * @param modifier 검색 화면의 크기·배치를 정하는 Modifier
 * @param selectMode 지금 로컬 화면의 선택 모드. 결과를 눌렀을 때 여는지를 목록 행과 같은 규칙으로 정하는 데 쓴다
 * @param viewModel 검색어·결과를 갖고 검색어 변경·닫기·결과 열기를 받는 ViewModel
 */
@Composable
fun FileSearchFrag(
    modifier: Modifier,
    selectMode: LocalSelectMode,
    viewModel: FileSearchViewModel
) {
    /** 검색 결과 표시 상태. 검색이 닫혔으면(null) 그릴 것이 없다. */
    val state = viewModel.state.collectAsStateWithLifecycle().value ?: return

    // 이 화면이 그려지는 동안(검색이 열린 동안)만 등록 — 목록 destination의 상위 폴더 이동보다 나중에 등록돼 먼저 받는다
    BackHandler(onBack = viewModel::close)

    Column(modifier = modifier.fillMaxSize()) {
        SearchField(query = viewModel.query, onQueryChange = viewModel::onQueryChange, onClose = viewModel::close)
        if (state.status == SearchStatus.Searching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (state.status == SearchStatus.LimitReached) {
            Text(
                text = stringResource(R.string.search_limit_reached, state.results.size),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        if (state.unreadableCount > 0) {
            Text(
                text = stringResource(R.string.search_unreadable, state.unreadableCount),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            SearchResultContent(
                state = state,
                onResultClick = { viewModel.onResultClick(it, selectMode) },
                onOpenLocation = viewModel::onOpenLocation
            )
        }
    }
}

/**
 * 뒤로 버튼, 검색어 입력칸, 지우기 버튼으로 된 검색창. 처음 그려질 때 입력칸에 포커스를 준다.
 * @param query 표시할 검색어
 * @param onQueryChange 검색어를 바꾸는 콜백
 * @param onClose 검색을 닫는 콜백
 */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    // 키 없음 — 입력칸과 같은 수명으로 1회 생성해 포커스 요청 대상을 유지한다
    val focusRequester = remember { FocusRequester() }
    // Unit 키 — 검색을 열 때(이 입력칸이 처음 그려질 때) 한 번만 포커스를 준다. 화면 회전으로 다시 그려지면 다시 준다
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    /** 검색 키를 누르면 입력칸의 포커스를 거둬 키보드를 내리는 데 쓰는 포커스 관리자. */
    val focusManager = LocalFocusManager.current

    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        placeholder = { Text(stringResource(R.string.search_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // 검색은 입력이 멈추면 이미 시작되므로 검색 키는 결과를 볼 수 있게 키보드만 내린다
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        leadingIcon = {
            IconButton(onClick = onClose) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = stringResource(R.string.search_close))
            }
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(imageVector = Icons.Default.Clear, contentDescription = stringResource(R.string.search_clear))
                }
            }
        }
    )
}

/**
 * 검색 상태에 따라 결과 목록, 결과 없음 안내, 저장소 없음 안내 중 하나를 보여준다.
 * "결과 없음"은 다 훑었는데 찾은 항목이 없을 때만 보여, 훑는 중이거나 아무것도 훑지 않은 것을 결과 없음으로 보이지 않게 한다.
 * @param state 검색 결과 표시 상태
 * @param onResultClick 결과 항목을 누른 콜백
 * @param onOpenLocation 결과 항목의 위치 열기 콜백
 */
@Composable
private fun SearchResultContent(
    state: SearchUiState,
    onResultClick: (FileHolderItem) -> Unit,
    onOpenLocation: (FileHolderItem) -> Unit
) {
    when {
        state.results.isNotEmpty() -> SearchResultList(results = state.results, onResultClick = onResultClick, onOpenLocation = onOpenLocation)
        state.status == SearchStatus.Finished -> CenteredNotice(text = stringResource(R.string.search_no_results))
        state.status == SearchStatus.NoStorage -> CenteredNotice(text = stringResource(R.string.search_no_storage))
    }
}

/**
 * 검색 결과 목록.
 * @param results 표시할 결과. 찾은 순서다
 * @param onResultClick 결과 항목을 누른 콜백
 * @param onOpenLocation 결과 항목의 위치 열기 콜백
 */
@Composable
private fun SearchResultList(
    results: List<SearchResult>,
    onResultClick: (FileHolderItem) -> Unit,
    onOpenLocation: (FileHolderItem) -> Unit
) {
    // items{} 밖에서 한 번 만들어 모든 결과 행이 같은 인스턴스를 공유 — 행이 Strong Skipping의 인스턴스 비교로 생략된다
    val typeIcons = rememberTypeIcons()
    // 키를 주지 않는다(위치 키) — 결과는 뒤에 붙기만 해 위치가 바뀌지 않고, 저장소 루트가 겹치면 같은 경로가 두 번 나올 수 있어 경로 키는 중복될 수 있다
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(results) { result ->
            SearchResultHolder(
                modifier = Modifier,
                result = result,
                typeIcons = typeIcons,
                onClick = { onResultClick(result.item) },
                onOpenLocation = { onOpenLocation(result.item) }
            )
        }
    }
}

/**
 * 결과 영역 가운데에 안내 문구를 보여준다.
 * @param text 안내 문구
 */
@Composable
private fun CenteredNotice(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center)
    )
}
