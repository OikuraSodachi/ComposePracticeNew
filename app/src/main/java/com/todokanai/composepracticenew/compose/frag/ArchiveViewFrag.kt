package com.todokanai.composepracticenew.compose.frag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.compose.dialog.ArchivePasswordDialog
import com.todokanai.composepracticenew.compose.holder.ArchiveEntryHolder
import com.todokanai.composepracticenew.compose.listview.rememberTypeIcons
import com.todokanai.composepracticenew.ui.model.ArchiveEntryRow
import com.todokanai.composepracticenew.ui.model.asString
import com.todokanai.composepracticenew.viewmodel.ArchiveViewContent
import com.todokanai.composepracticenew.viewmodel.ArchiveViewViewModel

/** 경로 표시줄에서 폴더 이름 사이에 넣는 구분 표시. 표시 전용이며 경로를 만들지 않는다. */
private const val FOLDER_SEPARATOR = " › "

/**
 * 압축 파일 내용 보기 화면. 위에 압축 파일 이름과 지금 폴더를, 아래에 지금 폴더의 항목을 보인다. 읽기 전용이다.
 * 항목 목록까지 암호화한 압축 파일이면 비밀번호를 묻는다.
 * @param modifier 화면의 배치를 정하는 Modifier
 * @param viewModel 목록 읽기·폴더 탐색·비밀번호 질문을 맡는 ViewModel
 */
@Composable
fun ArchiveViewFrag(modifier: Modifier, viewModel: ArchiveViewViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val passwordRequest by viewModel.passwordRequest.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = uiState.archiveName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp)
        )
        Text(
            text = uiState.folderNames.joinToString(separator = FOLDER_SEPARATOR, prefix = FOLDER_SEPARATOR),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        Divider()
        ArchiveContent(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            content = uiState.content,
            onRetry = viewModel::retry,
            onOpen = viewModel::openEntry
        )
    }

    passwordRequest?.let { request ->
        // 질문 id를 키로 — 다시 물을 때 앞서 입력한 비밀번호가 새 질문에 남지 않도록 다이얼로그 상태를 새로 만든다
        key(request.id) {
            ArchivePasswordDialog(
                archiveName = request.archiveName,
                prompt = request.prompt,
                onConfirm = { password -> viewModel.answerPassword(request.id, password) },
                onCancel = { viewModel.cancelPassword(request.id) }
            )
        }
    }
}

/**
 * 목록 영역. 읽는 중 표시, 읽기 실패 안내와 다시 시도 버튼, 빈 폴더 안내, 또는 항목 목록을 보인다.
 * @param modifier 목록 영역의 크기·배치를 정하는 Modifier
 * @param content 목록 영역의 상태
 * @param onRetry 다시 시도 버튼이 부르는 콜백
 * @param onOpen 행을 눌렀을 때 부르는 콜백
 */
@Composable
private fun ArchiveContent(
    modifier: Modifier,
    content: ArchiveViewContent,
    onRetry: () -> Unit,
    onOpen: (ArchiveEntryRow) -> Unit
) {
    when (content) {
        ArchiveViewContent.Loading -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is ArchiveViewContent.Failed -> Column(
            modifier = modifier
                .wrapContentSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = stringResource(R.string.archive_view_failed), textAlign = TextAlign.Center)
            content.detail?.let {
                Text(text = it.asString(), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            Button(onClick = onRetry) {
                Text(text = stringResource(R.string.btn_retry))
            }
        }
        is ArchiveViewContent.Loaded ->
            if (content.rows.isEmpty()) {
                Text(text = stringResource(R.string.archive_view_empty), modifier = modifier.wrapContentSize())
            } else {
                ArchiveEntryList(modifier = modifier, rows = content.rows, onOpen = onOpen)
            }
    }
}

/**
 * 지금 폴더의 항목 목록.
 * @param modifier 목록의 크기·배치를 정하는 Modifier
 * @param rows 표시할 행들(정렬됨)
 * @param onOpen 행을 눌렀을 때 부르는 콜백
 */
@Composable
private fun ArchiveEntryList(modifier: Modifier, rows: List<ArchiveEntryRow>, onOpen: (ArchiveEntryRow) -> Unit) {
    // items{} 밖에서 한 번 만들어 모든 행이 같은 인스턴스를 공유 — 행이 Strong Skipping의 인스턴스 비교로 생략된다
    val typeIcons = rememberTypeIcons()
    // 키를 주지 않는다(위치 키) — 압축 파일에는 같은 이름의 항목이 여럿 있을 수 있어 이름 키는 중복될 수 있다
    LazyColumn(modifier = modifier) {
        items(rows) { row ->
            ArchiveEntryHolder(
                modifier = Modifier,
                row = row,
                typeIcons = typeIcons,
                onClick = { onOpen(row) }
            )
        }
    }
}
