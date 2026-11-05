package com.todokanai.composepracticenew.repository

import android.util.Log
import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * 현재 경로와 목록 조회 요청을 보유하고, 경로 이동·새로고침마다 요청을 새로 만들어 그 요청으로 directoryListing을 구성하는 탐색 상태.
 * 같은 경로로 다시 이동해도(재연결 등) 새 요청이 되므로 목록을 다시 조회한다.
 * 탐색 저장소 구현체가 이 클래스를 보유해 상태를 넘기고, 경로 검증·부모 경로 규칙만 직접 구현한다.
 * 조회 방법은 생성자 인자로 받으므로 생성할 때 항상 준비돼 있다. 공유는 생성 중에 시작될 수 있으므로(started에 따라),
 * 보유자는 생성 중인 자신에 묶인 함수(`::listFiles` 같은 자기 멤버 참조)가 아니라 이미 생성된 의존 객체의 함수를 넘긴다 —
 * 그래야 생성 중에 바로 조회해도 보유자의 초기화되지 않은 필드를 읽지 않는다.
 * @param converter 파일 목록을 정렬·도메인 모델로 변환하는 DataConverter
 * @param sortPreference 정렬 기준 Flow를 제공하는 SortPreferenceRepository
 * @param appScope 파일 탐색 Flow를 공유할 애플리케이션 수명 CoroutineScope
 * @param started StateFlow 공유 시작 전략
 * @param initialPath 초기 탐색 경로; null이면 경로 없이 시작한다
 * @param listFiles 경로의 파일과 하위 디렉터리 목록 조회 결과를 반환하는 suspend 함수. 비어 있으면 저장소가 드러내는 항목이 없는 디렉터리다([DirectoryListing.Loaded]의 알려진 한계 참고). 경로가 없거나 디렉터리가 아니거나 읽을 수 없으면 빈 목록이 아니라 실패 결과를 돌려줘야 한다
 */
internal class DirectoryListingNavigation(
    converter: DataConverter,
    sortPreference: SortPreferenceRepository,
    appScope: CoroutineScope,
    started: SharingStarted,
    initialPath: String?,
    listFiles: suspend (String) -> IoResult<List<FileEntry>>
) {

    private companion object {
        /** 예상하지 못한 목록 조회 오류를 기록할 로그 태그. */
        const val TAG = "FileExplorerRepo"
    }

    /** 현재 경로의 변경 가능한 원본. [navigateTo]로만 바꾼다. */
    private val _currentPath = MutableStateFlow(initialPath)
    /** 현재 탐색 중인 디렉터리 경로. 경로가 없으면 null이다. */
    val currentPath: StateFlow<String?> = _currentPath.asStateFlow()

    /** 요청마다 증가시켜 같은 경로의 요청도 서로 구분되게 하는 세대 번호. */
    private val generation = AtomicLong(0L)
    /** 현재 목록 조회 요청의 변경 가능한 원본. 이동·새로고침·공유 중지 때만 바꾼다. */
    private val _listingRequest = MutableStateFlow(initialPath?.let { newRequest(it) })
    /** 현재 목록 조회 요청. 경로가 없으면 null이다. */
    val listingRequest: StateFlow<ListingRequest?> = _listingRequest.asStateFlow()

    /** [listingRequest]의 목록 조회 상태. */
    val directoryListing: StateFlow<DirectoryListing> =
        FileListFlowProducer(converter, sortPreference).produce(
            requests = listingRequest,
            appScope = appScope,
            started = started,
            onStopped = ::refresh,
            onUnexpectedFailure = { Log.e(TAG, "목록 조회 중 예상하지 못한 오류", it) },
            listFiles = listFiles
        )

    /**
     * 검증 없이 경로를 [path]로 바꾸고, 같은 경로라도 새 목록 조회 요청을 만든다. null이면 경로와 요청을 비운다.
     * 보유한 저장소가 검증을 마친 이동 진입점에서만 부른다.
     * @param path 이동할 디렉터리 경로. null이면 경로 없음
     */
    fun navigateTo(path: String?) {
        _currentPath.value = path
        _listingRequest.value = path?.let { newRequest(it) }
    }

    /** 현재 경로의 목록을 새 요청으로 다시 조회한다. 경로가 없으면 아무것도 하지 않는다. */
    fun refresh() {
        _listingRequest.update { current -> current?.let { newRequest(it.path) } }
    }

    /**
     * [path]의 새 목록 조회 요청을 만든다.
     * @param path 조회할 디렉터리 경로
     * @return 이전 요청과 세대가 다른 요청
     */
    private fun newRequest(path: String): ListingRequest = ListingRequest(path, generation.incrementAndGet())
}
