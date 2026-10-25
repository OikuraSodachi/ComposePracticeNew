package com.todokanai.composepracticenew.repository

import android.util.Log
import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.fileexplorer.FileEntry
import com.todokanai.fileexplorer.StorageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * 경로 이동·새로고침마다 목록 조회 요청을 새로 만들고, 그 요청으로 directoryListing을 구성하는 공통 로직을 보유한 기반 클래스.
 * 같은 경로로 다시 이동해도(재연결 등) 새 요청이 되므로 목록을 다시 조회한다.
 * 하위 클래스의 [listFiles]는 조회 실패를 빈 목록이 아니라 실패 결과로 돌려줘야 한다.
 * @param converter 파일 목록을 정렬·도메인 모델로 변환하는 DataConverter
 * @param sortPreference 정렬 기준 Flow를 제공하는 SortPreferenceRepository
 * @param appScope 파일 탐색 Flow를 공유할 애플리케이션 수명 CoroutineScope
 * @param started StateFlow 공유 시작 전략
 * @param initialPath 초기 탐색 경로; null이면 루트로 시작하지 않는다
 */
abstract class BaseFileExplorerRepository(
    converter: DataConverter,
    sortPreference: SortPreferenceRepository,
    appScope: CoroutineScope,
    started: SharingStarted,
    initialPath: String? = null
) : StorageRepository(), FileNavigatorRepository {

    private companion object {
        /** 예상하지 못한 목록 조회 오류를 기록할 로그 태그. */
        const val TAG = "FileExplorerRepo"
    }

    /** 요청마다 증가시켜 같은 경로의 요청도 서로 구분되게 하는 세대 번호. */
    private val generation = AtomicLong(0L)
    /** 현재 목록 조회 요청의 변경 가능한 원본. 이동·새로고침·공유 중지 때만 바꾼다. */
    private val _listingRequest = MutableStateFlow<ListingRequest?>(null)
    override val listingRequest: StateFlow<ListingRequest?> = _listingRequest.asStateFlow()

    override val directoryListing: StateFlow<DirectoryListing> =
        FileListFlowProducer(converter, sortPreference).produce(
            requests = listingRequest,
            appScope = appScope,
            started = started,
            onStopped = ::refresh,
            onUnexpectedFailure = { Log.e(TAG, "목록 조회 중 예상하지 못한 오류", it) }
        ) { listFiles(it) }

    init { initialPath?.let { navigateTo(it) } }

    /**
     * [path]의 파일과 하위 디렉터리 목록을 조회한다.
     * @param path 조회할 디렉터리 경로
     * @return 항목 목록. 비어 있으면 저장소가 드러내는 항목이 없는 디렉터리다(저장소가 숨긴 항목은 있을 수 있다). 경로가 없거나 디렉터리가 아니거나 읽을 수 없으면 실패 결과다
     */
    protected abstract suspend fun listFiles(path: String): IoResult<List<FileEntry>>

    /** 경로를 바꾸고, 같은 경로라도 새 목록 조회 요청을 만든다. */
    override fun navigateTo(path: String?) {
        super.navigateTo(path)
        _listingRequest.value = path?.let { newRequest(it) }
    }

    override fun refresh() {
        _listingRequest.update { current -> current?.let { newRequest(it.path) } }
    }

    /**
     * [path]의 새 목록 조회 요청을 만든다.
     * @param path 조회할 디렉터리 경로
     * @return 이전 요청과 세대가 다른 요청
     */
    private fun newRequest(path: String): ListingRequest = ListingRequest(path, generation.incrementAndGet())
}
