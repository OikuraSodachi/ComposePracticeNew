package com.todokanai.composepracticenew.repository

import android.util.Log
import com.todokanai.composepracticenew.data.DataConverter
import com.todokanai.composepracticenew.data.datastore.DataStoreRepository
import com.todokanai.composepracticenew.datasource.LocalFileDataSource
import com.todokanai.composepracticenew.model.RemoteStorageItem
import com.todokanai.fileexplorer.FileEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted

/**
 * 로컬 및 FTP 원격 경로를 모두 탐색할 수 있는 FileNavigatorRepository 구현체.
 * 경로 종류별 처리는 PathAwareFileSystem 구현체에 위임하며, 새 스토리지 타입은 fileSystems에 추가하면 된다.
 * @param converter 파일 목록을 UI 모델로 변환하는 DataConverter
 * @param dsRepo 정렬 기준 등 설정을 읽는 DataStoreRepository
 * @param appScope 파일 탐색 Flow를 공유할 애플리케이션 수명 CoroutineScope
 * @param localFiles 로컬 디렉터리 목록 읽기를 담당하는 LocalFileDataSource
 * @param ftpFileSystem FTP 파일 시스템 접근을 담당하는 FtpRepository
 * @param initialPath 초기 탐색 경로; null이면 설정하지 않는다
 */
class FileExplorerRepositoryImpl(
    converter: DataConverter,
    dsRepo: DataStoreRepository,
    appScope: CoroutineScope,
    localFiles: LocalFileDataSource,
    private val ftpFileSystem: FtpRepository,
    initialPath: String? = null
) : BaseFileExplorerRepository(converter, dsRepo, appScope, SharingStarted.Lazily, initialPath), RemoteFileNavigatorRepository {

    companion object {
        private const val TAG = "FileExplorerRepo"
    }

    // FTP보다 먼저 체크되면 안 되므로 FTP를 앞에 배치 — 로컬은 마지막 폴백
    private val fileSystems: List<PathAwareFileSystem> = listOf(
        FtpPathAwareFileSystem(ftpFileSystem),
        LocalPathAwareFileSystem(localFiles)
    )

    private fun resolveFileSystem(path: String): PathAwareFileSystem =
        fileSystems.firstOrNull { it.handles(path) } ?: error("No file system handles path: $path")

    override fun getParent(path: String): String? = resolveFileSystem(path).getParent(path)

    override suspend fun listFiles(path: String): List<FileEntry> {
        Log.d(TAG, "fileList 갱신 — path=$path")
        return resolveFileSystem(path).listFiles(path).also {
            Log.d(TAG, "fileList 결과 — path=$path size=${it.size}")
        }
    }

    override fun getParentPath(path: String): String? = getParent(path)

    override suspend fun navigate(path: String) {
        resolveFileSystem(path).resolveNavigationPath(path)?.let { navigateTo(it) }
    }

    override suspend fun setLocalPath(path: String) = navigate(path)

    override suspend fun connectRemote(item: RemoteStorageItem): Boolean =
        ftpFileSystem.connect(item.address, item.port, item.userId, item.password, item.encoding)

    override suspend fun setRemotePath(address: String) {
        navigateTo("ftp://${address.removePrefix("ftp://")}")
    }
}
