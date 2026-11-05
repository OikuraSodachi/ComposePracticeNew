package com.todokanai.composepracticenew.repository

import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.model.DirectoryListing
import com.todokanai.composepracticenew.model.ListingRequest
import com.todokanai.composepracticenew.model.NavigateResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow

/**
 * 주입된 PathAwareFileSystem 집합이 다루는 경로를 탐색하는 FileNavigatorRepository 구현체.
 * 경로 종류별 처리는 파일 시스템에 위임하므로, 화면마다 다루는 스토리지는 넘기는 집합으로 정하고
 * 새 스토리지 타입은 이 클래스 수정 없이 DI 등록만으로 추가된다. 서버 연결은 다루지 않는다.
 * @param converter 파일 목록을 정렬·도메인 모델로 변환하는 DataConverter
 * @param sortPreference 정렬 기준 Flow를 제공하는 SortPreferenceRepository
 * @param appScope 파일 탐색 Flow를 공유할 애플리케이션 수명 CoroutineScope
 * @param started 목록 Flow를 공유하기 시작하고 멈추는 정책
 * @param fileSystems 이 저장소가 다루는 경로 종류별 PathAwareFileSystem 집합 — 각 handles()는 서로 배타적이다
 * @param initialPath 초기 탐색 경로; null이면 설정하지 않는다
 */
class FileExplorerRepositoryImpl(
    converter: DataConverter,
    sortPreference: SortPreferenceRepository,
    appScope: CoroutineScope,
    started: SharingStarted,
    fileSystems: Set<PathAwareFileSystem>,
    initialPath: String? = null
) : FileNavigatorRepository {

    /** 경로별 파일 시스템 선택과 목록 조회를 맡는 라우터. */
    private val router = FileSystemRouter(fileSystems)

    /** 현재 경로와 목록 조회 상태. 목록은 [router]가 경로를 처리하는 파일 시스템에서 읽는다. */
    private val navigation = DirectoryListingNavigation(
        converter, sortPreference, appScope, started, initialPath,
        listFiles = router::listFiles
    )

    override val currentPath: StateFlow<String?> = navigation.currentPath
    override val listingRequest: StateFlow<ListingRequest?> = navigation.listingRequest
    override val directoryListing: StateFlow<DirectoryListing> = navigation.directoryListing

    override fun refresh() = navigation.refresh()

    override fun getParentPath(path: String): String? = router.resolve(path)?.getParent(path)

    override suspend fun navigate(path: String): NavigateResult {
        val resolved = router.resolve(path)?.resolveNavigationPath(path) ?: return NavigateResult.Rejected
        navigation.navigateTo(resolved)
        return NavigateResult.Navigated
    }
}
