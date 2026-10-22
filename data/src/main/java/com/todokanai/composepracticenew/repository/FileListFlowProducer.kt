package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.ListingFailureReason
import com.todokanai.composepracticenew.model.RemoteConnectionLostException
import com.todokanai.composepracticenew.model.SortMode
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

/**
 * currentPath·sortBy·refreshTrigger 세 Flow를 결합해 현재 경로의 목록 조회 상태([DirectoryListing]) StateFlow를 생성하는 팩토리.
 * @param converter 파일 목록을 정렬·도메인 모델로 변환하는 DataConverter
 * @param sortPreference 정렬 기준 Flow를 제공하는 SortPreferenceRepository
 */
class FileListFlowProducer(
    private val converter: DataConverter,
    private val sortPreference: SortPreferenceRepository
) {
    /**
     * currentPath·sortBy·refreshTrigger를 결합해 목록 조회 상태 StateFlow를 반환한다.
     * 다른 경로로 이동하면 먼저 [DirectoryListing.Loading]을 방출해 이전 경로의 목록이 새 경로의 것으로 보이지 않게 한다.
     * @param currentPath 현재 탐색 중인 디렉터리 경로 StateFlow
     * @param refreshTrigger 새로고침 신호를 전달하는 Long 타임스탬프 StateFlow
     * @param appScope StateFlow를 공유할 애플리케이션 수명 CoroutineScope
     * @param started StateFlow 공유 시작 전략
     * @param listFiles 경로를 받아 해당 디렉터리의 FileEntry 목록을 반환하는 suspend 함수. 조회에 실패하면 IOException을 던진다
     * @return 현재 경로의 목록 조회 상태를 방출하는 StateFlow
     */
    @Suppress("OPT_IN_USAGE")
    fun produce(
        currentPath: StateFlow<String?>,
        refreshTrigger: StateFlow<Long>,
        appScope: CoroutineScope,
        started: SharingStarted,
        listFiles: suspend (String) -> List<FileEntry>
    ): StateFlow<DirectoryListing> {
        var latest: DirectoryListing = DirectoryListing.NoPath
        return combine(currentPath, sortPreference.sortBy, refreshTrigger) { path, sortMode, _ -> path to sortMode }
            .flatMapLatest { (path, sortMode) -> listingOf(path, sortMode, latest, listFiles) }
            .onEach { latest = it }
            .stateIn(scope = appScope, started = started, initialValue = DirectoryListing.NoPath)
    }

    /**
     * [path]의 목록을 조회하며 거치는 상태를 방출한다.
     * 같은 경로의 목록이 이미 있으면(새로고침·정렬 변경) 다시 조회하는 동안 그 목록을 유지해 화면이 깜빡이지 않게 한다.
     * @param path 조회할 디렉터리 경로. null이면 탐색 경로가 없는 상태다
     * @param sortMode 항목 정렬 기준
     * @param previous 직전에 방출한 목록 상태
     * @param listFiles 경로의 FileEntry 목록을 반환하는 suspend 함수
     * @return 조회 중·조회 결과 상태를 차례로 방출하는 Flow
     */
    private fun listingOf(
        path: String?,
        sortMode: SortMode,
        previous: DirectoryListing,
        listFiles: suspend (String) -> List<FileEntry>
    ): Flow<DirectoryListing> = flow {
        if (path == null) {
            emit(DirectoryListing.NoPath)
            return@flow
        }
        if (!isLoadedAt(previous, path)) emit(DirectoryListing.Loading(path))
        emit(load(path, sortMode, listFiles))
    }

    /**
     * [path]의 목록을 조회해 결과 상태로 바꾼다. 조회 실패는 빈 목록이 아니라 [DirectoryListing.Failed]가 된다.
     * IOException만 실패 상태로 바꾸며, 그 밖의 예외는 구현 오류이므로 숨기지 않고 전파한다.
     * @param path 조회할 디렉터리 경로
     * @param sortMode 항목 정렬 기준
     * @param listFiles 경로의 FileEntry 목록을 반환하는 suspend 함수
     * @return 조회 성공이면 [DirectoryListing.Loaded], 실패면 [DirectoryListing.Failed]
     */
    private suspend fun load(path: String, sortMode: SortMode, listFiles: suspend (String) -> List<FileEntry>): DirectoryListing =
        try {
            DirectoryListing.Loaded(path, converter.fileHolderItemList(listFiles(path), sortMode))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            DirectoryListing.Failed(path, failureReasonOf(e), e.message)
        }

    /**
     * [listing]이 [path]에 대해 조회를 마친 목록인지 판별한다.
     * @param listing 판별할 목록 상태
     * @param path 비교할 디렉터리 경로
     * @return [path]의 [DirectoryListing.Loaded]이면 true
     */
    private fun isLoadedAt(listing: DirectoryListing, path: String): Boolean =
        listing is DirectoryListing.Loaded && listing.path == path

    /**
     * 목록 조회 실패 예외의 종류를 판별한다.
     * @param e 목록 조회 중 발생한 예외
     * @return 연결이 없거나 끊긴 경우 [ListingFailureReason.CONNECTION_LOST], 그 밖에는 [ListingFailureReason.UNREADABLE]
     */
    private fun failureReasonOf(e: IOException): ListingFailureReason =
        if (e is RemoteConnectionLostException) ListingFailureReason.CONNECTION_LOST else ListingFailureReason.UNREADABLE
}
