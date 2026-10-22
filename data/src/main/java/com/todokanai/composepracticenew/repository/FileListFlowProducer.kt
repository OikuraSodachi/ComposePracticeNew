package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.ListingFailureReason
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.composepracticenew.model.RemoteConnectionLostException
import com.todokanai.composepracticenew.model.SortMode
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/**
 * 목록 조회 요청 Flow로 목록 조회 상태([DirectoryListing]) StateFlow를 생성하는 팩토리.
 * 조회는 요청이 바뀔 때만 하고, 정렬은 조회한 항목 위에 적용하므로 정렬 기준만 바뀌면 다시 조회하지 않는다.
 * @param converter 파일 목록을 정렬·도메인 모델로 변환하는 DataConverter
 * @param sortPreference 정렬 기준 Flow를 제공하는 SortPreferenceRepository
 */
class FileListFlowProducer(
    private val converter: DataConverter,
    private val sortPreference: SortPreferenceRepository
) {
    /**
     * 요청마다 목록을 조회해 정렬 기준과 결합한 목록 조회 상태 StateFlow를 반환한다.
     * 새 요청을 조회하는 동안은 항상 [DirectoryListing.Loading]이므로, 이전 요청의 목록이 현재 요청의 확인된 목록으로 보이지 않는다.
     * @param requests 현재 목록 조회 요청 StateFlow. 경로가 없으면 null
     * @param appScope StateFlow를 공유할 애플리케이션 수명 CoroutineScope
     * @param started StateFlow 공유 시작 전략
     * @param onStopped 공유가 멈췄을 때 호출된다. 멈춘 동안 남아 있는 마지막 목록이 현재 요청의 목록으로 쓰이지 않도록 요청을 새로 바꾸는 데 쓴다
     * @param onUnexpectedFailure 입출력 실패가 아닌 예외로 조회하지 못했을 때 그 예외를 기록하는 함수
     * @param listFiles 경로를 받아 해당 디렉터리의 FileEntry 목록을 반환하는 suspend 함수. 조회에 실패하면 IOException을 던진다
     * @return 현재 요청의 목록 조회 상태를 방출하는 StateFlow
     */
    fun produce(
        requests: StateFlow<ListingRequest?>,
        appScope: CoroutineScope,
        started: SharingStarted,
        onStopped: () -> Unit,
        onUnexpectedFailure: (Throwable) -> Unit,
        listFiles: suspend (String) -> List<FileEntry>
    ): StateFlow<DirectoryListing> {
        val fetchStates = fetchStatesOf(requests, onUnexpectedFailure, listFiles).onCompletion { onStopped() }
        return sortedListingsOf(fetchStates)
            .stateIn(scope = appScope, started = started, initialValue = DirectoryListing.NoPath)
    }

    /**
     * 조회 상태를 정렬 기준과 결합해 목록 조회 상태로 바꾼다.
     * 같은 조회 결과를 같은 기준으로 이미 정렬했으면 그 결과를 재사용하므로, 다시 조회하는 동안 직전 목록을 다시 정렬하지 않고
     * 직전 목록이 이미 방출한 [DirectoryListing.Loaded]와 같은 인스턴스가 되어 하위 소비자도 변환을 건너뛸 수 있다.
     * 캐시는 하나의 수집 코루틴 안에서만 읽고 쓴다.
     * @param fetchStates 조회 상태 Flow
     * @return 목록 조회 상태 Flow
     */
    private fun sortedListingsOf(fetchStates: Flow<FetchState>): Flow<DirectoryListing> = flow {
        var lastSorted: SortedFetch? = null
        combine(fetchStates, sortPreference.sortBy) { state, sortMode -> state to sortMode }.collect { (state, sortMode) ->
            val sortedLoaded = { fetched: FetchState.Fetched ->
                lastSorted?.takeIf { it.fetched === fetched && it.sortMode == sortMode }?.loaded
                    ?: loadedOf(fetched, sortMode).also { lastSorted = SortedFetch(fetched, sortMode, it) }
            }
            emit(listingOf(state, sortedLoaded))
        }
    }

    /**
     * 요청마다 조회해 거치는 상태를 방출한다. 조회 중 상태에는 같은 경로의 직전 목록을 표시용으로 붙인다.
     * 직전 상태는 방출 순서대로 하나의 수집 코루틴에서만 누적하므로 스레드 사이에 공유되지 않는다.
     * 공유가 다시 시작되면 멈추기 전 마지막 상태부터 이어 누적해, 같은 경로를 다시 조회하는 동안에도 직전 목록을 표시할 수 있게 한다.
     * @param requests 현재 목록 조회 요청 StateFlow
     * @param onUnexpectedFailure 입출력 실패가 아닌 예외를 기록하는 함수
     * @param listFiles 경로의 FileEntry 목록을 반환하는 suspend 함수
     * @return 조회 상태 Flow
     */
    @Suppress("OPT_IN_USAGE")
    private fun fetchStatesOf(
        requests: StateFlow<ListingRequest?>,
        onUnexpectedFailure: (Throwable) -> Unit,
        listFiles: suspend (String) -> List<FileEntry>
    ): Flow<FetchState> {
        // 공유가 멈췄다 다시 시작될 때 이어 누적할 마지막 상태 — 이전 공유가 완전히 끝난 뒤에만 읽힌다
        val lastState = AtomicReference<FetchState>(FetchState.NoPath)
        return flow {
            val states = requests
                .flatMapLatest { request -> fetch(request, onUnexpectedFailure, listFiles) }
                .scan(lastState.get(), ::withStale)
                .drop(1)
                .onEach { lastState.set(it) }
            emitAll(states)
        }
    }

    /**
     * [request]의 목록을 조회하며 조회 중·조회 결과 상태를 차례로 방출한다.
     * @param request 조회할 요청. null이면 탐색 경로가 없는 상태다
     * @param onUnexpectedFailure 입출력 실패가 아닌 예외를 기록하는 함수
     * @param listFiles 경로의 FileEntry 목록을 반환하는 suspend 함수
     * @return 조회 상태 Flow
     */
    private fun fetch(
        request: ListingRequest?,
        onUnexpectedFailure: (Throwable) -> Unit,
        listFiles: suspend (String) -> List<FileEntry>
    ): Flow<FetchState> = flow {
        if (request == null) {
            emit(FetchState.NoPath)
            return@flow
        }
        emit(FetchState.Fetching(request))
        emit(load(request, onUnexpectedFailure, listFiles))
    }

    /**
     * [request]의 목록을 조회해 결과 상태로 바꾼다. 조회 실패는 빈 목록이 아니라 실패 상태가 된다.
     * 입출력 실패가 아닌 예외도 실패 상태로 바꿔 기록한다 — 전파하면 공유 Flow의 상위 수집이 끝나 이후 이동·새로고침이 반영되지 않는다.
     * @param request 조회할 요청
     * @param onUnexpectedFailure 입출력 실패가 아닌 예외를 기록하는 함수
     * @param listFiles 경로의 FileEntry 목록을 반환하는 suspend 함수
     * @return 조회 성공이면 [FetchState.Fetched], 실패면 [FetchState.FetchFailed]
     */
    private suspend fun load(
        request: ListingRequest,
        onUnexpectedFailure: (Throwable) -> Unit,
        listFiles: suspend (String) -> List<FileEntry>
    ): FetchState =
        try {
            FetchState.Fetched(request, listFiles(request.path))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            FetchState.FetchFailed(request, failureReasonOf(e), e.message)
        } catch (e: Exception) {
            // 내부 예외 메시지는 사용자에게 의미가 없으므로 화면 상세에 싣지 않고 기록만 한다
            onUnexpectedFailure(e)
            FetchState.FetchFailed(request, ListingFailureReason.UNEXPECTED, detail = null)
        }

    /**
     * 새 조회 중 상태에 [previous]가 가진 같은 경로의 직전 목록을 붙인다. 조회 중이 아닌 상태는 그대로 둔다.
     * @param previous 직전에 방출한 조회 상태
     * @param next 새로 방출할 조회 상태
     * @return 같은 경로의 직전 목록을 붙인 [next]
     */
    private fun withStale(previous: FetchState, next: FetchState): FetchState {
        if (next !is FetchState.Fetching) return next
        val stale = when (previous) {
            is FetchState.Fetched -> previous
            is FetchState.Fetching -> previous.stale
            else -> null
        }
        return next.copy(stale = stale?.takeIf { it.request.path == next.request.path })
    }

    /**
     * 조회 상태를 정렬된 목록 조회 상태로 바꾼다.
     * @param state 조회 상태
     * @param loadedOf 조회 완료 상태를 정렬된 조회 완료 상태로 바꾸는 함수
     * @return 목록 조회 상태
     */
    private fun listingOf(state: FetchState, loadedOf: (FetchState.Fetched) -> DirectoryListing.Loaded): DirectoryListing =
        when (state) {
            FetchState.NoPath -> DirectoryListing.NoPath
            is FetchState.Fetching -> DirectoryListing.Loading(state.request, state.stale?.let(loadedOf))
            is FetchState.Fetched -> loadedOf(state)
            is FetchState.FetchFailed -> DirectoryListing.Failed(state.request, state.reason, state.detail)
        }

    /**
     * 조회한 항목을 [sortMode]로 정렬해 조회 완료 상태로 바꾼다.
     * @param fetched 조회 완료 상태
     * @param sortMode 항목 정렬 기준
     * @return 정렬된 조회 완료 상태
     */
    private fun loadedOf(fetched: FetchState.Fetched, sortMode: SortMode): DirectoryListing.Loaded =
        DirectoryListing.Loaded(fetched.request, converter.fileHolderItemList(fetched.entries, sortMode))

    /**
     * 목록 조회 실패 예외의 종류를 판별한다.
     * @param e 목록 조회 중 발생한 예외
     * @return 연결이 없거나 끊긴 경우 [ListingFailureReason.CONNECTION_LOST], 그 밖에는 [ListingFailureReason.UNREADABLE]
     */
    private fun failureReasonOf(e: IOException): ListingFailureReason =
        if (e is RemoteConnectionLostException) ListingFailureReason.CONNECTION_LOST else ListingFailureReason.UNREADABLE

    /**
     * 마지막으로 정렬한 조회 결과. 같은 조회 결과·기준이면 정렬을 다시 하지 않고 [loaded]를 재사용한다.
     * @param fetched 정렬한 조회 완료 상태
     * @param sortMode 정렬 기준
     * @param loaded 정렬 결과
     */
    private class SortedFetch(val fetched: FetchState.Fetched, val sortMode: SortMode, val loaded: DirectoryListing.Loaded)

    /** 정렬을 적용하기 전의 조회 상태. 조회한 항목을 그대로 보관해 정렬 기준이 바뀌어도 다시 조회하지 않게 한다. */
    private sealed interface FetchState {

        /** 탐색할 경로가 없는 상태. */
        data object NoPath : FetchState

        /**
         * [request]를 조회하는 중인 상태.
         * @param request 조회 중인 요청
         * @param stale 같은 경로의 직전 조회 결과. 표시용이며 없으면 null
         */
        data class Fetching(val request: ListingRequest, val stale: Fetched? = null) : FetchState

        /**
         * [request]의 조회를 마친 상태.
         * @param request 조회를 마친 요청
         * @param entries 정렬하지 않은 디렉터리 항목
         */
        data class Fetched(val request: ListingRequest, val entries: List<FileEntry>) : FetchState

        /**
         * [request]의 조회에 실패한 상태.
         * @param request 조회에 실패한 요청
         * @param reason 실패 종류
         * @param detail 실패의 상세 내용. 없으면 null
         */
        data class FetchFailed(val request: ListingRequest, val reason: ListingFailureReason, val detail: String?) : FetchState
    }
}
