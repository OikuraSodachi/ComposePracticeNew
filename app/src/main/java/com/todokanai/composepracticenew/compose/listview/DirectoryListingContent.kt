package com.todokanai.composepracticenew.compose.listview

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.todokanai.composepracticenew.R
import com.todokanai.composepracticenew.model.ListingFailureReason
import com.todokanai.composepracticenew.ui.model.FileHolderItem
import com.todokanai.composepracticenew.ui.model.ListingUiState

/**
 * 목록 상태에 따라 조회 중 표시, 조회 실패 안내와 다시 시도 버튼, 빈 폴더 안내, 또는 [content]로 그린 항목 목록을 보여준다.
 * 조회 실패를 빈 폴더로 표시하지 않도록 "빈 폴더"는 조회를 마친 목록이 비었을 때만 보여준다.
 * @param modifier 목록 영역의 크기·배치를 정하는 Modifier
 * @param listing 표시할 목록 상태
 * @param onRetry 조회 실패 안내의 다시 시도 버튼이 호출하는 콜백
 * @param content 항목이 하나 이상일 때 목록을 그리는 컴포저블. [modifier]가 적용된 영역 안에 그려진다
 */
@Composable
fun DirectoryListingContent(
    modifier: Modifier,
    listing: ListingUiState,
    onRetry: () -> Unit,
    content: @Composable (List<FileHolderItem>) -> Unit
) {
    when (listing) {
        ListingUiState.NoPath -> Box(modifier = modifier)
        ListingUiState.Loading -> Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is ListingUiState.Failed -> ListingFailureNotice(modifier = modifier, failure = listing, onRetry = onRetry)
        is ListingUiState.Loaded ->
            if (listing.items.isEmpty()) {
                Text(
                    modifier = modifier
                        .fillMaxWidth()
                        .wrapContentSize(),
                    text = stringResource(R.string.empty_directory)
                )
            } else {
                // modifier(부모 Column의 weight 등)가 목록에도 적용되도록 Box로 감싼다
                Box(modifier = modifier) { content(listing.items) }
            }
    }
}

/**
 * 목록 조회 실패 안내. 실패 종류에 맞는 문구와 서버 응답 등 상세 내용, 다시 시도 버튼을 보여준다.
 * @param modifier 안내 영역의 크기·배치를 정하는 Modifier
 * @param failure 표시할 조회 실패 상태
 * @param onRetry 다시 시도 버튼이 호출하는 콜백
 */
@Composable
private fun ListingFailureNotice(modifier: Modifier, failure: ListingUiState.Failed, onRetry: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = stringResource(failureMessageRes(failure.reason)), textAlign = TextAlign.Center)
        failure.detail?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
        Button(onClick = onRetry) {
            Text(text = stringResource(R.string.btn_retry))
        }
    }
}

/**
 * 목록 조회 실패 종류에 맞는 안내 문구 리소스를 반환한다.
 * @param reason 조회 실패 종류
 * @return 안내 문구 문자열 리소스 ID
 */
@StringRes
private fun failureMessageRes(reason: ListingFailureReason): Int = when (reason) {
    ListingFailureReason.UNREADABLE -> R.string.listing_failed_unreadable
    ListingFailureReason.CONNECTION_LOST -> R.string.listing_failed_connection
}
