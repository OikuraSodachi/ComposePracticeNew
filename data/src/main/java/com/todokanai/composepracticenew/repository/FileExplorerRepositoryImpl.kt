package com.todokanai.composepracticenew.repository

import android.util.Log
import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.model.IoResult
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.composepracticenew.model.onSuccess
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted

/**
 * 로컬 및 원격 경로를 모두 탐색할 수 있는 FileNavigatorRepository 구현체.
 * 경로 종류별 처리는 주입된 PathAwareFileSystem 집합에 위임하므로, 새 스토리지 타입은 이 클래스 수정 없이 DI 등록만으로 추가된다.
 * @param converter 파일 목록을 정렬·도메인 모델로 변환하는 DataConverter
 * @param sortPreference 정렬 기준 Flow를 제공하는 SortPreferenceRepository
 * @param appScope 파일 탐색 Flow를 공유할 애플리케이션 수명 CoroutineScope
 * @param fileSystems 경로 종류별 처리를 담당하는 PathAwareFileSystem 집합 — 각 handles()는 서로 배타적이다
 * @param connection 원격 서버 연결과 루트 경로 계산을 담당하는 RemoteConnectionRepository
 * @param initialPath 초기 탐색 경로; null이면 설정하지 않는다
 */
class FileExplorerRepositoryImpl(
    converter: DataConverter,
    sortPreference: SortPreferenceRepository,
    appScope: CoroutineScope,
    private val fileSystems: Set<PathAwareFileSystem>,
    private val connection: RemoteConnectionRepository,
    initialPath: String? = null
) : BaseFileExplorerRepository(converter, sortPreference, appScope, SharingStarted.Lazily, initialPath), RemoteFileNavigatorRepository {

    companion object {
        private const val TAG = "FileExplorerRepo"
        /** 경로를 처리할 파일 시스템이 없어 목록을 조회하지 못했을 때의 실패 메시지. */
        private const val UNSUPPORTED_PATH_MESSAGE = "지원하지 않는 경로입니다"
    }

    /**
     * [path]를 처리하는 파일 시스템을 반환한다. 처리자가 없는 경로는 크래시 대신 경고 로그를 남기고 null을 반환해
     * 호출부가 "목록 조회 실패·부모 없음·이동 무시"로 다루게 한다.
     */
    private fun resolveFileSystem(path: String): PathAwareFileSystem? =
        fileSystems.firstOrNull { it.handles(path) }
            ?: null.also { Log.w(TAG, "No file system handles path: $path") }

    override fun getParent(path: String): String? = resolveFileSystem(path)?.getParent(path)

    override suspend fun listFiles(path: String): IoResult<List<FileEntry>> {
        Log.d(TAG, "fileList 갱신 — path=$path")
        val fileSystem = resolveFileSystem(path) ?: return IoResult.ItemFailed(UNSUPPORTED_PATH_MESSAGE)
        return fileSystem.listFiles(path).onSuccess {
            Log.d(TAG, "fileList 결과 — path=$path size=${it.size}")
        }
    }

    override fun getParentPath(path: String): String? = getParent(path)

    override suspend fun navigate(path: String) {
        resolveFileSystem(path)?.resolveNavigationPath(path)?.let { navigateTo(it) }
    }

    override suspend fun connectRemote(item: RemoteStorageItem): Boolean = connection.connect(item)

    override suspend fun navigateToRemoteRoot(item: RemoteStorageItem) {
        navigateTo(connection.rootPathOf(item))
    }
}
